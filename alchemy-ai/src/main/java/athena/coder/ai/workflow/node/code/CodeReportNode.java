package athena.coder.ai.workflow.node.code;

import athena.coder.ai.assistant.agent.code.CodeReportAgent;
import athena.coder.ai.assistant.agent.result.summarizer.SummarizerResult;
import athena.coder.ai.workflow.entity.WorkflowState;
import athena.coder.ai.workflow.node.AbstractAgentNode;
import athena.coder.ai.spi.ErrorLogger;

import java.time.LocalDate;
import java.util.Map;

import static athena.coder.ai.assistant.model.factory.AiAssistantFactory.newChatAssistant;
import static athena.coder.ai.workflow.entity.WorkflowState.*;
import static org.bsc.langgraph4j.GraphDefinition.END;

/**
 * 编码工作流 - 收尾报告节点
 * <p>
 * 职责：整合编码/测试/调试/审查全流程证据，产出功能交付报告与 Commit Message，
 * 路由到 END 结束工作流
 */
public class CodeReportNode extends AbstractAgentNode {

    @Override
    protected String stepLabel() {
        return "[报告]";
    }

    @Override
    protected Map<String, Object> doApply(WorkflowState state, NodeContext ctx) throws Exception {
        String originalRequirement = state.getStringValue(ORIGINAL_REQUIREMENT);
        String changedFiles = requireUpstream(state.getStringValue(CHANGED_FILES),
                "changedFiles 为空，无代码变更可总结（需要编码环节先完成）");
        String changedDiffRef = state.getStringValue(CHANGED_DIFF_REF);
        String testResult = state.getStringValue(TEST_RESULT);
        String reviewResult = state.getStringValue(REVIEW_RESULT);

        warnIfBlank(originalRequirement, "originalRequirement 为空，将无法核对需求完成度");
        warnIfBlank(testResult, "testResult 为空，将缺少测试质量证据");

        String changeSummary = buildChangeSummary(changedFiles, changedDiffRef);

        logStart(ctx, "开始生成总结",
                "originalRequirement长度", originalRequirement != null ? originalRequirement.length() : 0,
                "changeSummary长度", changeSummary.length(),
                "testResult长度", testResult != null ? testResult.length() : 0,
                "reviewResult长度", reviewResult != null ? reviewResult.length() : 0);
        notifyModelCalling(state);

        CodeReportAgent assistant = newChatAssistant(ctx.modelType(), CodeReportAgent.class);
        AgentCall<SummarizerResult> call = request -> assistant.report(
                ctx.taskId(),
                request,
                ctx.projectPath(),
                LocalDate.now().format(DATE_FMT),
                originalRequirement,
                changeSummary,
                testResult,
                reviewResult
        );

        SummarizerResult summarizeResult = callAgentWithRetry(
                "请整合本次编码工作流的全部执行结果，生成交付报告和 Commit Message",
                "请重新生成总结。注意：上次调用失败，请严格按JSON格式输出完整报告。",
                call, null);

        String summarizeResultJson = MAPPER.writeValueAsString(summarizeResult);

        validateSummarizeResult(summarizeResult);
        logKeyInfoFromResult(summarizeResult);

        logInfo(String.format("CodeReportNode 总结完成: result长度=%d", summarizeResultJson.length()));

        // 输出总结报告给用户
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
     * 验证总结结果的完整性（检查关键字段是否存在）
     */
    private void validateSummarizeResult(SummarizerResult result) {
        if (result.error() != null) {
            ErrorLogger.warn("CodeReportNode", "总结结果包含错误标记: " + result.error());
            return;
        }
        if (result.report() == null || result.report().path("title").isMissingNode()) {
            ErrorLogger.warn("CodeReportNode", "总结结果缺少关键字段: /report/title");
        }
        if (result.commitMessage() == null || result.commitMessage().path("fullMessage").isMissingNode()) {
            ErrorLogger.warn("CodeReportNode", "总结结果缺少关键字段: /commitMessage/fullMessage");
        }
    }

    /**
     * 提取关键信息记录日志（便于快速浏览核心内容）
     */
    private void logKeyInfoFromResult(SummarizerResult result) {
        logIfPresent("会话ID", result.sessionId());
        if (result.report() != null) {
            logIfPresent("报告标题", result.report().path("title").asText(null));
            String overview = result.report().path("overview").asText(null);
            if (overview != null) {
                logInfo("执行摘要: " + truncate(overview, 150));
            }
            logIfPresent("整体风险等级", result.report().at("/riskAssessment/overallRisk").asText(null));
        }
        if (result.commitMessage() != null) {
            String fullMsg = result.commitMessage().path("fullMessage").asText(null);
            if (fullMsg != null) {
                logInfo("Commit Message:\n" + fullMsg);
            }
            String type = result.commitMessage().path("type").asText(null);
            String subject = result.commitMessage().path("subject").asText(null);
            if (type != null && subject != null) {
                logInfo("Commit 类型: %s / 标题: %s".formatted(type, subject));
            }
        }
        if (result.branchSuggestion() != null) {
            logIfPresent("建议分支名", result.branchSuggestion().path("name").asText(null));
        }
    }

    private void logIfPresent(String label, String value) {
        if (value != null && !value.isBlank()) {
            logInfo(label + ": " + value);
        }
    }
}
