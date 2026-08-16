package athena.coder.ai.workflow.node.word;

import athena.coder.ai.assistant.agent.result.summarizer.SummarizerResult;
import athena.coder.ai.assistant.agent.ReportAgent;
import athena.coder.ai.workflow.entity.WorkflowState;
import athena.coder.ai.workflow.node.AbstractAgentNode;
import athena.coder.ai.spi.ErrorLogger;

import java.time.LocalDate;
import java.util.Map;

import static athena.coder.ai.assistant.model.factory.AiAssistantFactory.newChatAssistant;
import static athena.coder.ai.workflow.entity.WorkflowState.*;
import static org.bsc.langgraph4j.GraphDefinition.END;

/**
 * 文档工作流 - 文档报告节点
 * <p>
 * 职责：整合文档编写/审查证据，产出文档变更报告与 Commit Message，
 * 路由到 END 结束工作流
 */
public class DocReportNode extends AbstractAgentNode {

    @Override
    protected String stepLabel() {
        return "[报告]";
    }

    @Override
    protected Map<String, Object> doApply(WorkflowState state, NodeContext ctx) throws Exception {
        String originalRequirement = state.getStringValue(ORIGINAL_REQUIREMENT);
        String changedFiles = requireUpstream(state.getStringValue(CHANGED_FILES),
                "changedFiles 为空，无文档变更可总结（需要编写环节先完成）");
        String changedDiffRef = state.getStringValue(CHANGED_DIFF_REF);
        String reviewResult = state.getStringValue(REVIEW_RESULT);

        warnIfBlank(originalRequirement, "originalRequirement 为空，将无法核对文档完成度");
        warnIfBlank(reviewResult, "reviewResult 为空，报告将缺少审查证据");

        String changeSummary = buildChangeSummary(changedFiles, changedDiffRef);

        logStart(ctx, "开始生成文档报告",
                "originalRequirement长度", originalRequirement != null ? originalRequirement.length() : 0,
                "changeSummary长度", changeSummary.length(),
                "reviewResult长度", reviewResult != null ? reviewResult.length() : 0);
        notifyModelCalling(state);

        ReportAgent assistant = newChatAssistant(ctx.modelType(), ReportAgent.class);
        AgentCall<SummarizerResult> call = request -> assistant.report(
                ctx.taskId(),
                request,
                ctx.projectPath(),
                LocalDate.now().format(DATE_FMT),
                sessionId(),
                "docs",
                "docs",
                "文档工作流：文档变更交付报告",
                originalRequirement,
                changeSummary,
                "",
                "",
                reviewResult
        );

        SummarizerResult summarizeResult = callAgentWithRetry(
                "请整合本次文档工作流的全部执行结果，生成文档变更报告和 Commit Message",
                "请重新生成文档报告。注意：上次调用失败，请严格按JSON格式输出完整报告。",
                call, null);

        String summarizeResultJson = MAPPER.writeValueAsString(summarizeResult);

        validateSummarizeResult(summarizeResult);

        logInfo(String.format("DocReportNode 报告完成: result长度=%d", summarizeResultJson.length()));

        // 输出文档报告给用户
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

        return Map.of(
                SUMMARIZE_RESULT, summarizeResultJson,
                NEXT_NODE, END
        );
    }

    /**
     * 验证报告结果的完整性（检查关键字段是否存在）
     */
    private void validateSummarizeResult(SummarizerResult result) {
        if (result.error() != null) {
            ErrorLogger.warn("DocReportNode", "报告结果包含错误标记: " + result.error());
            return;
        }
        if (result.report() == null || result.report().path("title").isMissingNode()) {
            ErrorLogger.warn("DocReportNode", "报告结果缺少关键字段: /report/title");
        }
        if (result.commitMessage() == null || result.commitMessage().path("fullMessage").isMissingNode()) {
            ErrorLogger.warn("DocReportNode", "报告结果缺少关键字段: /commitMessage/fullMessage");
        }
    }
}