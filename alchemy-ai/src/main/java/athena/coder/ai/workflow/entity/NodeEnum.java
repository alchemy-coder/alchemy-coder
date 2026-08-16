package athena.coder.ai.workflow.entity;

public enum NodeEnum {

    USER_FACE,
    // 0. 入口调度
    ROUTER,             // 路由调度：意图识别、工作流模板选择

    // 1. 核心主干
    PLANNER,            // 规划师：需求拆解、生成执行计划
    PLAN_CONFIRM,       // 规划人工确认门：确认放行，拒绝回 PLANNER 重新规划
    CODER,              // 编码器：编写/修改代码
    TESTER,             // 测试员：执行单测、收集结果

    // 2. 质量与修复
    DEBUGGER,           // 调试员：分析报错、制定修复策略（不改代码）
    REVIEWER,           // 审查员：代码规范、安全、需求对齐

    // 3. 收尾与异常
    SUMMARIZER,

    // 4. 子工作流（主图节点，确认后按 WORKFLOW_MODE 分流；枚举名与 WorkflowMode 同名，信号零映射）
    CODE_WORKFLOW,      // 编码工作流
    DEBUG_WORKFLOW,     // 缺陷修复工作流
    WORD_WORKFLOW,      // 文档工作流
    TEST_WORKFLOW;      // 测试补全工作流

}
