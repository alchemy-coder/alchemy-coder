package athena.coder.ai.assistant.agent;

import athena.coder.ai.assistant.agent.result.reviewer.ReviewerResult;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

import static athena.coder.ai.assistant.agent.PromptFragments.ENV_LINE;
import static athena.coder.ai.assistant.agent.PromptFragments.JSON_OUTPUT_RULE;
import static athena.coder.ai.assistant.agent.PromptFragments.PROJECT_FACTS_BLOCK;
import static athena.coder.ai.assistant.agent.PromptFragments.REVIEW_EVIDENCE;
import static athena.coder.ai.assistant.agent.PromptFragments.REVIEW_SCHEMA;

/**
 * 通用质量审查智能体（合并原 CodeReviewAgent / TestReviewAgent / DocReviewAgent）
 * <p>
 * 场景差异经 {@code {{scenario}}}（身份定位 + 审查维度）与 {@code {{stageResults}}}（审查阶段 schema）
 * 注入，核心原则与决策规则取三场景并集（安全零容忍 + 越界检测 + 结合证据）。
 * 无测试证据的场景（文档）将 testResult 传空串，审查以内容核对为主。
 * sessionId 由节点生成。
 */
public interface ReviewAgent {

    @SystemMessage("# Review Agent - 质量审查员\n"
            + JSON_OUTPUT_RULE + """
            ## 身份定位
            {{scenario}}
            """
            + REVIEW_SCHEMA + """
            其中 stageResults 使用：
            {{stageResults}}
            """
            + ENV_LINE + """
            ## 核心原则
            1. **优先采信项目知识上下文** - facts 里已有的文件路径与符号直接采用，勿重复 readFile 全文；仅缺失时才用工具探测
            2. **只审不改** - 只读取和分析，绝对不修改文件
            3. **安全零容忍** - 发现安全缺陷或越界修改（变更夹带本环节不应改的内容）必须 BLOCKED
            4. **结合证据** - 参考测试证据与源码事实下结论，禁止凭印象
            5. **可操作建议** - 每个问题附带修改建议或示例

            ## 决策规则
            - BLOCKER（安全/编译错误/核心缺失/越界修改/与代码事实严重不符）→ BLOCKED
            - CRITICAL/MAJOR 或测试未通过 → REQUEST_CHANGES
            - MINOR（样式/命名/措辞）→ APPROVED_WITH_NOTES
            - 其他 → APPROVED
            """)
    ReviewerResult review(@UserMessage("{{reviewRequest}}" + REVIEW_EVIDENCE + PROJECT_FACTS_BLOCK)
                          @V("reviewRequest") String reviewRequest,
                          @V("workDir") String workDir,
                          @V("projectType") String projectType,
                          @V("curDate") String curDate,
                          @V("sessionId") String sessionId,
                          @V("scenario") String scenario,
                          @V("stageResults") String stageResults,
                          @V("originalRequirement") String originalRequirement,
                          @V("changeSummary") String changeSummary,
                          @V("testResult") String testResult,
                          @V("acceptanceCriteria") String acceptanceCriteria,
                          @V("projectFacts") String projectFacts);
}
