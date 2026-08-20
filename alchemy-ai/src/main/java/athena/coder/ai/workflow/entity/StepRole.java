package athena.coder.ai.workflow.entity;

/**
 * 节点步骤角色 —— 进度指示的专家身份。
 * <p>
 * ai 层单一事实来源：节点进度文案由 {@code AbstractAgentNode#notifyProgress} 以
 * {@code 【专家名】 描述} 形式直出，app 层仅做纯展示，不再维护「标签 → 专家名」映射。
 */
public enum StepRole {

    USER("用户专家"),
    ROUTER("路由专家"),
    PLANNER("规划专家"),
    CODER("编码专家"),
    TEST_WRITER("补测专家"),
    DOC_WRITER("文档专家"),
    FIXER("修复专家"),
    TESTER("测试专家"),
    ANALYST("调试专家"),
    REVIEWER("审查专家"),
    REPORTER("报告专家");

    private final String expert;

    StepRole(String expert) {
        this.expert = expert;
    }

    /** 专家名（loading 指示灯文案前缀） */
    public String expert() {
        return expert;
    }
}
