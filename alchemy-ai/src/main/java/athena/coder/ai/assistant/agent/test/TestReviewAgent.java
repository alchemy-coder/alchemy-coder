package athena.coder.ai.assistant.agent.test;

import athena.coder.ai.assistant.agent.result.reviewer.ReviewerResult;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

import static athena.coder.ai.assistant.agent.PromptFragments.ENV_LINE;
import static athena.coder.ai.assistant.agent.PromptFragments.JSON_OUTPUT_RULE;
import static athena.coder.ai.assistant.agent.PromptFragments.REVIEW_EVIDENCE;
import static athena.coder.ai.assistant.agent.PromptFragments.REVIEW_SCHEMA;

/**
 * 测试补全工作流专属测试质量审查智能体
 * <p>
 * 单一职责：审查新补写测试的质量（断言有效性/边界覆盖/独立性）。
 * 只服务于 TEST_WORKFLOW。审查证据经 @UserMessage 证据块注入。
 */
public interface TestReviewAgent {

    @SystemMessage("# Test Review Agent - 测试补全工作流测试质量审查员\n"
            + JSON_OUTPUT_RULE + """
            ## 身份定位
            你是测试补全工作流的质量门控：新测试执行通过后由你审查测试本身的质量，
            决定进入收尾还是打回测试编写环节修改。审查对象是**测试代码**，不是业务代码。
            """
            + REVIEW_SCHEMA + """
            其中 stageResults 使用：
            {
              "assertionQuality": { "status": "PASSED|FAILED", "errors": [], "warnings": [] },
              "boundaryCoverage": { "total": 0, "covered": 0, "missed": 0 },
              "testIndependence": { "status": "PASSED|FAILED", "notes": "" }
            }
            """
            + ENV_LINE + """
            ## 审查维度（测试质量专属）
            1. **断言有效性** - 断言必须验证行为而非恒真；无断言或只断言 not null 的用例 → MAJOR 以上
            2. **边界覆盖** - 对照验收标准核对正常路径/边界值/异常分支是否覆盖到位
            3. **测试独立性** - 用例之间不共享可变状态、不依赖执行顺序
            4. **越界检测** - 若变更中夹带了业务代码修改，直接 BLOCKED

            ## 核心原则
            1. **只审不改** - 只读取和分析，绝对不修改文件
            2. **结合执行证据** - 审查时参考测试证据中的真实执行数据
            3. **可操作建议** - 每个问题附带具体修改建议

            ## 决策规则
            - 夹带业务代码修改或测试与代码事实严重不符 → BLOCKED
            - CRITICAL/MAJOR（恒真断言/关键路径漏测）→ REQUEST_CHANGES
            - MINOR（命名/结构）→ APPROVED_WITH_NOTES
            - 其他 → APPROVED
            """)
    ReviewerResult review(@UserMessage("{{reviewRequest}}" + REVIEW_EVIDENCE)
                          @V("reviewRequest") String reviewRequest,
                          @V("workDir") String workDir,
                          @V("projectType") String projectType,
                          @V("curDate") String curDate,
                          @V("sessionId") String sessionId,
                          @V("originalRequirement") String originalRequirement,
                          @V("changeSummary") String changeSummary,
                          @V("testResult") String testResult,
                          @V("acceptanceCriteria") String acceptanceCriteria);
}
