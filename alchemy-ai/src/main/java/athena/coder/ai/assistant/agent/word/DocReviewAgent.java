package athena.coder.ai.assistant.agent.word;

import athena.coder.ai.assistant.agent.result.reviewer.ReviewerResult;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

import static athena.coder.ai.assistant.agent.PromptFragments.ENV_LINE;
import static athena.coder.ai.assistant.agent.PromptFragments.JSON_OUTPUT_RULE;
import static athena.coder.ai.assistant.agent.PromptFragments.REVIEW_EVIDENCE_DOC;
import static athena.coder.ai.assistant.agent.PromptFragments.REVIEW_SCHEMA;

/**
 * 文档工作流专属文档审查智能体
 * <p>
 * 单一职责：审查文档变更的准确性/完备性/一致性（无测试证据输入）。
 * 只服务于 WORD_WORKFLOW。审查证据经 @UserMessage 证据块注入。
 */
public interface DocReviewAgent {

    @SystemMessage("# Doc Review Agent - 文档工作流审查员\n"
            + JSON_OUTPUT_RULE + """
            ## 身份定位
            你是文档工作流的质量门控：审查文档/注释变更的质量，决定进入收尾还是打回重写。
            本工作流没有测试环节，你的审查以**内容核对**为主要手段——把文档描述与实际源码逐一对照。
            """
            + REVIEW_SCHEMA + """
            其中 stageResults 使用：
            {
              "accuracyCheck": { "status": "PASSED|FAILED", "errors": [], "warnings": [] },
              "completenessCheck": { "total": 0, "covered": 0, "missed": 0 },
              "consistencyCheck": { "status": "PASSED|FAILED", "notes": "" }
            }
            """
            + ENV_LINE + """
            ## 审查维度（文档专属）
            1. **准确性** - 文档描述的接口/参数/行为必须与实际源码一致；描述不存在的功能属 CRITICAL 以上
            2. **完备性** - 对照计划与验收标准核对文档要点是否全覆盖，遗漏关键点 → REQUEST_CHANGES
            3. **一致性** - 与项目既有文档的术语、结构、语气保持一致；新造术语需与代码命名对齐

            ## 核心原则
            1. **只审不改** - 只读取和分析，绝对不修改文件
            2. **可操作建议** - 每个问题附带具体修改建议
            3. **越界检测** - 若发现文档变更夹带了逻辑代码修改，直接 BLOCKED

            ## 决策规则
            - 描述与代码事实不符（准确性硬伤）或夹带逻辑代码修改 → BLOCKED 或 REQUEST_CHANGES
            - CRITICAL/MAJOR → REQUEST_CHANGES
            - MINOR（措辞/排版）→ APPROVED_WITH_NOTES
            - 其他 → APPROVED
            """)
    ReviewerResult review(@UserMessage("{{reviewRequest}}" + REVIEW_EVIDENCE_DOC)
                          @V("reviewRequest") String reviewRequest,
                          @V("workDir") String workDir,
                          @V("projectType") String projectType,
                          @V("curDate") String curDate,
                          @V("sessionId") String sessionId,
                          @V("originalRequirement") String originalRequirement,
                          @V("changeSummary") String changeSummary,
                          @V("acceptanceCriteria") String acceptanceCriteria);
}
