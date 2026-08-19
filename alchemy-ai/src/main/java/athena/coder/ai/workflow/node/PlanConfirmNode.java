package athena.coder.ai.workflow.node;

import athena.coder.ai.assistant.agent.ConfirmIntentAgent;
import athena.coder.ai.assistant.agent.result.confirm.ConfirmIntent;
import athena.coder.ai.assistant.agent.result.confirm.ConfirmIntentResult;
import athena.coder.ai.assistant.agent.result.router.WorkflowMode;
import athena.coder.ai.tool.config.AgentToolPolicy;
import athena.coder.ai.workflow.entity.WorkflowState;
import athena.coder.ai.workflow.gate.HumanGate;
import athena.coder.entity.chat.ChatEnum;
import athena.coder.exception.RocAgentException;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static athena.coder.ai.assistant.model.factory.AiAssistantFactory.newChatAssistant;
import static athena.coder.ai.workflow.entity.NodeEnum.PLANNER;
import static athena.coder.ai.workflow.entity.WorkflowState.*;
import static org.bsc.langgraph4j.GraphDefinition.END;

/**
 * 规划人工确认节点（阻塞门）
 * <p>
 * 负责：
 * 1. 输出确认提示，通过 {@link HumanGate} 阻塞等待用户回复（默认 30 分钟超时，超时终止流程）
 * 2. 调 ConfirmIntentAgent 判定回复意图（确认/拒绝），模型失败时降级为关键词规则兜底；拒绝时以用户原文作为修改意见
 * 3. 确认 → 按 WORKFLOW_MODE 分流到对应子工作流节点（枚举名与 NodeEnum 同名，信号零映射；
 *    mode 由 ROUTER 节点校验保证非空，此处直接取用）
 * 4. 拒绝 → 将修改意见写入 PLAN_FEEDBACK，回到 PLANNER 重新规划（带回环熔断）
 */
public class PlanConfirmNode extends AbstractAgentNode {

    /**
     * 拒绝重规划熔断上限
     */
    private static final int MAX_REPLAN_COUNT = 3;

    private static final Set<String> CONFIRM_WORDS = Set.of(
            "确认", "确认执行", "同意", "执行", "继续", "开始执行", "没问题",
            "ok", "okay", "yes", "y");

    private static final String DEFAULT_FEEDBACK = "用户对当前计划不满意，请重新审视需求、调整任务拆解后重新规划";

    @Override
    protected Map<String, Object> doApply(WorkflowState state, NodeContext ctx) throws Exception {
        Long taskId = state.getTaskId();

        // 用确认卡片类型输出：独立醒目样式；且与 PLANNER 的 ROBOT_RESULT 计划内容不同 type，upsert 不会互相覆盖
        state.outputBotResponse("""
                ## 📋 执行计划已就绪，等待您的确认

                请审阅上方的执行计划，并选择下一步操作：

                - ✅ 点击下方 **【确认执行】** 按钮（或直接回复 **确认**）—— 立即按计划开始执行
                - ✏️ 直接回复修改意见 —— 我将按您的意见重新规划

                > 💡 意见示例：“任务2的存储方案改为文件存储”、“把任务3拆解得更细一些”\
                """, ChatEnum.ROBOT_CONFIRM);

        String reply;
        try {
            reply = HumanGate.await(taskId);
        } catch (RocAgentException e) {
            // 确认超时/等待中断：门已在 finally 清理，用户后续消息走新工作流
            state.outputBotResponse("⏰ 执行计划确认超时，本次流程结束。如需继续，请重新发送消息。", ChatEnum.ROBOT);
            return Map.of(NEXT_NODE, END);
        } finally {
            HumanGate.remove(taskId);
        }

        ConfirmIntentResult intentResult = classifyReply(state, ctx, reply);

        if (intentResult.intent() == ConfirmIntent.CONFIRM) {
            // WORKFLOW_MODE 由 ROUTER 节点写入并校验非空（失败则抛异常终止流程，不会到达本节点）
            WorkflowMode mode = ctx.requireWorkflowMode();
            state.outputBotResponse("✅ 计划已确认，进入" + mode.label() + "开始执行...", ChatEnum.ROBOT);
            // WorkflowMode 与 NodeEnum 子工作流枚举同名，name() 即主图路由信号，零映射
            return Map.of(NEXT_NODE, mode.name());
        }

        // 拒绝：回环计数熔断
        int count = state.getIntValue(PLAN_CONFIRM_COUNT) + 1;
        if (count > MAX_REPLAN_COUNT) {
            state.outputBotResponse("⚠️ 已达到重新规划次数上限（" + MAX_REPLAN_COUNT + " 次），本次流程终止。", ChatEnum.ROBOT);
            return Map.of(NEXT_NODE, END);
        }

        // 修改意见直接取用户原文，避免模型提炼造成信息折损
        String feedback = (reply == null || reply.isBlank()) ? DEFAULT_FEEDBACK : reply;
        notifyProgress(state, stepLabel(), "用户拒绝计划，将按修改意见重新规划...");

        Map<String, Object> ret = new HashMap<>();
        ret.put(PLAN_FEEDBACK, feedback);
        ret.put(PLAN_CONFIRM_COUNT, count);
        ret.put(NEXT_NODE, PLANNER.name());
        return ret;
    }

    /**
     * 调用 ConfirmIntentAgent 判定回复意图；模型调用失败或返回不完整时，
     * 降级为关键词规则兜底（精确命中确认词→CONFIRM，否则 REJECT）
     */
    private ConfirmIntentResult classifyReply(WorkflowState state, NodeContext ctx, String reply) throws Exception {
        notifyModelCalling(state);
        ConfirmIntentAgent agent = newChatAssistant(ctx.modelType(), ConfirmIntentAgent.class, AgentToolPolicy.CONFIRM_INTENT);
        String planSummary = requireUpstream(state.getStringValue(PLAN), "PLAN 缺失，无法进行确认意图判定");
        AgentCall<ConfirmIntentResult> call = request -> {
            ConfirmIntentResult r = agent.classify(request, planSummary);
            if (r == null || r.intent() == null) {
                throw new Exception("ConfirmIntentAgent 返回结果不完整（intent为空）");
            }
            return r;
        };
        try {
            return callAgentWithRetry(reply,
                    "你上次的输出格式不正确，请重新判定用户意图并严格按JSON格式输出。用户回复: " + reply,
                    call, null);
        } catch (Exception e) {
            return fallbackClassify(reply);
        }
    }

    /**
     * 规则兜底：精确命中确认词 → CONFIRM；否则 REJECT
     */
    private ConfirmIntentResult fallbackClassify(String reply) {
        if (reply != null) {
            String normalized = reply.trim().toLowerCase(Locale.ROOT)
                    .replaceAll("[!！。.，,~～\\s]", "");
            if (CONFIRM_WORDS.contains(normalized)) {
                return new ConfirmIntentResult(ConfirmIntent.CONFIRM);
            }
        }
        return new ConfirmIntentResult(ConfirmIntent.REJECT);
    }

    @Override
    protected String stepLabel() {
        return "[确认]";
    }

}
