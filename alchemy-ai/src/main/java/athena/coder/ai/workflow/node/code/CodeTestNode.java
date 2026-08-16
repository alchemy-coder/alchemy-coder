package athena.coder.ai.workflow.node.code;

import athena.coder.ai.assistant.agent.code.CodeTestAgent;
import athena.coder.ai.assistant.agent.result.tester.TesterResult;
import athena.coder.ai.workflow.entity.WorkflowState;
import athena.coder.ai.workflow.node.AbstractTesterNode;

import java.time.LocalDate;
import java.util.Map;

import static athena.coder.ai.assistant.model.factory.AiAssistantFactory.newChatAssistant;
import static athena.coder.ai.workflow.entity.NodeEnum.REVIEWER;
import static athena.coder.ai.workflow.entity.WorkflowState.*;

/**
 * 编码工作流 - 测试验证节点
 * <p>
 * 职责：对编码环节的代码变更执行测试验证，按结果路由：
 * PASS/SKIP → REVIEWER，FAIL/ERROR → DEBUGGER
 */
public class CodeTestNode extends AbstractTesterNode {

    public CodeTestNode() {
        super(REVIEWER);
    }

    @Override
    protected String stepLabel() {
        return "[测试]";
    }

    @Override
    protected Map<String, Object> doApply(WorkflowState state, NodeContext ctx) throws Exception {
        String changedFiles = requireUpstream(state.getStringValue(CHANGED_FILES),
                "changedFiles 为空，无法执行测试（需要编码环节先完成）");
        String changedDiffRef = requireUpstream(state.getStringValue(CHANGED_DIFF_REF),
                "changedDiffRef 为空，无法查看代码变更详情（需要编码环节先提交代码）");
        String acceptanceCriteria = requireUpstream(state.getStringValue(ACCEPTANCE_CRITERIA),
                "acceptanceCriteria 为空，缺少验收标准（需要主图规划环节先生成执行计划）");

        logStart(ctx, "开始测试",
                "changedFiles长度", changedFiles.length(),
                "changedDiffRef", truncate(changedDiffRef, 30),
                "acceptanceCriteria长度", acceptanceCriteria.length());
        notifyModelCalling(state);

        CodeTestAgent assistant = newChatAssistant(ctx.modelType(), CodeTestAgent.class);
        AgentCall<TesterResult> call = request -> assistant.test(
                ctx.taskId(),
                request,
                ctx.projectPath(),
                ctx.projectType(),
                LocalDate.now().format(DATE_FMT),
                changedFiles,
                changedDiffRef,
                acceptanceCriteria
        );

        TesterResult testResult = callAgentWithRetry(
                "请对代码变更执行测试验证",
                "请重新执行测试。注意：上次调用失败，请严格按JSON格式输出测试结果。",
                call, null);

        String status = (testResult.status() != null ? testResult.status() : "ERROR").toUpperCase();
        String testResultJson = MAPPER.writeValueAsString(testResult);

        logInfo(String.format("CodeTestNode 测试完成: status=%s, result长度=%d", status, testResultJson.length()));

        String testIcon = switch (status) {
            case "PASS" -> "[通过]";
            case "FAIL", "ERROR" -> "[失败]";
            case "SKIP" -> "[跳过]";
            default -> "[未知]";
        };
        String testMsg = switch (status) {
            case "PASS" -> "测试通过";
            case "FAIL" -> "测试失败，进入修复流程";
            case "ERROR" -> "测试执行出错，进入修复流程";
            case "SKIP" -> "测试被跳过";
            default -> "测试状态未知: " + status;
        };
        notifyResult(state, testIcon, testMsg);

        return Map.of(
                NEXT_NODE, determineNextNode(status),
                TEST_RESULT, testResultJson
        );
    }

}
