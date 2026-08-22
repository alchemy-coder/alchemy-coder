package athena.coder.ai.workflow.entity;

import athena.coder.ai.assistant.agent.result.router.WorkflowMode;
import athena.coder.entity.chat.ChatEnum;
import athena.coder.entity.model.LLMModelEnum;
import org.bsc.langgraph4j.state.AgentState;

import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;

public class WorkflowState extends AgentState {


    //下一个节点
    public final static String NEXT_NODE = "nextNode";


    public final static String WORKFLOW_MODE = "workflowMode";

    /**
     * UserFaceAssistant 提炼的用户意图摘要，ROUTE 模式下注入 RouterAgent/PlannerAgent 上下文
     */
    public final static String ROUTE_CONTEXT = "routeContext";

    /**
     * 原始需求/计划快照（由写角色节点落库，供审查/报告节点对齐需求）
     */
    public final static String ORIGINAL_REQUIREMENT = "originalRequirement";

    /**
     * 规划蓝图
     */
    public final static String PLAN = "PLAN";

    /**
     * 规划阶段产出的项目探索事实（JSON 字符串），单一写者 PLANNER，下游节点只读。
     * 缺失/为空时下游降级为自行探索，不阻断流程。
     */
    public final static String PROJECT_FACTS = "projectFacts";

    /**
     * CODER 变更的文件列表（逗号分隔）
     */
    public final static String CHANGED_FILES = "changedFiles";

    /**
     * git commit range，格式 "beforeCommit..afterCommit"，下游通过 git diff 查看变更
     */
    public final static String CHANGED_DIFF_REF = "changedDiffRef";

    /**
     * 验收标准摘要（精简版），来自 PLANNER 的执行计划提取，供 TESTER 验证测试覆盖度
     * 格式：每个任务的关键验收点列表，如：
     * - 任务1: 用户注册成功返回非空 userId
     * - 任务2: 邮箱格式无效时抛 InvalidEmailException
     */
    public final static String ACCEPTANCE_CRITERIA = "acceptanceCriteria";

    /**
     * DEBUGGER 输出的修复策略，供 CODER 在 Bug 修复流程中使用
     */
    public final static String FIX_STRATEGY = "fixStrategy";

    /**
     * TESTER 输出的测试结果（JSON格式），包含 status/summary/coverage/failures 等
     */
    public final static String TEST_RESULT = "testResult";

    /**
     * REVIEWER 输出的审查报告（JSON格式），包含 verdict/issues/stageResults/improvements 等
     */
    public final static String REVIEW_RESULT = "reviewResult";

    /**
     * SUMMARIZER 输出的总结报告（JSON格式），包含 report/commitMessage/branchSuggestion/metadata 等
     */
    public final static String SUMMARIZE_RESULT = "summarizeResult";

    /**
     * 历史修复记录列表，用于 DEBUGGER 防止重复失败策略
     * 格式：JSON数组，每条记录是一次修复策略
     * 示例：[{"targetFile":"A.java","actionType":"MODIFY",...}, ...]
     */
    public final static String PREVIOUS_FIXES = "previousFixes";

    /**
     * DEBUGGER→CODER 修复回环计数，超限熔断强制走 SUMMARIZER 收尾
     */
    public final static String DEBUG_LOOP_COUNT = "debugLoopCount";

    /**
     * REVIEWER→CODER 审查打回回环计数，超限熔断强制走 SUMMARIZER 收尾
     */
    public final static String REVIEW_LOOP_COUNT = "reviewLoopCount";

    /**
     * 用户拒绝规划时的修改意见，由 PLAN_CONFIRM 写入，PLANNER 重新规划时拼入 prompt
     */
    public final static String PLAN_FEEDBACK = "planFeedback";

    /**
     * PLAN_CONFIRM 拒绝重规划回环计数，超限熔断强制走 END
     */
    public final static String PLAN_CONFIRM_COUNT = "planConfirmCount";

    /**
     * 规划已经用户确认的标记，供下游节点使用
     */
    // CONFIRMED 已删除：全工程无读取方，属死 key

    // ===== 初始化必填字段 key（调用方构建 initialState 时使用，见 MasterWorkflow#start）=====

    public final static String INIT_TASK_ID = "TASK_ID";
    public final static String INIT_WORK_FULL_PATH = "WORK_FULL_PATH";
    public final static String INIT_USER_MESSAGE = "USER_MESSAGE";
    public final static String INIT_MODEL_TYPE = "MODEL_TYPE";
    public final static String INIT_BOT_RESPONSE = "BOT_RESPONSE";
    /**
     * 会话 uuid（一次用户消息），贯穿节点/工具执行轨迹，用于按会话回溯；可空
     */
    public final static String INIT_SESSION_ID = "SESSION_ID";


    /**
     * 任务Id
     */
    private final Long taskId;
    /**
     * 工作路径
     */
    private final String workFullPath;

    /**
     * 用户当前输入
     */
    private final String userMessage;

    /**
     * 会话 uuid（一次用户消息），执行轨迹回溯用；可能为空
     */
    private final String sessionId;


    private final LLMModelEnum modelType;


    private transient final BiConsumer<String, ChatEnum> botResponse;

    public WorkflowState(Map<String, Object> initData) {
        super(initData);
        this.taskId = Objects.requireNonNull((Long) initData.get(INIT_TASK_ID), "TASK_ID must not be null");
        this.workFullPath = Objects.requireNonNull((String) initData.get(INIT_WORK_FULL_PATH), "WORK_FULL_PATH must not be null");
        this.userMessage = Objects.requireNonNull((String) initData.get(INIT_USER_MESSAGE), "USER_MESSAGE must not be null");
        this.sessionId = (String) initData.get(INIT_SESSION_ID);
        this.modelType = Objects.requireNonNull((LLMModelEnum) initData.get(INIT_MODEL_TYPE), "MODEL_TYPE must not be null");
        this.botResponse = Objects.requireNonNull((BiConsumer<String, ChatEnum>) initData.get(INIT_BOT_RESPONSE), "BOT_RESPONSE must not be null");
    }

    /**
     * 输出指定类型的消息到聊天界面
     */
    public void outputBotResponse(String msg, ChatEnum type) {
        botResponse.accept(msg, type);
    }


    public String getUserMessage() {
        return userMessage;
    }

    /**
     * 会话 uuid（一次用户消息），执行轨迹回溯用；可能为空
     */
    public String getSessionId() {
        return sessionId;
    }


    public Long getTaskId() {
        return taskId;
    }


    public String getWorkFullPath() {
        return workFullPath;
    }


    public LLMModelEnum getModelType() {
        return modelType;
    }

    // ===== 强类型状态访问器（收敛各节点散落的 getStringValue/getIntValue 调用面）=====

    /** 规划蓝图（PLAN），未设置时返回 null */
    public String getPlan() {
        return getStringValue(PLAN);
    }

    /** 原始需求/计划快照（写角色节点落库，供审查/报告对齐需求），未设置时返回 null */
    public String getOriginalRequirement() {
        return getStringValue(ORIGINAL_REQUIREMENT);
    }

    /** 规划阶段产出的项目探索事实 JSON（单一写者 PLANNER，下游只读），未设置时返回 null */
    public String getProjectFacts() {
        return getStringValue(PROJECT_FACTS);
    }

    /** CODER 变更的文件列表（逗号分隔），未设置时返回 null */
    public String getChangedFiles() {
        return getStringValue(CHANGED_FILES);
    }

    /** git commit range，未设置时返回 null */
    public String getChangedDiffRef() {
        return getStringValue(CHANGED_DIFF_REF);
    }

    /** 验收标准摘要，未设置时返回 null */
    public String getAcceptanceCriteria() {
        return getStringValue(ACCEPTANCE_CRITERIA);
    }

    /** DEBUGGER 输出的修复策略 JSON，未设置时返回 null */
    public String getFixStrategy() {
        return getStringValue(FIX_STRATEGY);
    }

    /** TESTER 输出的测试结果 JSON，未设置时返回 null */
    public String getTestResult() {
        return getStringValue(TEST_RESULT);
    }

    /** REVIEWER 输出的审查报告 JSON，未设置时返回 null */
    public String getReviewResult() {
        return getStringValue(REVIEW_RESULT);
    }

    /** SUMMARIZER 输出的总结报告 JSON，未设置时返回 null */
    public String getSummarizeResult() {
        return getStringValue(SUMMARIZE_RESULT);
    }

    /** 历史修复记录 JSON 数组，未设置时返回 null */
    public String getPreviousFixes() {
        return getStringValue(PREVIOUS_FIXES);
    }

    /** 用户拒绝规划时的修改意见 JSON，未设置时返回 null */
    public String getPlanFeedback() {
        return getStringValue(PLAN_FEEDBACK);
    }

    /** 用户意图摘要（ROUTE_CONTEXT），未设置时返回 null */
    public String getRouteContext() {
        return getStringValue(ROUTE_CONTEXT);
    }

    /** 下一个节点路由信号（枚举名或 END），未设置时返回 null */
    public String getNextNode() {
        return getStringValue(NEXT_NODE);
    }

    /** 路由选择的工作流模式，未设置时返回 null */
    public WorkflowMode getWorkflowMode() {
        Object value = this.data().get(WORKFLOW_MODE);
        return value instanceof WorkflowMode mode ? mode : null;
    }

    /** DEBUGGER→CODER 修复回环计数，未设置时返回 0 */
    public int getDebugLoopCount() {
        return getIntValue(DEBUG_LOOP_COUNT);
    }

    /** REVIEWER→CODER 审查打回回环计数，未设置时返回 0 */
    public int getReviewLoopCount() {
        return getIntValue(REVIEW_LOOP_COUNT);
    }

    /** PLAN_CONFIRM 拒绝重规划回环计数，未设置时返回 0 */
    public int getPlanConfirmCount() {
        return getIntValue(PLAN_CONFIRM_COUNT);
    }

    /**
     * 读取字符串型状态值，未设置时返回 null（内部实现，外部请用上述强类型访问器）
     */
    private String getStringValue(String key) {
        Object value = this.data().get(key);
        return value != null ? String.valueOf(value) : null;
    }

    /**
     * 读取整型计数器（如回环熔断计数），未设置时返回 0（内部实现）
     */
    private int getIntValue(String key) {
        Object value = this.data().get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String str) {
            try {
                return Integer.parseInt(str.trim());
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    /**
     * 读取 routeContext（意图摘要）注入用户消息，提升下游 Agent 语义理解精度。
     * routeContext 不存在或为空时降级为原始用户消息，不阻断流程。
     */
    public String buildRoutedMessage() {
        String routeContext = getRouteContext();
        if (routeContext == null || routeContext.isBlank()) {
            return userMessage;
        }
        return "意图摘要: " + routeContext + "\n\n用户原始消息: " + userMessage;
    }

}