package athena.coder.ai.assistant.agent;

import athena.coder.ai.assistant.agent.result.coder.CoderResult;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

import static athena.coder.ai.assistant.agent.PromptFragments.CODER_SCHEMA;
import static athena.coder.ai.assistant.agent.PromptFragments.ENV_LINE;
import static athena.coder.ai.assistant.agent.PromptFragments.JSON_OUTPUT_RULE;
import static athena.coder.ai.assistant.agent.PromptFragments.PROJECT_FACTS_BLOCK;

/**
 * 通用受约束编写智能体（合并原 TestWriteAgent / DocWriteAgent）
 * <p>
 * 场景差异经 {@code {{scenario}}} 与 {@code {{hardConstraint}}} 注入：
 * TEST_WORKFLOW 传补测使命与"禁改被测业务代码"硬约束；
 * WORD_WORKFLOW 传文档使命与"禁改逻辑代码"硬约束。
 */
public interface GenericWriterAgent {

    @SystemMessage("# Constrained Writer Agent - 受约束编写器\n"
            + JSON_OUTPUT_RULE + """
            ## 身份定位
            你是本工作流中唯一有权修改文件的智能体。
            {{scenario}}
            """
            + CODER_SCHEMA
            + ENV_LINE + """
            ## 硬约束（违反即失败）
            {{hardConstraint}}

            ## 核心原则
            1. **优先采信项目知识上下文** - facts 里已有的文件路径与符号直接采用，勿重复 readFile 全文；仅缺失时才用工具探测
            2. **先读后写** - 动笔前先读相关源码，断言/描述必须基于真实行为与接口签名，禁止臆造
            3. **风格一致** - 沿用项目既有目录结构、命名风格与排版习惯
            4. **Tool操作文件** - 所有变更通过 `editFile/writeFile` 完成，禁止在回复中写代码/正文
            5. **每次变更后验证** - `getCompilationDiagnostics` 确认编译通过
            6. **记录完整** - changedFiles 必须与实际变更一致，漏报导致下游漏测
            """)
    CoderResult write(@UserMessage("{{taskDescription}}" + PROJECT_FACTS_BLOCK)
                      @V("taskDescription") String taskDescription,
                      @V("workDir") String workDir,
                      @V("projectType") String projectType,
                      @V("curDate") String curDate,
                      @V("scenario") String scenario,
                      @V("hardConstraint") String hardConstraint,
                      @V("projectFacts") String projectFacts);
}
