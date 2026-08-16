package athena.coder.ai.assistant.agent;

import athena.coder.ai.assistant.agent.result.summarizer.SummarizerResult;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

import static athena.coder.ai.assistant.agent.PromptFragments.ENV_LINE;
import static athena.coder.ai.assistant.agent.PromptFragments.JSON_OUTPUT_RULE;
import static athena.coder.ai.assistant.agent.PromptFragments.REPORT_EVIDENCE;
import static athena.coder.ai.assistant.agent.PromptFragments.REPORT_SCHEMA;

/**
 * 通用收尾报告智能体（合并原 Code/Fix/Test/Doc ReportAgent）
 * <p>
 * 场景差异经 {@code {{scenario}}} 注入（报告视角/commit type/分支前缀/专属结构要求）；
 * 无对应证据的章节传空串并在 scenario 中说明（如文档流程无测试结果）。
 * sessionId 由节点生成。
 */
public interface ReportAgent {

    @SystemMessage("# Report Agent - 收尾报告员\n"
            + JSON_OUTPUT_RULE + """
            ## 身份定位
            你是工作流的收尾报告员：整合各环节执行结果，生成交付报告与 Commit Message。
            {{scenario}}
            """
            + REPORT_SCHEMA
            + ENV_LINE + """
            ## 核心原则
            1. **只读不改** - 只分析和总结，绝对不修改文件
            2. **基于事实** - 所有结论来自上游节点客观数据，验证未通过时禁止粉饰为已修复
            3. **规范输出** - Commit Message 遵循 Conventional Commits 规范
            """)
    SummarizerResult report(@MemoryId long memoryId,
                            @UserMessage("{{summarizeRequest}}" + REPORT_EVIDENCE)
                            @V("summarizeRequest") String summarizeRequest,
                            @V("workDir") String workDir,
                            @V("curDate") String curDate,
                            @V("sessionId") String sessionId,
                            @V("commitType") String commitType,
                            @V("branchPrefix") String branchPrefix,
                            @V("scenario") String scenario,
                            @V("originalRequirement") String originalRequirement,
                            @V("changeSummary") String changeSummary,
                            @V("fixStrategy") String fixStrategy,
                            @V("testResult") String testResult,
                            @V("reviewResult") String reviewResult);
}
