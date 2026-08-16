package athena.coder.ai.workflow.node.debug;

import athena.coder.ai.assistant.agent.debug.FixVerifyAgent;
import athena.coder.ai.assistant.agent.result.tester.TesterResult;
import athena.coder.ai.workflow.entity.WorkflowState;
import athena.coder.ai.workflow.node.AbstractTesterNode;

import java.time.LocalDate;
import java.util.Map;

import static athena.coder.ai.assistant.model.factory.AiAssistantFactory.newChatAssistant;
import static athena.coder.ai.workflow.entity.NodeEnum.SUMMARIZER;
import static athena.coder.ai.workflow.entity.WorkflowState.*;

/**
 * 缺陷修复工作流 - 回归验证节点
 * <p>
 * 职责：对修复改动执行回归验证（复现用例转绿 + 无新增回归），按结果路由：
 * PASS/SKIP → SUMMARIZER（本子图无审查环节，以测试通过为准），
 * FAIL/ERROR → DEBUGGER 重新分析
 */
public class FixVerifyNode extends AbstractTesterNode {

    public FixVerifyNode() {
        super(SUMMARIZER);
    }

    @Override
    protected String stepLabel() {
        return "[验证]";
    }

    @Override
    protected Map<String, Object> doApply(WorkflowState state, NodeContext ctx) throws Exception {
        String changedFiles = requireUpstream(state.getStringValue(CHANGED_FILES),
                "changedFiles 为空，无法执行回归验证（需要修复环节先完成）");
        String changedDiffRef = requireUpstream(state.getStringValue(CHANGED_DIFF_REF),
                "changedDiffRef 为空，无法查看修复变更详情（需要修复环节先提交代码）");
        String acceptanceCriteria = requireUpstream(state.getStringValue(ACCEPTANCE_CRITERIA),
                "acceptanceCriteria 为空，缺少缺陷验收标准（需要主图规划环节先生成执行计划）");

        logStart(ctx, "开始回归验证",
                "changedFiles长度", changedFiles.length(),
                "changedDiffRef", truncate(changedDiffRef, 30),
                "acceptanceCriteria长度", acceptanceCriteria.length());
        notifyModelCalling(state);

        FixVerifyAgent assistant = newChatAssistant(ctx.modelType(), FixVerifyAgent.class);
        AgentCall<TesterResult> call = request -> assistant.verify(
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
                "请对修复变更执行回归验证，确认缺陷复现路径转绿且无新增回归",
                "请重新执行回归验证。注意：上次调用失败，请严格按JSON格式输出验证结果。",
                call, null);

        String status = (testResult.status() != null ? testResult.status() : "ERROR").toUpperCase();
        String testResultJson = MAPPER.writeValueAsString(testResult);

        logInfo(String.format("FixVerifyNode 验证完成: status=%s, result长度=%d", status, testResultJson.length()));

        String testIcon = switch (status) {
            case "PASS" -> "[通过]";
            case "FAIL", "ERROR" -> "[失败]";
            case "SKIP" -> "[跳过]";
            default -> "[未知]";
        };
        String testMsg = switch (status) {
            case "PASS" -> "回归验证通过，缺陷已修复";
            case "FAIL" -> "回归验证失败，进入根因分析";
            case "ERROR" -> "验证执行出错，进入根因分析";
            case "SKIP" -> "验证被跳过";
            default -> "验证状态未知: " + status;
        };
        notifyResult(state, testIcon, testMsg);

        return Map.of(
                NEXT_NODE, determineNextNode(status),
                TEST_RESULT, testResultJson
        );
    }
}
