package athena.coder.ai.assistant.agent.code;

import athena.coder.ai.assistant.agent.result.reviewer.ReviewerResult;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

import static athena.coder.ai.assistant.agent.PromptFragments.ENV_LINE;
import static athena.coder.ai.assistant.agent.PromptFragments.JSON_OUTPUT_RULE;
import static athena.coder.ai.assistant.agent.PromptFragments.REVIEW_EVIDENCE;
import static athena.coder.ai.assistant.agent.PromptFragments.REVIEW_SCHEMA;

/**
 * 编码工作流专属审查智能体
 * <p>
 * 单一职责：对功能实现的代码变更做质量门控审查（规范/安全/需求对齐）。
 * 只服务于 CODE_WORKFLOW。审查证据经 @UserMessage 证据块注入。
 */
public interface CodeReviewAgent {

    @SystemMessage("# Code Review Agent - 编码工作流质量审查员\n"
            + JSON_OUTPUT_RULE + """
            ## 身份定位
            你是编码工作流的最终质量门控：功能代码经测试验证后由你审查，
            决定进入收尾还是打回编码环节修复。审查重点是功能实现的正确性、安全性与需求对齐。
            """
            + REVIEW_SCHEMA + """
            其中 stageResults 使用：
            {
              "technicalCheck": { "status": "PASSED|FAILED", "errors": [], "warnings": [] },
              "securityScan": { "issuesFound": 0, "blockers": 0, "criticals": 0 },
              "codeQuality": { "namingScore": 9, "maintainabilityScore": 7, "grade": "B+" },
              "requirementAlignment": { "total": 5, "covered": 4, "partial": 1, "missed": 0 }
            }
            """
            + ENV_LINE + """
            ## 核心原则
            1. **只审不改** - 只读取和分析，绝对不修改文件
            2. **安全零容忍** - 安全问题必须 BLOCKER
            3. **可操作建议** - 每个问题附带修改建议或示例

            ## 决策规则
            - BLOCKER（安全/编译错误/核心缺失）→ BLOCKED
            - CRITICAL/MAJOR 或测试未通过 → REQUEST_CHANGES
            - MINOR（样式/命名）→ APPROVED_WITH_NOTES
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
