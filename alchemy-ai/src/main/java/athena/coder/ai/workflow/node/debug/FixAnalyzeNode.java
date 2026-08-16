package athena.coder.ai.workflow.node.debug;

import athena.coder.ai.assistant.agent.FixAnalystAgent;
import athena.coder.ai.assistant.agent.result.debugger.DebuggerResult;
import athena.coder.ai.workflow.entity.WorkflowState;
import athena.coder.ai.workflow.node.AbstractAgentNode;
import athena.coder.ai.spi.ErrorLogger;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

import java.time.LocalDate;
import java.util.Map;

import static athena.coder.ai.assistant.model.factory.AiAssistantFactory.newChatAssistant;
import static athena.coder.ai.workflow.entity.NodeEnum.CODER;
import static athena.coder.ai.workflow.entity.NodeEnum.SUMMARIZER;
import static athena.coder.ai.workflow.entity.WorkflowState.*;

/**
 * 缺陷修复工作流 - 根因分析节点
 * <p>
 * 职责：回归验证未通过时做根因分析与升级判断（重点防重复修复，只分析不改代码）。路由：
 * 正常 → CODER 执行修复；升级/回环超限熔断 → SUMMARIZER 收尾报告
 */
public class FixAnalyzeNode extends AbstractAgentNode {

    /**
     * DEBUGGER→CODER 修复回环熔断上限，超过后强制走 SUMMARIZER 收尾
     */
    private static final int MAX_DEBUG_LOOPS = 3;

    @Override
    protected String stepLabel() {
        return "[分析]";
    }

    @Override
    protected Map<String, Object> doApply(WorkflowState state, NodeContext ctx) throws Exception {
        String testResult = requireUpstream(state.getStringValue(TEST_RESULT),
                "testResult 为空，无法分析失败原因（需要回归验证环节先完成）");
        String changedFiles = requireUpstream(state.getStringValue(CHANGED_FILES),
                "changedFiles 为空，无法确定排查范围（需要修复环节先完成）");
        String changedDiffRef = requireUpstream(state.getStringValue(CHANGED_DIFF_REF),
                "changedDiffRef 为空，无法查看修复变更详情");
        String acceptanceCriteria = requireUpstream(state.getStringValue(ACCEPTANCE_CRITERIA),
                "acceptanceCriteria 为空，缺少缺陷验收标准");
        String previousFixes = state.getStringValue(PREVIOUS_FIXES);

        logStart(ctx, "开始根因分析",
                "testResult长度", testResult.length(),
                "changedFiles长度", changedFiles.length(),
                "changedDiffRef", truncate(changedDiffRef, 30),
                "acceptanceCriteria长度", acceptanceCriteria.length(),
                "hasPreviousFixes", previousFixes != null && !previousFixes.isBlank());
        notifyModelCalling(state);

        FixAnalystAgent assistant = newChatAssistant(ctx.modelType(), FixAnalystAgent.class);
        AgentCall<DebuggerResult> call = request -> assistant.analyze(
                ctx.taskId(),
                request,
                ctx.projectPath(),
                ctx.projectType(),
                LocalDate.now().format(DATE_FMT),
                sessionId(),
                testResult,
                changedFiles,
                changedDiffRef,
                acceptanceCriteria,
                previousFixes != null ? previousFixes : ""
        );

        DebuggerResult debugResult = callAgentWithRetry(
                "请对回归验证失败进行根因分析并制定新的修复策略",
                "请重新进行根因分析。注意：上次调用失败，请严格按JSON格式输出修复策略。",
                call, null);

        boolean shouldEscalate = debugResult.shouldEscalate();
        String fixStrategyJson = MAPPER.writeValueAsString(debugResult);

        int loopCount = state.getIntValue(DEBUG_LOOP_COUNT) + 1;
        String nextNode = determineNextNode(shouldEscalate, loopCount);
        String updatedPreviousFixes = appendPreviousFixes(previousFixes, MAPPER.readTree(fixStrategyJson));

        logInfo(String.format("FixAnalyzeNode 分析完成: shouldEscalate=%b, action=%s, result长度=%d",
                shouldEscalate, shouldEscalate ? "升级到人工介入" : "等待修复执行器执行", fixStrategyJson.length()));

        if (shouldEscalate) {
            notifyResult(state, "[警告]", "问题复杂，需要人工介入");
        } else {
            notifyResult(state, "[修复]", "已定位根因，准备修复（第" + loopCount + "轮）");
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
            ErrorLogger.warn("FixAnalyzeNode", "检测到死循环风险或复杂问题，路由到 SUMMARIZER 收尾并提示人工介入");
            return SUMMARIZER.name();
        }
        if (loopCount >= MAX_DEBUG_LOOPS) {
            ErrorLogger.warn("FixAnalyzeNode", "修复回环已达上限(" + MAX_DEBUG_LOOPS + "次)，熔断并路由到 SUMMARIZER 收尾");
            return SUMMARIZER.name();
        }
        logInfo("[OK] 分析完成(第" + loopCount + "轮)，路由到 CODER 执行修复策略");
        return CODER.name();
    }

    /**
     * 将本次修复策略追加到历史修复记录（JSON数组），供下一轮根因分析防重复
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
            ErrorLogger.warn("FixAnalyzeNode.appendPreviousFixes", "追加历史修复记录失败，仅保留本次策略: " + e.getMessage());
            return "[" + fixStrategy.toString() + "]";
        }
    }
}