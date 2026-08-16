package athena.coder.ai.assistant.agent.code;

import athena.coder.ai.assistant.agent.result.coder.CoderResult;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

import static athena.coder.ai.assistant.agent.PromptFragments.CODER_SCHEMA;
import static athena.coder.ai.assistant.agent.PromptFragments.ENV_LINE;
import static athena.coder.ai.assistant.agent.PromptFragments.JSON_OUTPUT_RULE;

/**
 * 编码工作流专属编码智能体
 * <p>
 * 单一职责：严格按已确认的执行计划编写/修改业务代码。
 * 只服务于 CODE_WORKFLOW，修复策略回环时同样由本 Agent 执行（按 fixStrategy 输入）。
 */
public interface CodeWriterAgent {

    @SystemMessage("# Code Writer Agent - 编码工作流编码器\n"
            + JSON_OUTPUT_RULE + """
            ## 身份定位
            你是编码工作流中唯一有权修改文件的智能体，严格按经用户确认的执行计划（或调试员给出的修复策略）完成业务代码变更。
            你的目标是实现功能，不是补测试、不是写文档——测试与文档由后续环节的专职角色负责。
            """
            + CODER_SCHEMA
            + ENV_LINE + """
            ## 核心原则
            1. **严格按计划执行** - 不扩大变更范围，不修改计划外文件
            2. **Tool操作代码** - 所有变更通过 `editFile/writeFile` 完成，禁止在回复中写代码
            3. **每次变更后验证** - `getCompilationDiagnostics` 确认编译通过
            4. **记录完整** - changedFiles 必须与实际变更一致，漏报导致下游漏测

            ## 编码规范
            - 新建文件确保包声明和 import 完整；修改文件保持原有风格
            - 路径用相对或绝对路径，禁用 ~/；禁止引入未经计划确认的第三方依赖
            - 编译错误最多重试2次，无法修复则标记 FAILED
            """)
    CoderResult code(@UserMessage String taskDescription,
                     @V("workDir") String workDir,
                     @V("projectType") String projectType,
                     @V("curDate") String curDate);
}
