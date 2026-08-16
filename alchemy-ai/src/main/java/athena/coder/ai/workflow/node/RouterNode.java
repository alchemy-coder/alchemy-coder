package athena.coder.ai.workflow.node;

import athena.coder.ai.assistant.agent.RouterAgent;
import athena.coder.ai.assistant.agent.result.router.RouterResult;
import athena.coder.ai.assistant.agent.result.router.WorkflowMode;
import athena.coder.ai.workflow.entity.WorkflowState;
import athena.coder.exception.RocAgentException;

import java.util.Map;
import java.util.Objects;

import static athena.coder.ai.assistant.model.factory.AiAssistantFactory.newChatAssistant;
import static athena.coder.ai.workflow.entity.WorkflowState.WORKFLOW_MODE;

/**
 * 路由节点
 * <p>
 * 职责：调用 RouterAgent 进行意图分类，路由到对应工作流入口节点，
 * 并将 {@link WorkflowMode} 写入 state 供子工作流内的节点读取。
 */
public class RouterNode extends AbstractAgentNode {

    @Override
    protected Map<String, Object> doApply(WorkflowState state, NodeContext ctx) throws Exception {
        logStart(ctx, "开始路由");
        notifyModelCalling(state);

        RouterAgent router = newChatAssistant(ctx.modelType(), RouterAgent.class);
        AgentCall<RouterResult> call = request -> {
            RouterResult r = router.route(request, ctx.projectPath());
            if (r.workflowMode() == null) {
                throw new IllegalStateException("RouterAgent workflowMode 为空");
            }
            return r;
        };

        RouterResult result = callAgentWithRetry(
                state.buildRoutedMessage(),
                "你上次的输出格式不正确，请严格按JSON格式重新输出。用户消息: " + state.getUserMessage(),
                call,
                e -> {
                    throw new RocAgentException("路由智能体路由失败", e);
                });

        if (Objects.isNull(result) || Objects.isNull(result.workflowMode())) {
            throw new RocAgentException("路由智能体路由失败");
        }
        WorkflowMode mode = result.workflowMode();
        logInfo("RouterNode 路由完成: workflowMode=" + mode);
        return Map.of(WORKFLOW_MODE, mode);
    }

    @Override
    protected String stepLabel() {
        return "[路由]";
    }
}
