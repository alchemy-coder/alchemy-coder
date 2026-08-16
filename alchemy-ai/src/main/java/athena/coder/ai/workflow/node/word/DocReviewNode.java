package athena.coder.ai.workflow.node.word;

import athena.coder.ai.assistant.agent.result.reviewer.ReviewerResult;
import athena.coder.ai.assistant.agent.word.DocReviewAgent;
import athena.coder.ai.workflow.entity.WorkflowState;
import athena.coder.ai.workflow.node.AbstractAgentNode;
import athena.coder.ai.spi.ErrorLogger;
import athena.coder.exception.RocAgentException;

import java.time.LocalDate;
import java.util.Map;

import static athena.coder.ai.assistant.model.factory.AiAssistantFactory.newChatAssistant;
import static athena.coder.ai.workflow.entity.NodeEnum.CODER;
import static athena.coder.ai.workflow.entity.NodeEnum.SUMMARIZER;
import static athena.coder.ai.workflow.entity.WorkflowState.*;

/**
 * 文档工作流 - 文档审查节点
 * <p>
 * 职责：审查文档变更的准确性/完备性/一致性（无测试证据输入，以内容核对为主）。路由：
 * APPROVED/APPROVED_WITH_NOTES → SUMMARIZER；
 * REQUEST_CHANGES → CODER（超限熔断 → SUMMARIZER）；BLOCKED → SUMMARIZER（提示人工介入）
 */
public class DocReviewNode extends AbstractAgentNode {

    /**
     * REVIEWER→CODER 审查打回回环熔断上限，超过后强制走 SUMMARIZER 收尾
     */
    private static final int MAX_REVIEW_LOOPS = 2;

    @Override
    protected String stepLabel() {
        return "[审查]";
    }

    @Override
    protected Map<String, Object> doApply(WorkflowState state, NodeContext ctx) throws Exception {
        String originalRequirement = requireUpstream(state.getStringValue(ORIGINAL_REQUIREMENT),
                "originalRequirement 为空，无法执行需求对齐检查");
        String changedFiles = requireUpstream(state.getStringValue(CHANGED_FILES),
                "changedFiles 为空，无文档变更可审查（需要编写环节先完成）");
        String changedDiffRef = state.getStringValue(CHANGED_DIFF_REF);
        String acceptanceCriteria = state.getStringValue(ACCEPTANCE_CRITERIA);

        warnIfBlank(changedDiffRef, "changedDiffRef 为空，将无法查看具体的文档变更 diff");
        warnIfBlank(acceptanceCriteria, "acceptanceCriteria 为空，将跳过验收标准核对");

        String changeSummary = buildChangeSummary(changedFiles, changedDiffRef);

        logStart(ctx, "开始审查",
                "originalRequirement长度", originalRequirement.length(),
                "changeSummary长度", changeSummary.length(),
                "acceptanceCriteria长度", acceptanceCriteria != null ? acceptanceCriteria.length() : 0);
        notifyModelCalling(state);

        DocReviewAgent assistant = newChatAssistant(ctx.modelType(), DocReviewAgent.class);
        AgentCall<ReviewerResult> call = request -> assistant.review(
                request,
                ctx.projectPath(),
                ctx.projectType(),
                LocalDate.now().format(DATE_FMT),
                originalRequirement,
                changeSummary,
                acceptanceCriteria
        );

        ReviewerResult reviewResult = callAgentWithRetry(
                "请对本次文档变更进行准确性与完备性审查",
                "请重新执行审查。注意：上次调用失败，请严格按JSON格式输出审查报告。",
                call, null);

        // 缺失 verdict 时默认 BLOCKED，确保安全
        String verdict = (reviewResult.verdict() != null ? reviewResult.verdict() : "BLOCKED").toUpperCase();
        String reviewResultJson = MAPPER.writeValueAsString(reviewResult);

        logInfo(String.format("DocReviewNode 审查完成: verdict=%s, result长度=%d", verdict, reviewResultJson.length()));

        String reviewIcon = switch (verdict) {
            case "APPROVED", "APPROVED_WITH_NOTES" -> "[通过]";
            case "REQUEST_CHANGES" -> "[重审]";
            case "BLOCKED" -> "[阻塞]";
            default -> "[未知]";
        };
        String reviewMsg = switch (verdict) {
            case "APPROVED" -> "文档审查通过";
            case "APPROVED_WITH_NOTES" -> "文档审查通过（有建议）";
            case "REQUEST_CHANGES" -> "文档审查打回，需要修改";
            case "BLOCKED" -> "文档审查严重阻塞，需人工介入";
            default -> "审查结论: " + verdict;
        };
        String summaryText = reviewResult.summary();
        if (summaryText != null && !summaryText.isBlank()) {
            reviewMsg += "：" + truncate(summaryText, 100);
        }
        notifyResult(state, reviewIcon, reviewMsg);

        int loopCount = state.getIntValue(REVIEW_LOOP_COUNT);
        if ("REQUEST_CHANGES".equals(verdict)) {
            loopCount++;
        }

        return Map.of(
                REVIEW_RESULT, reviewResultJson,
                REVIEW_LOOP_COUNT, loopCount,
                NEXT_NODE, determineNextNode(verdict, loopCount)
        );
    }

    /**
     * 根据审查结论与回环计数决定下一个节点（熔断保护）
     */
    private String determineNextNode(String verdict, int loopCount) {
        return switch (verdict) {
            case "APPROVED", "APPROVED_WITH_NOTES" -> {
                logInfo("审查通过(" + verdict + ")，路由到 SUMMARIZER 进行收尾");
                yield SUMMARIZER.name();
            }
            case "REQUEST_CHANGES" -> {
                if (loopCount >= MAX_REVIEW_LOOPS) {
                    ErrorLogger.warn("DocReviewNode", "审查打回已达上限(" + MAX_REVIEW_LOOPS + "次)，熔断并路由到 SUMMARIZER 收尾");
                    yield SUMMARIZER.name();
                }
                ErrorLogger.warn("DocReviewNode", "审查未通过(REQUEST_CHANGES，第" + loopCount + "轮)，路由到 CODER 进行修改");
                yield CODER.name();
            }
            case "BLOCKED" -> {
                ErrorLogger.warn("DocReviewNode", "审查严重阻塞(BLOCKED)，路由到 SUMMARIZER 输出收尾报告并提示人工介入");
                yield SUMMARIZER.name();
            }
            default -> {
                throw new RocAgentException("未知的审查结论: " + verdict);
            }
        };
    }
}
