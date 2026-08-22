package athena.coder.ai.workflow.node;

import athena.coder.ai.assistant.agent.result.router.WorkflowMode;
import athena.coder.ai.spi.AgentExecution;
import athena.coder.ai.spi.AgentExecutionSink;
import athena.coder.ai.spi.AiInfra;
import athena.coder.ai.spi.ErrorLogger;
import athena.coder.ai.tool.base.ToolInvocationLogger;
import athena.coder.ai.workflow.entity.StepRole;
import athena.coder.ai.workflow.entity.WorkflowState;
import athena.coder.ai.util.ProjectTypeUtil;
import athena.coder.entity.chat.ChatEnum;
import athena.coder.entity.model.LLMModelEnum;
import athena.coder.exception.RocAgentException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.gson.Gson;
import org.bsc.langgraph4j.action.NodeAction;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Agent 节点模板基类
 * <p>
 * 抽取各 Agent 节点的公共骨架：
 * 1. {@link #apply} 模板方法：state 非空校验 + 基础上下文校验（projectPath/taskId/modelType），
 * 构建 {@link NodeContext} 快照后委托子类 {@link #doApply} 执行差异化业务
 * 2. {@link #callAgentWithRetry}：统一的“首调 → 任何异常带错误信息重试一次 → 兕底/上抛”策略（泛型，支持强类型结果）
 * 3. {@link #requireUpstream}/{@link #warnIfBlank}：上游数据声明式校验
 * 4. {@link #textAt}：Agent 输出 JSON 字段提取（JSON Pointer）
 * 5. {@link #buildChangeSummary}：changedFiles + diffRef 合并为结构化 JSON（Jackson 构建，避免手拼注入）
 * 6. {@link #sessionId}：会话 ID 节点侧生成（提示词不再让 LLM 自制 ID）
 * 7. 每次节点执行把入参/出参/当前 state 持久化到 {@link AgentExecutionSink}（替代原执行日志）
 * <p>
 * 节点差异化逻辑（determineNextNode/输出组装）保留在各子类与角色基类中
 */
public abstract class AbstractAgentNode implements NodeAction<WorkflowState> {

    protected static final ObjectMapper MAPPER = new ObjectMapper();
    protected static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final Gson GSON = new Gson();

    /**
     * 校验上游必填数据，为空时抛出携带指引信息的业务异常
     */
    protected static String requireUpstream(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new RocAgentException(message);
        }
        return value;
    }

    /**
     * 提取 JSON 文本字段，缺失时返回默认值
     */
    protected static String textAt(JsonNode node, String pointer) {
        if (node == null) {
            return null;
        }
        JsonNode target = node.at(pointer);
        return target.isMissingNode() || target.isNull() ? null : target.asText();
    }

    /**
     * 构建 changeSummary（changedFiles + diffRef 合并为结构化 JSON）
     * <p>
     * 用 Jackson 构建替代手拼字符串，文件名含引号/反斜杠等特殊字符时不会产生非法 JSON
     */
    protected static String buildChangeSummary(String changedFiles, String changedDiffRef) {
        ObjectNode summary = MAPPER.createObjectNode();
        ArrayNode files = summary.putArray("changedFiles");
        if (changedFiles != null && !changedFiles.isBlank()) {
            for (String file : changedFiles.split(",")) {
                files.add(file.trim());
            }
        }
        summary.put("diffRef", changedDiffRef != null ? changedDiffRef : "");
        return summary.toPrettyString();
    }

    /**
     * 截断长字符串用于日志显示
     */
    protected static String truncate(String str, int maxLength) {
        if (str == null) return "null";
        if (str.length() <= maxLength) return str;
        return str.substring(0, maxLength) + "...";
    }

    @Override
    public final Map<String, Object> apply(WorkflowState state) throws Exception {
        if (state == null) {
            throw new RocAgentException(getClass().getSimpleName() + ": state 不能为 null");
        }
        validateBaseContext(state);
        String nodeName = getClass().getSimpleName();
        long startMs = System.currentTimeMillis();
        String inputJson = toJson(state.data());
        enableToolProgress(state);
        try {
            Map<String, Object> result = doApply(state, buildContext(state));
            long costMs = System.currentTimeMillis() - startMs;
            record(state, nodeName, "END", inputJson, result, null, costMs);
            return result;
        } catch (Exception e) {
            ErrorLogger.log(nodeName, e, state.getTaskId(), null, null);
            long costMs = System.currentTimeMillis() - startMs;
            record(state, nodeName, "ERROR", inputJson, null, e.getMessage(), costMs);
            throw e;
        } finally {
            disableToolProgress();
        }
    }

    /**
     * 子类实现差异化业务：读取上游数据 → 调用 Agent → 解析结果 → 组装输出与路由信号
     */
    protected abstract Map<String, Object> doApply(WorkflowState state, NodeContext ctx) throws Exception;

    /**
     * 构建节点执行上下文快照
     */
    private NodeContext buildContext(WorkflowState state) {
        String projectPath = state.getWorkFullPath();
        Object wm = state.data().get(WorkflowState.WORKFLOW_MODE);
        return new NodeContext(
                projectPath,
                state.getTaskId(),
                state.getModelType(),
                wm instanceof WorkflowMode mode ? mode : null,
                ProjectTypeUtil.detect(projectPath));
    }

    /**
     * 校验基础上下文（所有节点共同的前置条件）
     */
    private void validateBaseContext(WorkflowState state) {
        String nodeName = getClass().getSimpleName();
        String projectPath = state.getWorkFullPath();
        if (projectPath == null || projectPath.isBlank()) {
            throw new RocAgentException(nodeName + ": projectPath 不能为空");
        }
        if (!Files.exists(Path.of(projectPath))) {
            throw new RocAgentException(nodeName + ": 项目目录不存在: " + projectPath);
        }
        if (state.getTaskId() == null) {
            throw new RocAgentException(nodeName + ": taskId 不能为空");
        }
        if (state.getModelType() == null) {
            throw new RocAgentException(nodeName + ": modelType 不能为空");
        }
    }

    /**
     * 调用 Agent（含统一重试策略）：
     * - 首调失败（任何异常，包括 RocAgentException）→ 用重试指令（携带错误信息）再调一次
     * - 重试仍失败 → fallback 非 null 时返回兜底结果，否则上抛由子工作流统一处理
     *
     * @param firstRequest 首次调用的指令文本
     * @param retryRequest 重试调用的指令文本前缀（会自动追加错误信息）
     * @param call         实际的 Agent 调用
     * @param fallback     重试失败后的兜底结果构建器，null 表示不兜底直接抛出
     */
    protected <T> T callAgentWithRetry(String firstRequest, String retryRequest,
                                       AgentCall<T> call, Function<Exception, T> fallback) throws Exception {
        try {
            return call.invoke(firstRequest);
        } catch (Exception e) {
            ErrorLogger.log(getClass().getSimpleName(), e);
            try {
                return call.invoke(retryRequest + "错误信息: " + e.getMessage());
            } catch (Exception retryEx) {
                ErrorLogger.log(getClass().getSimpleName(), retryEx);
                if (fallback != null) {
                    return fallback.apply(retryEx);
                }
                throw retryEx;
            }
        }
    }

    /**
     * 上游可选数据为空时仅记录告警（不中断流程）
     */
    protected void warnIfBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            ErrorLogger.warn(getClass().getSimpleName(), message);
        }
    }

    // ==================== 节点执行持久化 ====================

    /**
     * 序列化 state 为 JSON；过滤掉不可 JSON 化的 {@link BiConsumer}（BOT_RESPONSE 回调），
     * 其余原样完整持久化。Gson 在 JPMS 下无法序列化 lambda（会反射其捕获字段），必须先行剔除。
     */
    private static String toJson(Map<String, Object> data) {
        if (data == null) {
            return null;
        }
        Map<String, Object> persistable = new HashMap<>(data);
        persistable.values().removeIf(v -> v instanceof BiConsumer);
        return GSON.toJson(persistable);
    }

    /**
     * 落库一次节点执行（入参/出参/当前 state）；sink 未装配时静默跳过，不阻断主流程。
     *
     * @param output 出参 state 增量，ERROR 时传 null
     */
    private void record(WorkflowState state, String nodeName, String phase,
                        String inputJson, Map<String, Object> output, String errorMsg, long costMs) {
        AgentExecutionSink sink = AiInfra.agentExecutions();
        if (sink == null) {
            return;
        }
        String outputJson = toJson(output);
        String stateJson = toJson(merge(state.data(), output));
        sink.record(new AgentExecution(AgentExecution.Kind.NODE, state.getTaskId(), state.getSessionId(), nodeName,
                null, phase, inputJson, outputJson, stateJson, errorMsg, costMs));
    }

    /** 执行后 state = 入参 merge 出参（ERROR 时 output=null → 等于入参） */
    private static Map<String, Object> merge(Map<String, Object> base, Map<String, Object> delta) {
        Map<String, Object> merged = new HashMap<>(base);
        if (delta != null) {
            merged.putAll(delta);
        }
        return merged;
    }

    /**
     * 向用户输出进度通知（带专家身份前缀）
     *
     * @param state 当前工作流状态（用于获取 botResponse consumer）
     * @param msg   进度消息文本
     */
    protected void notifyProgress(WorkflowState state, String msg) {
        state.outputBotResponse("【" + stepRole().expert() + "】 " + msg, ChatEnum.ROBOT_PROGRESS);
    }

    /**
     * 向用户输出关键结果摘要（带图标前缀 + 换行）
     *
     * @param state 当前工作流状态
     * @param icon  表情图标（如 "✅"、"❌"、"⚠️"）
     * @param msg   结果消息文本
     */
    protected void notifyResult(WorkflowState state, String icon, String msg) {
        state.outputBotResponse(icon + " " + msg + "\n", ChatEnum.ROBOT_RESULT);
    }

    /**
     * 启用工具调用进度透出（ThreadLocal 回调）。
     * <p>
     * 在调用 Agent 前调用，工具每次执行完成后自动向用户输出进度摘要，
     * 格式为 "{@code 【专家名】 描述}"，如 "【规划专家】 读取 UserService.java"。
     * Agent 调用完成后务必在 finally 块中调用 {@link #disableToolProgress()} 清理。
     */
    protected void enableToolProgress(WorkflowState state) {
        ToolInvocationLogger.setProgressCallback(
                (summary, toolName) -> notifyProgress(state, summary));
        ToolInvocationLogger.setExecContext(state.getTaskId(), state.getSessionId(), getClass().getSimpleName());
    }

    /**
     * 禁用工具调用进度透出，清理 ThreadLocal 回调与执行上下文。
     */
    protected void disableToolProgress() {
        ToolInvocationLogger.clearProgressCallback();
        ToolInvocationLogger.clearExecContext();
    }

    /**
     * 当前节点的步骤角色（决定进度指示的专家身份），子类必须覆写。
     */
    protected abstract StepRole stepRole();

    /**
     * 生成会话 ID，供下游 Agent 在输出中关联（UUID 前8位）
     */
    protected String sessionId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * 通知用户当前 Agent 正在调用大模型（LLM 推理阶段，尚未调用工具）。
     *
     * @param state 当前工作流状态
     */
    protected void notifyModelCalling(WorkflowState state) {
        notifyProgress(state, "调用大模型...");
    }

    /**
     * 一次 Agent 调用，request 为本次调用的指令文本（首调/重试的差异仅在指令上）
     *
     * @param <T> Agent 返回结果类型（String 或强类型结果对象）
     */
    @FunctionalInterface
    protected interface AgentCall<T> {
        T invoke(String request) throws Exception;
    }

    // ===== 用户可见的进度输出（第一层：进度通知）=====

    /**
     * 节点执行上下文快照（消除各节点开头重复的 state 读取样板）
     * <p>
     * workflowMode 在入口节点（USER_FACE/ROUTER）阶段尚未写入，允许为 null；
     * 子工作流内的节点通过 {@link #requireWorkflowMode()} 获取（缺失时快速失败）
     */
    protected record NodeContext(String projectPath, Long taskId, LLMModelEnum modelType,
                                 WorkflowMode workflowMode, String projectType) {

        public WorkflowMode requireWorkflowMode() {
            if (workflowMode == null) {
                throw new IllegalArgumentException("WorkflowMode 未指定");
            }
            return workflowMode;
        }
    }

}