package athena.coder.ai.workflow.node;

import athena.coder.ai.assistant.agent.RouterAgent;
import athena.coder.ai.assistant.agent.result.router.RouterResult;
import athena.coder.ai.assistant.agent.result.router.WorkflowMode;
import athena.coder.ai.tool.config.AgentToolPolicy;
import athena.coder.ai.workflow.entity.StepRole;
import athena.coder.ai.workflow.entity.WorkflowState;
import athena.coder.exception.RocAgentException;

import java.util.Map;

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
        notifyModelCalling(state);

        RouterAgent router = newChatAssistant(ctx.modelType(), RouterAgent.class, AgentToolPolicy.ROUTER);
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

        WorkflowMode mode = result.workflowMode();
        return Map.of(WORKFLOW_MODE, mode);
    }

    @Override
    protected StepRole stepRole() {
        return StepRole.ROUTER;
    }
}
