package athena.coder.ai.workflow.node;

import athena.coder.ai.assistant.agent.TestExecutorAgent;
import athena.coder.ai.assistant.agent.result.tester.TesterResult;
import athena.coder.ai.tool.config.AgentToolPolicy;
import athena.coder.ai.workflow.entity.NodeEnum;
import athena.coder.ai.workflow.entity.ProjectFacts;
import athena.coder.ai.workflow.entity.StepRole;
import athena.coder.ai.workflow.entity.TesterStatus;
import athena.coder.ai.workflow.entity.WorkflowState;
import athena.coder.ai.spi.ErrorLogger;

import java.time.LocalDate;
import java.util.Map;

import static athena.coder.ai.assistant.model.factory.AiAssistantFactory.newChatAssistant;
import static athena.coder.ai.workflow.entity.WorkflowState.*;

/**
 * 通用测试执行节点（合并原 CodeTestNode / TestRunNode / FixVerifyNode）
 * <p>
 * 场景差异经 {@link TestConfig} 注入：使命 scenario、指令 request、通过目标 passTarget（编码/补测→REVIEWER，修复→SUMMARIZER）。
 * 路由：PASS/SKIP → passTarget，FAIL/ERROR → DEBUGGER。
 */
public class TestNode extends AbstractAgentNode {

    private final TestConfig config;

    public TestNode(TestConfig config) {
        this.config = config;
    }

    /** 场景化工厂：编码 */
    public static TestNode code() {
        return new TestNode(TestConfig.code());
    }

    /** 场景化工厂：测试补全 */
    public static TestNode test() {
        return new TestNode(TestConfig.test());
    }

    /** 场景化工厂：缺陷修复 */
    public static TestNode fix() {
        return new TestNode(TestConfig.fix());
    }

    @Override
    protected StepRole stepRole() {
        return config.stepRole();
    }

    @Override
    protected Map<String, Object> doApply(WorkflowState state, NodeContext ctx) throws Exception {
        String changedFiles = requireUpstream(state.getChangedFiles(),
                "changedFiles 为空，无法执行测试（需要" + config.upstreamNoun() + "环节先完成）");
        String changedDiffRef = requireUpstream(state.getChangedDiffRef(),
                "changedDiffRef 为空，无法查看变更详情（需要" + config.upstreamNoun() + "环节先提交代码）");
        String acceptanceCriteria = requireUpstream(state.getAcceptanceCriteria(),
                "acceptanceCriteria 为空，缺少验收标准（需要主图规划环节先生成执行计划）");
        String projectFacts = ProjectFacts.toPromptBlock(state.getProjectFacts());

        notifyModelCalling(state);

        TestExecutorAgent assistant = newChatAssistant(ctx.modelType(), TestExecutorAgent.class, config.policy());
        AgentCall<TesterResult> call = request -> assistant.test(
                ctx.taskId(), request, ctx.projectPath(), ctx.projectType(),
                LocalDate.now().format(DATE_FMT), config.scenario(),
                changedFiles, changedDiffRef, acceptanceCriteria, projectFacts);

        TesterResult testResult = callAgentWithRetry(config.request(), config.retryRequest(), call, null);

        TesterStatus status = TesterStatus.from(testResult.status());
        String testResultJson = MAPPER.writeValueAsString(testResult);

        String testIcon = switch (status) {
            case PASS -> "[通过]";
            case FAIL, ERROR -> "[失败]";
            case SKIP -> "[跳过]";
        };
        String testMsg = switch (status) {
            case PASS -> config.passMsg();
            case FAIL -> config.failMsg();
            case ERROR -> config.errorMsg();
            case SKIP -> config.skipMsg();
        };
        notifyResult(state, testIcon, testMsg);

        return Map.of(NEXT_NODE, determineNextNode(status), TEST_RESULT, testResultJson);
    }

    private String determineNextNode(TesterStatus status) {
        return switch (status) {
            case PASS, SKIP -> {
                yield config.passTarget().name();
            }
            case FAIL, ERROR -> {
                ErrorLogger.warn(getClass().getSimpleName(), "测试失败/错误，路由到 DEBUGGER 进行分析");
                yield NodeEnum.DEBUGGER.name();
            }
        };
    }

    /**
     * 测试角色配置：使命/指令/通过目标/结果文案/工具权限
     */
    public record TestConfig(
            String scenario,
            String request,
            String retryRequest,
            String upstreamNoun,
            StepRole stepRole,
            String passMsg,
            String failMsg,
            String errorMsg,
            String skipMsg,
            NodeEnum passTarget,
            AgentToolPolicy policy) {

        public static TestConfig code() {
            return new TestConfig("编码工作流：对功能代码变更执行测试验证，精准验证功能实现",
                    "请对代码变更执行测试验证",
                    "请重新执行测试。注意：上次调用失败，请严格按JSON格式输出测试结果。",
                    "编码", StepRole.TESTER,
                    "测试通过", "测试失败，进入修复流程", "测试执行出错，进入修复流程", "测试被跳过",
                    NodeEnum.REVIEWER, AgentToolPolicy.TESTER);
        }

        public static TestConfig test() {
            return new TestConfig("测试补全工作流：执行新补写的测试用例并采集覆盖率数据",
                    "请执行新补写的测试用例并采集覆盖率数据",
                    "请重新执行测试。注意：上次调用失败，请严格按JSON格式输出测试结果。",
                    "补测", StepRole.TESTER,
                    "新测试全部通过", "新测试存在失败，进入失败分析", "测试执行出错，进入失败分析", "测试被跳过",
                    NodeEnum.REVIEWER, AgentToolPolicy.TESTER);
        }

        public static TestConfig fix() {
            return new TestConfig("缺陷修复工作流：对修复变更执行回归验证，确认缺陷复现路径转绿且无新增回归",
                    "请对修复变更执行回归验证，确认缺陷复现路径转绿且无新增回归",
                    "请重新执行回归验证。注意：上次调用失败，请严格按JSON格式输出验证结果。",
                    "修复", StepRole.TESTER,
                    "回归验证通过，缺陷已修复", "回归验证失败，进入根因分析", "验证执行出错，进入根因分析", "验证被跳过",
                    NodeEnum.SUMMARIZER, AgentToolPolicy.TESTER);
        }
    }
}
