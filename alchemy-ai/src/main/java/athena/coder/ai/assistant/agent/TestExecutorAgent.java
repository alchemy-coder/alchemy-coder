package athena.coder.ai.assistant.agent;

import athena.coder.ai.assistant.agent.result.tester.TesterResult;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

import static athena.coder.ai.assistant.agent.PromptFragments.ENV_LINE;
import static athena.coder.ai.assistant.agent.PromptFragments.JSON_OUTPUT_RULE;
import static athena.coder.ai.assistant.agent.PromptFragments.PROJECT_FACTS_BLOCK;
import static athena.coder.ai.assistant.agent.PromptFragments.TESTER_SCHEMA;
import static athena.coder.ai.assistant.agent.PromptFragments.TEST_EVIDENCE;

/**
 * 通用测试执行智能体（合并原 CodeTestAgent / FixVerifyAgent / TestRunAgent）
 * <p>
 * 场景差异经 {@code {{scenario}}} 注入（精准验证功能实现 / 回归验证缺陷转绿 / 执行新补测试并采集覆盖率）。
 * 测试依据（changedFiles/diffRef/验收标准）经 @UserMessage 证据块注入。
 */
public interface TestExecutorAgent {

    @SystemMessage("# Test Executor Agent - 测试执行员\n"
            + JSON_OUTPUT_RULE + """
            ## 身份定位
            你只执行测试、只报告事实，不修改任何文件、不给出修复建议。
            {{scenario}}
            """
            + TESTER_SCHEMA
            + ENV_LINE + """
            ## 核心原则
            1. **优先采信项目知识上下文** - facts 里已有的文件路径与符号直接采用，勿重复 readFile 全文；仅缺失时才用工具探测
            2. **精确执行** - 基于变更文件选择测试范围，禁止盲跑全量
            3. **数据真实** - 通过 `executeCommand/testExecution` 工具真实运行，禁止编造测试结果
            4. **完整记录** - stdout + stderr + exitCode + duration 缺一不可
            5. **失败必详** - FAIL 时必须给出完整错误信息与关键堆栈，供分析员定位

            ## 判定标准
            - PASS：目标测试通过且无新增回归失败
            - FAIL：目标测试仍失败或出现新的回归失败
            - SKIP：项目无可执行测试（需在 summary 说明原因）
            - ERROR：测试执行环境异常（非被测代码问题）
            """)
    TesterResult test(@MemoryId long memoryId,
                      @UserMessage("{{testRequest}}" + TEST_EVIDENCE + PROJECT_FACTS_BLOCK)
                      @V("testRequest") String testRequest,
                      @V("workDir") String workDir,
                      @V("projectType") String projectType,
                      @V("curDate") String curDate,
                      @V("scenario") String scenario,
                      @V("changedFiles") String changedFiles,
                      @V("changedDiffRef") String changedDiffRef,
                      @V("acceptanceCriteria") String acceptanceCriteria,
                      @V("projectFacts") String projectFacts);
}
