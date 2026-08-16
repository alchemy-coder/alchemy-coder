package athena.coder.ai.workflow.node;

import athena.coder.ai.assistant.agent.result.router.WorkflowMode;
import athena.coder.ai.tool.base.ToolInvocationLogger;
import athena.coder.ai.workflow.entity.WorkflowState;
import athena.coder.ai.spi.ErrorLogger;
import athena.coder.ai.util.ProjectTypeUtil;
import athena.coder.entity.chat.ChatEnum;
import athena.coder.entity.model.ModelEnum;
import athena.coder.exception.RocAgentException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.bsc.langgraph4j.action.NodeAction;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.logging.Logger;

/**
 * Agent 节点模板基类
 * <p>
 * 抽取各 Agent 节点的公共骨架：
 * 1. {@link #apply} 模板方法：state 非空校验 + 基础上下文校验（projectPath/taskId/modelType），
 * 构建 {@link NodeContext} 快照后委托子类 {@link #doApply} 执行差异化业务
 * 2. {@link #callAgentWithRetry}：统一的“首调 → 任何异常带错误信息重试一次 → 兕底/上抛”策略（泛型，支持强类型结果）
 * 3. {@link #requireUpstream}/{@link #warnIfBlank}：上游数据声明式校验
 * 4. {@link #textAt}：Agent 输出 JSON 字段提取（JSON Pointer）
 * 5. {@link #logStart}：统一的节点开始日志（key-value 成对传入，支持 NodeContext 上下文字段自动前缀）
 * 6. {@link #buildChangeSummary}：changedFiles + diffRef 合并为结构化 JSON（Jackson 构建，避免手拼注入）
 * 7. {@link #sessionId}：会话 ID 节点侧生成（提示词不再让 LLM 自制 ID）
 * <p>
 * 节点差异化逻辑（determineNextNode/输出组装）保留在各子类与角色基类中
 */
public abstract class AbstractAgentNode implements NodeAction<WorkflowState> {

    protected static final ObjectMapper MAPPER = new ObjectMapper();
    protected static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;
    private final Logger log = Logger.getLogger(getClass().getName());

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
        log.info("▶ [" + nodeName + "] 节点开始执行，state.nextNode = " + state.data().get(WorkflowState.NEXT_NODE));
        enableToolProgress(state);
        try {
            Map<String, Object> result = doApply(state, buildContext(state));
            long costMs = System.currentTimeMillis() - startMs;
            log.info(String.format("■ [%s] 节点执行完成，耗时 %dms，路由信号→ %s",
                    nodeName, costMs, result.getOrDefault(WorkflowState.NEXT_NODE, "(无信号，走静态边)")));
            return result;
        } catch (Exception e) {
            ErrorLogger.log(nodeName, e, state.getTaskId(), null, null);
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

    // ==================== 子类日志包装方法 ====================

    protected void logInfo(String msg) {
        log.info(msg);
    }

    /**
     * 记录节点开始日志：kv 按 key1, value1, key2, value2... 成对传入
     * <p>
     * 值的展示形式由调用方决定（如传长度、传 truncate 后的文本）
     */
    protected void logStart(String action, Object... kv) {
        StringBuilder sb = new StringBuilder(getClass().getSimpleName()).append(' ').append(action).append(':');
        for (int i = 0; i + 1 < kv.length; i += 2) {
            sb.append("\n  - ").append(kv[i]).append('=').append(kv[i + 1]);
        }
        log.info(sb.toString());
    }

    /**
     * 记录节点开始日志（自动前缀上下文字段 taskId/modelType/workflowMode/projectType），
     * 差异化字段由 kv 追加；workflowMode 尚未写入的入口节点（USER_FACE/ROUTER）自动省略该字段
     */
    protected void logStart(NodeContext ctx, String action, Object... kv) {
        Object[] prefix = ctx.workflowMode() != null
                ? new Object[]{"taskId", ctx.taskId(), "modelType", ctx.modelType(),
                "workflowMode", ctx.workflowMode(), "projectType", ctx.projectType()}
                : new Object[]{"taskId", ctx.taskId(), "modelType", ctx.modelType(),
                "projectType", ctx.projectType()};
        Object[] merged = new Object[prefix.length + kv.length];
        System.arraycopy(prefix, 0, merged, 0, prefix.length);
        System.arraycopy(kv, 0, merged, prefix.length, kv.length);
        logStart(action, merged);
    }

    /**
     * 向用户输出进度通知（带图标前缀 + 换行）
     *
     * @param state 当前工作流状态（用于获取 botResponse consumer）
     * @param icon  表情图标（如 "📋"、"💻"、"🧪"）
     * @param msg   进度消息文本
     */
    protected void notifyProgress(WorkflowState state, String icon, String msg) {
        state.outputBotResponse(icon + " " + msg + "\n", ChatEnum.ROBOT_PROGRESS);
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
     * 在调用 Agent 前调用，工具每次执行完成后会自动向用户输出进度摘要。
     * 摘要格式为 "{stepLabel} {自然语言描述}"，如 "[规划] 读取 UserService.java"，
     * UI 层自动解析为 "【规划专家】 读取 UserService.java"。
     * Agent 调用完成后务必在 finally 块中调用 {@link #disableToolProgress()} 清理。
     */
    protected void enableToolProgress(WorkflowState state) {
        String label = stepLabel();
        ToolInvocationLogger.setProgressCallback(
                (summary, toolName) -> notifyProgress(state, label, summary));
    }

    /**
     * 禁用工具调用进度透出，清理 ThreadLocal 回调。
     */
    protected void disableToolProgress() {
        ToolInvocationLogger.clearProgressCallback();
    }

    /**
     * 当前节点的步骤标签（如 "[规划]"、"[编码]"），用于工具进度消息前缀。
     * 子类必须覆写以返回对应标签。
     */
    protected String stepLabel() {
        return "";
    }

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
        String label = stepLabel();
        notifyProgress(state, label, "调用大模型...");
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
    protected record NodeContext(String projectPath, Long taskId, ModelEnum modelType,
                                 WorkflowMode workflowMode, String projectType) {

        public WorkflowMode requireWorkflowMode() {
            if (workflowMode == null) {
                throw new IllegalArgumentException("WorkflowMode 未指定");
            }
            return workflowMode;
        }
    }

}