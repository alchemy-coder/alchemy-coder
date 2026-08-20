package athena.coder.ai.workflow.node;

import athena.coder.ai.assistant.agent.FixAnalystAgent;
import athena.coder.ai.assistant.agent.result.debugger.DebuggerResult;
import athena.coder.ai.tool.config.AgentToolPolicy;
import athena.coder.ai.workflow.entity.NodeEnum;
import athena.coder.ai.workflow.entity.StepRole;
import athena.coder.ai.workflow.entity.WorkflowState;
import athena.coder.ai.spi.ErrorLogger;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

import java.time.LocalDate;
import java.util.Map;

import static athena.coder.ai.assistant.model.factory.AiAssistantFactory.newChatAssistant;
import static athena.coder.ai.workflow.entity.WorkflowState.*;

/**
 * 通用调试分析节点（合并原 CodeFixAnalystNode / FixAnalyzeNode / TestFixAnalystNode）
 * <p>
 * 测试失败时做根因分析并制定修复策略（只分析不改代码），是打破 写↔测 死循环的关键角色。
 * 场景差异经 {@link AnalystConfig} 注入：使命 scenario、指令 request、升级/修复文案。
 * 路由：正常 → CODER 执行修复；升级/回环超限熔断 → SUMMARIZER 收尾报告。
 */
public class AnalystNode extends AbstractAgentNode {

    /**
     * DEBUGGER→CODER 修复回环熔断上限，超过后强制走 SUMMARIZER 收尾
     */
    private static final int MAX_DEBUG_LOOPS = 3;

    private final AnalystConfig config;

    public AnalystNode(AnalystConfig config) {
        this.config = config;
    }

    /** 场景化工厂：编码 */
    public static AnalystNode code() {
        return new AnalystNode(AnalystConfig.code());
    }

    /** 场景化工厂：测试补全 */
    public static AnalystNode test() {
        return new AnalystNode(AnalystConfig.test());
    }

    /** 场景化工厂：缺陷修复 */
    public static AnalystNode fix() {
        return new AnalystNode(AnalystConfig.fix());
    }

    @Override
    protected StepRole stepRole() {
        return config.stepRole();
    }

    @Override
    protected Map<String, Object> doApply(WorkflowState state, NodeContext ctx) throws Exception {
        String testResult = requireUpstream(state.getStringValue(TEST_RESULT),
                "testResult 为空，无法分析失败原因（需要测试环节先完成）");
        String changedFiles = requireUpstream(state.getStringValue(CHANGED_FILES),
                "changedFiles 为空，无法确定排查范围（需要" + config.upstreamNoun() + "环节先完成）");
        String changedDiffRef = requireUpstream(state.getStringValue(CHANGED_DIFF_REF),
                "changedDiffRef 为空，无法查看变更详情");
        String acceptanceCriteria = requireUpstream(state.getStringValue(ACCEPTANCE_CRITERIA),
                "acceptanceCriteria 为空，缺少验收标准");
        String previousFixes = state.getStringValue(PREVIOUS_FIXES);

        notifyModelCalling(state);

        FixAnalystAgent assistant = newChatAssistant(ctx.modelType(), FixAnalystAgent.class, config.policy());
        AgentCall<DebuggerResult> call = request -> assistant.analyze(
                ctx.taskId(), request, ctx.projectPath(), ctx.projectType(),
                LocalDate.now().format(DATE_FMT), sessionId(), config.scenario(),
                testResult, changedFiles, changedDiffRef, acceptanceCriteria,
                previousFixes != null ? previousFixes : "");

        DebuggerResult debugResult = callAgentWithRetry(config.request(), config.retryRequest(), call, null);

        boolean shouldEscalate = debugResult.shouldEscalate();
        String fixStrategyJson = MAPPER.writeValueAsString(debugResult);

        int loopCount = state.getIntValue(DEBUG_LOOP_COUNT) + 1;
        String nextNode = determineNextNode(shouldEscalate, loopCount);
        String updatedPreviousFixes = appendPreviousFixes(previousFixes, MAPPER.readTree(fixStrategyJson));

        if (shouldEscalate) {
            notifyResult(state, "[警告]", config.escalateMsg());
        } else {
            notifyResult(state, "[修复]", config.fixMsgPrefix() + "（第" + loopCount + "轮）");
        }

        return Map.of(
                FIX_STRATEGY, fixStrategyJson,
                PREVIOUS_FIXES, updatedPreviousFixes,
                DEBUG_LOOP_COUNT, loopCount,
                NEXT_NODE, nextNode
        );
    }

    /**
     * 根据升级标志与回环计数决定下一个节点（熔断保护）
     */
    private String determineNextNode(boolean shouldEscalate, int loopCount) {
        if (shouldEscalate) {
            ErrorLogger.warn(getClass().getSimpleName(), "检测到死循环风险或复杂问题，路由到 SUMMARIZER 收尾并提示人工介入");
            return NodeEnum.SUMMARIZER.name();
        }
        if (loopCount >= MAX_DEBUG_LOOPS) {
            ErrorLogger.warn(getClass().getSimpleName(), "修复回环已达上限(" + MAX_DEBUG_LOOPS + "次)，熔断并路由到 SUMMARIZER 收尾");
            return NodeEnum.SUMMARIZER.name();
        }
        return NodeEnum.CODER.name();
    }

    /**
     * 将本次修复策略追加到历史修复记录（JSON数组），供下一轮分析防重复
     */
    private String appendPreviousFixes(String previousFixes, JsonNode fixStrategy) {
        try {
            ArrayNode history;
            if (previousFixes != null && !previousFixes.isBlank()) {
                JsonNode parsed = MAPPER.readTree(previousFixes);
                history = parsed.isArray() ? (ArrayNode) parsed : MAPPER.createArrayNode().add(parsed);
            } else {
                history = MAPPER.createArrayNode();
            }
            history.add(fixStrategy);
            return MAPPER.writeValueAsString(history);
        } catch (Exception e) {
            ErrorLogger.warn(getClass().getSimpleName() + ".appendPreviousFixes", "追加历史修复记录失败，仅保留本次策略: " + e.getMessage());
            return "[" + fixStrategy + "]";
        }
    }

    /**
     * 分析角色配置：使命/指令/升级与修复文案/工具权限
     */
    public record AnalystConfig(
            String scenario,
            String request,
            String retryRequest,
            String actionVerb,
            String upstreamNoun,
            StepRole stepRole,
            String escalateMsg,
            String fixMsgPrefix,
            AgentToolPolicy policy) {

        public static AnalystConfig code() {
            return new AnalystConfig(
                    "编码工作流：新功能测试失败，定位业务代码根因（区分实现缺陷与测试覆盖不足）并制定修复策略",
                    "请对测试失败进行根因分析并制定修复策略",
                    "请重新进行调试分析。注意：上次调用失败，请严格按JSON格式输出修复策略。",
                    "调试分析", "编码", StepRole.ANALYST,
                    "问题复杂，需要人工介入", "已定位根因，准备修复",
                    AgentToolPolicy.ANALYST);
        }

        public static AnalystConfig test() {
            return new AnalystConfig(
                    "测试补全工作流：新补写测试失败，区分'测试写错'与'被测代码有bug'，定位根因并制定修复策略",
                    "请对新测试失败进行根因分析并制定修复策略",
                    "请重新进行失败分析。注意：上次调用失败，请严格按JSON格式输出修复策略。",
                    "失败分析", "补测", StepRole.ANALYST,
                    "问题复杂或疑似业务代码缺陷，需要人工介入", "已定位根因，准备修复测试",
                    AgentToolPolicy.ANALYST);
        }

        public static AnalystConfig fix() {
            return new AnalystConfig(
                    "缺陷修复工作流：回归验证失败，分析当前修复为何未生效并制定新的修复策略（重点防重复修复）",
                    "请对回归验证失败进行根因分析并制定新的修复策略",
                    "请重新进行根因分析。注意：上次调用失败，请严格按JSON格式输出修复策略。",
                    "根因分析", "修复", StepRole.ANALYST,
                    "问题复杂，需要人工介入", "已定位根因，准备修复",
                    AgentToolPolicy.ANALYST);
        }
    }
}
