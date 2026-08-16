package athena.coder.ai.workflow.node;

import athena.coder.ai.assistant.agent.ReportAgent;
import athena.coder.ai.assistant.agent.result.summarizer.SummarizerResult;
import athena.coder.ai.tool.config.AgentToolPolicy;
import athena.coder.ai.workflow.entity.WorkflowState;
import athena.coder.ai.spi.ErrorLogger;

import java.time.LocalDate;
import java.util.Map;

import static athena.coder.ai.assistant.model.factory.AiAssistantFactory.newChatAssistant;
import static athena.coder.ai.workflow.entity.WorkflowState.*;
import static org.bsc.langgraph4j.GraphDefinition.END;

/**
 * 通用收尾报告节点（合并原 CodeReportNode / FixReportNode / TestReportNode / DocReportNode）
 * <p>
 * 场景差异经 {@link ReportConfig} 注入：报告视角 scenario、commit type/branchPrefix、指令 request。
 * 无对应证据的章节经 {@link #orEmpty} 传空串。路由到 END 结束工作流。
 */
public class ReportNode extends AbstractAgentNode {

    private final ReportConfig config;

    public ReportNode(ReportConfig config) {
        this.config = config;
    }

    /** 场景化工厂：编码 */
    public static ReportNode code() {
        return new ReportNode(ReportConfig.code());
    }

    /** 场景化工厂：测试补全 */
    public static ReportNode test() {
        return new ReportNode(ReportConfig.test());
    }

    /** 场景化工厂：缺陷修复 */
    public static ReportNode fix() {
        return new ReportNode(ReportConfig.fix());
    }

    /** 场景化工厂：文档 */
    public static ReportNode doc() {
        return new ReportNode(ReportConfig.doc());
    }

    @Override
    protected String stepLabel() {
        return "[报告]";
    }

    @Override
    protected Map<String, Object> doApply(WorkflowState state, NodeContext ctx) throws Exception {
        String originalRequirement = state.getStringValue(ORIGINAL_REQUIREMENT);
        String changedFiles = requireUpstream(state.getStringValue(CHANGED_FILES),
                "changedFiles 为空，无变更可总结（需要编写环节先完成）");
        String changedDiffRef = state.getStringValue(CHANGED_DIFF_REF);
        String fixStrategy = state.getStringValue(FIX_STRATEGY);
        String testResult = state.getStringValue(TEST_RESULT);
        String reviewResult = state.getStringValue(REVIEW_RESULT);

        warnIfBlank(originalRequirement, "originalRequirement 为空，将无法核对完成度");

        String changeSummary = buildChangeSummary(changedFiles, changedDiffRef);

        logStart(ctx, "开始生成" + config.actionVerb(),
                "originalRequirement长度", originalRequirement != null ? originalRequirement.length() : 0,
                "changeSummary长度", changeSummary.length(),
                "fixStrategy长度", fixStrategy != null ? fixStrategy.length() : 0,
                "testResult长度", testResult != null ? testResult.length() : 0,
                "reviewResult长度", reviewResult != null ? reviewResult.length() : 0);
        notifyModelCalling(state);

        ReportAgent assistant = newChatAssistant(ctx.modelType(), ReportAgent.class, config.policy());
        AgentCall<SummarizerResult> call = request -> assistant.report(
                ctx.taskId(), request, ctx.projectPath(), LocalDate.now().format(DATE_FMT),
                sessionId(), config.commitType(), config.branchPrefix(), config.scenario(),
                orEmpty(originalRequirement), changeSummary, orEmpty(fixStrategy), orEmpty(testResult), orEmpty(reviewResult));

        SummarizerResult summarizeResult = callAgentWithRetry(config.request(), config.retryRequest(), call, null);

        String summarizeResultJson = MAPPER.writeValueAsString(summarizeResult);

        validateSummarizeResult(summarizeResult);

        logInfo(getClass().getSimpleName() + " 完成: result长度=" + summarizeResultJson.length());

        // 输出报告给用户
        String reportTitle = textAt(summarizeResult.report(), "/title");
        String reportOverview = textAt(summarizeResult.report(), "/overview");
        String fullMessage = summarizeResult.commitMessage() != null
                ? textAt(summarizeResult.commitMessage(), "/fullMessage") : null;
        StringBuilder reportMsg = new StringBuilder();
        if (reportTitle != null && !reportTitle.isBlank())
            reportMsg.append("## ").append(reportTitle).append("\n\n");
        if (reportOverview != null && !reportOverview.isBlank())
            reportMsg.append(reportOverview).append("\n\n");
        if (fullMessage != null && !fullMessage.isBlank())
            reportMsg.append("**Commit:** ").append(fullMessage).append("\n");
        if (!reportMsg.isEmpty()) {
            notifyResult(state, "[完成]", reportMsg.toString());
        }

        return Map.of(SUMMARIZE_RESULT, summarizeResultJson, NEXT_NODE, END);
    }

    private static String orEmpty(String value) {
        return value != null ? value : "";
    }

    /**
     * 验证报告结果的完整性（检查关键字段是否存在）
     */
    private void validateSummarizeResult(SummarizerResult result) {
        if (result.report() == null || result.report().path("title").isMissingNode()) {
            ErrorLogger.warn(getClass().getSimpleName(), "报告结果缺少关键字段: /report/title");
        }
        if (result.commitMessage() == null || result.commitMessage().path("fullMessage").isMissingNode()) {
            ErrorLogger.warn(getClass().getSimpleName(), "报告结果缺少关键字段: /commitMessage/fullMessage");
        }
    }

    /**
     * 报告角色配置：报告视角/commit type/branchPrefix/指令/工具权限
     */
    public record ReportConfig(
            String scenario,
            String commitType,
            String branchPrefix,
            String request,
            String retryRequest,
            String actionVerb,
            AgentToolPolicy policy) {

        public static ReportConfig code() {
            return new ReportConfig("编码工作流：功能实现交付报告", "feat", "feature",
                    "请整合本次编码工作流的全部执行结果，生成交付报告和 Commit Message",
                    "请重新生成总结。注意：上次调用失败，请严格按JSON格式输出完整报告。",
                    "总结", AgentToolPolicy.REPORTER);
        }

        public static ReportConfig test() {
            return new ReportConfig("测试补全工作流：补测交付报告", "test", "test",
                    "请整合本次测试补全工作流的全部执行结果，生成补测报告和 Commit Message",
                    "请重新生成补测报告。注意：上次调用失败，请严格按JSON格式输出完整报告。",
                    "补测报告", AgentToolPolicy.REPORTER);
        }

        public static ReportConfig fix() {
            return new ReportConfig("缺陷修复工作流：修复交付报告", "fix", "fix",
                    "请整合本次缺陷修复的全部执行结果，生成修复报告和 Commit Message",
                    "请重新生成修复报告。注意：上次调用失败，请严格按JSON格式输出完整报告。",
                    "修复报告", AgentToolPolicy.REPORTER);
        }

        public static ReportConfig doc() {
            return new ReportConfig("文档工作流：文档变更交付报告", "docs", "docs",
                    "请整合本次文档工作流的全部执行结果，生成文档变更报告和 Commit Message",
                    "请重新生成文档报告。注意：上次调用失败，请严格按JSON格式输出完整报告。",
                    "文档报告", AgentToolPolicy.REPORTER);
        }
    }
}
