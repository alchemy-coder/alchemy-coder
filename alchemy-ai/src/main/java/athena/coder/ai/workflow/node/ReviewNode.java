package athena.coder.ai.workflow.node;

import athena.coder.ai.assistant.agent.ReviewAgent;
import athena.coder.ai.assistant.agent.result.reviewer.ReviewerResult;
import athena.coder.ai.tool.config.AgentToolPolicy;
import athena.coder.ai.workflow.entity.NodeEnum;
import athena.coder.ai.workflow.entity.ReviewVerdict;
import athena.coder.ai.workflow.entity.WorkflowState;
import athena.coder.ai.spi.ErrorLogger;

import java.time.LocalDate;
import java.util.Map;

import static athena.coder.ai.assistant.model.factory.AiAssistantFactory.newChatAssistant;
import static athena.coder.ai.workflow.entity.WorkflowState.*;

/**
 * 通用质量审查节点（合并原 CodeReviewNode / TestReviewNode / DocReviewNode）
 * <p>
 * 场景差异经 {@link ReviewConfig} 注入：使命 scenario、审查阶段 schema stageResults、文案 subject、
 * 是否依赖测试证据 hasTestEvidence。路由：APPROVED/APPROVED_WITH_NOTES → SUMMARIZER；
 * REQUEST_CHANGES → CODER（超限熔断 → SUMMARIZER）；BLOCKED → SUMMARIZER（提示人工介入）。
 */
public class ReviewNode extends AbstractAgentNode {

    /**
     * REVIEWER→CODER 审查打回回环熔断上限，超过后强制走 SUMMARIZER 收尾
     */
    private static final int MAX_REVIEW_LOOPS = 2;

    private final ReviewConfig config;

    public ReviewNode(ReviewConfig config) {
        this.config = config;
    }

    /** 场景化工厂：编码 */
    public static ReviewNode code() {
        return new ReviewNode(ReviewConfig.code());
    }

    /** 场景化工厂：测试补全 */
    public static ReviewNode test() {
        return new ReviewNode(ReviewConfig.test());
    }

    /** 场景化工厂：文档 */
    public static ReviewNode doc() {
        return new ReviewNode(ReviewConfig.doc());
    }

    @Override
    protected String stepLabel() {
        return "[审查]";
    }

    @Override
    protected Map<String, Object> doApply(WorkflowState state, NodeContext ctx) throws Exception {
        String originalRequirement = requireUpstream(state.getStringValue(ORIGINAL_REQUIREMENT),
                "originalRequirement 为空，无法执行需求对齐检查");
        String changedFiles = requireUpstream(state.getStringValue(CHANGED_FILES),
                "changedFiles 为空，无变更可审查（需要编写环节先完成）");
        String changedDiffRef = state.getStringValue(CHANGED_DIFF_REF);
        String testResult = config.hasTestEvidence()
                ? requireUpstream(state.getStringValue(TEST_RESULT), "testResult 为空，缺少测试证据（需要测试环节先完成）")
                : "";
        String acceptanceCriteria = state.getStringValue(ACCEPTANCE_CRITERIA);

        warnIfBlank(changedDiffRef, "changedDiffRef 为空，将无法查看具体的变更 diff");
        warnIfBlank(acceptanceCriteria, "acceptanceCriteria 为空，将跳过验收标准核对");

        String changeSummary = buildChangeSummary(changedFiles, changedDiffRef);

        notifyModelCalling(state);

        ReviewAgent assistant = newChatAssistant(ctx.modelType(), ReviewAgent.class, config.policy());
        AgentCall<ReviewerResult> call = request -> assistant.review(
                request, ctx.projectPath(), ctx.projectType(), LocalDate.now().format(DATE_FMT),
                sessionId(), config.scenario(), config.stageResults(),
                originalRequirement, changeSummary, testResult, acceptanceCriteria);

        ReviewerResult reviewResult = callAgentWithRetry(config.request(), config.retryRequest(), call, null);

        // 缺失 verdict 时 ReviewVerdict.from 默认 BLOCKED，确保安全
        ReviewVerdict verdict = ReviewVerdict.from(reviewResult.verdict());
        String reviewResultJson = MAPPER.writeValueAsString(reviewResult);

        String reviewIcon = switch (verdict) {
            case APPROVED, APPROVED_WITH_NOTES -> "[通过]";
            case REQUEST_CHANGES -> "[重审]";
            case BLOCKED -> "[阻塞]";
        };
        String reviewMsg = switch (verdict) {
            case APPROVED -> config.subject() + "通过";
            case APPROVED_WITH_NOTES -> config.subject() + "通过（有建议）";
            case REQUEST_CHANGES -> config.subject() + "打回，需要修改";
            case BLOCKED -> config.subject() + "严重阻塞，需人工介入";
        };
        String summaryText = reviewResult.summary();
        if (summaryText != null && !summaryText.isBlank()) {
            reviewMsg += "：" + truncate(summaryText, 100);
        }
        notifyResult(state, reviewIcon, reviewMsg);

        int loopCount = state.getIntValue(REVIEW_LOOP_COUNT);
        if (verdict == ReviewVerdict.REQUEST_CHANGES) {
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
    private String determineNextNode(ReviewVerdict verdict, int loopCount) {
        return switch (verdict) {
            case APPROVED, APPROVED_WITH_NOTES -> {
                yield NodeEnum.SUMMARIZER.name();
            }
            case REQUEST_CHANGES -> {
                if (loopCount >= MAX_REVIEW_LOOPS) {
                    ErrorLogger.warn(getClass().getSimpleName(), "审查打回已达上限(" + MAX_REVIEW_LOOPS + "次)，熔断并路由到 SUMMARIZER 收尾");
                    yield NodeEnum.SUMMARIZER.name();
                }
                ErrorLogger.warn(getClass().getSimpleName(), "审查未通过(REQUEST_CHANGES，第" + loopCount + "轮)，路由到 CODER 进行修改");
                yield NodeEnum.CODER.name();
            }
            case BLOCKED -> {
                ErrorLogger.warn(getClass().getSimpleName(), "审查严重阻塞(BLOCKED)，路由到 SUMMARIZER 输出收尾报告并提示人工介入");
                yield NodeEnum.SUMMARIZER.name();
            }
        };
    }

    /**
     * 审查角色配置：使命/审查阶段 schema/文案/是否依赖测试证据/工具权限
     */
    public record ReviewConfig(
            String scenario,
            String stageResults,
            String subject,
            String request,
            String retryRequest,
            boolean hasTestEvidence,
            AgentToolPolicy policy) {

        public static ReviewConfig code() {
            return new ReviewConfig(
                    "你是编码工作流的质量审查员：对功能实现的代码变更做质量门控审查（代码规范/安全/需求对齐/可测试性），判定是否满足交付标准。",
                    """
                    {
                      "代码规范": { "status": "PASS|WARN|FAIL", "notes": "命名/风格/结构合规性" },
                      "安全": { "status": "PASS|WARN|FAIL", "notes": "越界修改/注入/敏感信息" },
                      "需求对齐": { "status": "PASS|WARN|FAIL", "notes": "对照验收标准逐条核对" },
                      "可测试性": { "status": "PASS|WARN|FAIL", "notes": "是否便于测试与维护" }
                    }
                    """,
                    "审查",
                    "请对本次代码变更进行全面质量审查",
                    "请重新执行审查。注意：上次调用失败，请严格按JSON格式输出审查报告。",
                    true,
                    AgentToolPolicy.CODE_REVIEWER);
        }

        public static ReviewConfig test() {
            return new ReviewConfig(
                    "你是测试补全工作流的审查员：审查新补写测试的质量（断言有效性/边界覆盖/测试独立性），区分'测试写错'与'被测代码有bug'。",
                    """
                    {
                      "断言有效性": { "status": "PASS|WARN|FAIL", "notes": "断言是否真实校验行为" },
                      "边界覆盖": { "status": "PASS|WARN|FAIL", "notes": "正常/异常/边界场景覆盖" },
                      "测试独立性": { "status": "PASS|WARN|FAIL", "notes": "无外部依赖、可重复执行" }
                    }
                    """,
                    "测试质量审查",
                    "请对新补写的测试进行质量审查",
                    "请重新执行审查。注意：上次调用失败，请严格按JSON格式输出审查报告。",
                    true,
                    AgentToolPolicy.TEST_REVIEWER);
        }

        public static ReviewConfig doc() {
            return new ReviewConfig(
                    "你是文档工作流的审查员：审查文档变更的准确性/完备性/一致性（无测试证据，以内容核对为主）。",
                    """
                    {
                      "准确性": { "status": "PASS|WARN|FAIL", "notes": "与源码事实一致" },
                      "完备性": { "status": "PASS|WARN|FAIL", "notes": "覆盖目标文档的全部要点" },
                      "一致性": { "status": "PASS|WARN|FAIL", "notes": "术语/格式/风格统一" }
                    }
                    """,
                    "文档审查",
                    "请对本次文档变更进行准确性与完备性审查",
                    "请重新执行审查。注意：上次调用失败，请严格按JSON格式输出审查报告。",
                    false,
                    AgentToolPolicy.DOC_REVIEWER);
        }
    }
}
