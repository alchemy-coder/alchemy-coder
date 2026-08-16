package athena.coder.ai.workflow.node;

import athena.coder.ai.assistant.agent.UserFaceAssistant;
import athena.coder.ai.assistant.agent.result.user.UserFaceMode;
import athena.coder.ai.assistant.agent.result.user.UserFaceResult;
import athena.coder.ai.rag.RagManager;
import athena.coder.ai.tool.config.AgentToolPolicy;
import athena.coder.ai.workflow.entity.WorkflowState;
import athena.coder.ai.spi.ErrorLogger;
import athena.coder.entity.chat.ChatEnum;

import java.util.Map;

import static athena.coder.ai.assistant.model.factory.AiAssistantFactory.newChatAssistant;
import static athena.coder.ai.workflow.entity.NodeEnum.ROUTER;
import static athena.coder.ai.workflow.entity.WorkflowState.NEXT_NODE;
import static athena.coder.ai.workflow.entity.WorkflowState.ROUTE_CONTEXT;
import static org.bsc.langgraph4j.GraphDefinition.END;

/**
 * 用户入口节点
 * <p>
 * 工作流的起点，负责：
 * 1. 从 state 读取用户消息
 * 2. 调用 UserFaceAssistant 处理（同步，返回 UserFaceResult）
 * 3. 按 mode 分流：DIRECT → END / ROUTE → ROUTER / CLARIFY → END
 */
public class UserFaceNode extends AbstractAgentNode {

    @Override
    protected Map<String, Object> doApply(WorkflowState state, NodeContext ctx) throws Exception {
        String userMessage = state.getUserMessage();
        logStart(ctx, "开始处理", "userMessage", truncate(userMessage, 100));
        notifyModelCalling(state);

        // 每轮对话入口刷新索引：增量扫描无变更时零 API 开销，保证代码变更后 RAG 不长期滞后
        RagManager.getInstance().indexAsync(ctx.projectPath());

        UserFaceAssistant assistant = newChatAssistant(ctx.modelType(), UserFaceAssistant.class, AgentToolPolicy.USER_FACE);
        AgentCall<UserFaceResult> call = request -> assistant.chat(ctx.taskId(), request, ctx.projectPath());

        UserFaceResult result = callAgentWithRetry(
                userMessage,
                "你上次的输出格式不正确，请严格按JSON格式重新输出。用户消息: " + userMessage,
                call,
                ex -> {
                    ErrorLogger.warn("UserFaceNode", "UserFaceAssistant 输出两次解析失败: " + ex.getMessage());
                    return new UserFaceResult(UserFaceMode.CLARIFY,
                            "抱歉，系统暂时无法理解您的请求，请重新描述一下您的问题。",
                            "用户意图不明确，无法解析");
                });

        return switch (result.mode()) {
            case ROUTE -> Map.of(NEXT_NODE, ROUTER.name(), ROUTE_CONTEXT, result.routeContext());
            case CLARIFY, DIRECT -> {
                state.outputBotResponse(result.content(), ChatEnum.ROBOT_RESULT);
                yield Map.of(NEXT_NODE, END);
            }
        };
    }

    @Override
    protected String stepLabel() {
        return "[用户]";
    }
}
