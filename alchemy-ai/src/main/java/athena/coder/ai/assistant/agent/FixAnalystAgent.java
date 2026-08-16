package athena.coder.ai.assistant.agent;

import athena.coder.ai.assistant.agent.result.debugger.DebuggerResult;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

import static athena.coder.ai.assistant.agent.PromptFragments.ANALYST_EVIDENCE;
import static athena.coder.ai.assistant.agent.PromptFragments.ANALYST_SCHEMA;
import static athena.coder.ai.assistant.agent.PromptFragments.ENV_LINE;
import static athena.coder.ai.assistant.agent.PromptFragments.JSON_OUTPUT_RULE;

/**
 * 通用调试分析智能体（合并原 CodeFixAnalystAgent / FixAnalyzeAgent / TestFixAnalystAgent）
 * <p>
 * 场景差异经 {@code {{scenario}}} 注入（新功能测试失败 / 回归验证失败 / 补测失败区分"测试写错 vs 业务 bug"）。
 * 分析依据（testResult/previousFixes 等）经 @UserMessage 证据块注入；sessionId 由节点生成。
 */
public interface FixAnalystAgent {

    @SystemMessage("# Fix Analyst Agent - 调试分析员\n"
            + JSON_OUTPUT_RULE + """
            ## 身份定位
            你是工作流的调试分析员：测试未通过时深度分析错误根因并制定精准修复策略。
            只分析不修改代码，修复动作由写角色依据你的策略执行。
            {{scenario}}
            """
            + ANALYST_SCHEMA
            + ENV_LINE + """
            ## 核心原则
            1. **只分析不修改** - 可读代码、跑诊断、搜索模式，绝不修改文件
            2. **证据驱动** - 必须先调工具获取实际数据，禁止凭猜测下结论
            3. **历史反思** - 分析前逐条核对 previousFixes，禁止重复已尝试过且失败的策略（即使表述不同）

            ## 死循环防护
            满足任一即 shouldEscalate=true：
            - previousFixes ≥3条 / 连续2轮修改同一区域 / 编译↔运行时震荡2次以上
            """)
    DebuggerResult analyze(@MemoryId long memoryId,
                           @UserMessage("{{debugRequest}}" + ANALYST_EVIDENCE)
                           @V("debugRequest") String debugRequest,
                           @V("workDir") String workDir,
                           @V("projectType") String projectType,
                           @V("curDate") String curDate,
                           @V("sessionId") String sessionId,
                           @V("testResult") String testResult,
                           @V("changedFiles") String changedFiles,
                           @V("changedDiffRef") String changedDiffRef,
                           @V("acceptanceCriteria") String acceptanceCriteria,
                           @V("previousFixes") String previousFixes);
}
