package athena.coder.ai.tool.config;

import java.util.Set;

/**
 * Agent 工具策略配置
 * <p>
 * 集中定义每个 Agent 可以使用的工具类别。
 * 新增 Agent 时只需在此枚举中添加一个条目即可，
 * 无需修改 ToolRegistry 或各个 Agent 类。
 * <p>
 * 设计原则：
 * - 最小权限原则：每个 Agent 只获得完成职责所需的最少工具
 * - 方法级精确控制：通过 ToolRegistry.METHOD_CATEGORY_OVERRIDES 防止写方法泄露
 * - 只读Agent 真正只读：writeFile/gitCommit/addDependency 等写方法不会出现在只读Agent的工具列表中
 * <p>
 * 工具权限矩阵（子流程 Agent 专属化后按流程分组）：
 * <pre>
 * | 角色类型       | Agent                                                | 权限特点                       |
 * |---------------|------------------------------------------------------|--------------------------------|
 * | 写入类（4个）  | CodeWriter/FixApply/TestWrite                        | 全量写权限（FILE_WRITE等）      |
 * |               | DocWrite                                             | 仅文件写，无测试/诊断/依赖权限  |
 * | 测试执行类(3) | CodeTest/FixVerify/TestRun                           | TEST+DIAGNOSTIC+NETWORK 只读验证 |
 * | 分析类（3个）  | CodeFixAnalyst/FixAnalyze/TestFixAnalyst             | 只读+诊断                       |
 * | 审查类（3个）  | CodeReview(含SECURITY_SCAN)/DocReview/TestReview     | 只读，CodeReview额外安全扫描    |
 * | 报告类（4个）  | CodeReport/FixReport/DocReport/TestReport            | 只读                            |
 * </pre>
 * <p>
 * 补充说明：
 * <ul>
 *   <li>DependencyManagerTool 的 listDependencies/securityAudit 方法继承 READ_ONLY 类别，
 *       对声明 READ_ONLY 的所有 Agent 可见</li>
 *   <li>APITestClientTool 的 base64Encode/base64Decode 方法归入 READ_ONLY，所有只读 Agent 可调用</li>
 *   <li>SecurityScannerTool 归入 SECURITY_SCAN，仅 CodeWriterAgent 与 CodeReviewAgent 可用</li>
 * </ul>
 * <p>
 * 方法级精确控制（METHOD_CATEGORY_OVERRIDES）：
 * <pre>
 * | 方法名          | 所属类别       | 只有声明该类别才能调用               |
 * |----------------|---------------|-----------------------------------|
 * | writeFile      | FILE_WRITE    | 仅写入类 Agent                     |
 * | appendToFile   | FILE_WRITE    | 仅写入类 Agent                     |
 * | deleteFile     | FILE_WRITE    | 仅写入类 Agent                     |
 * | gitAdd         | GIT_WRITE     | 仅声明 GIT_WRITE 的写入类 Agent    |
 * | gitCommit      | GIT_WRITE     | 仅声明 GIT_WRITE 的写入类 Agent    |
 * | addDependency  | DEPENDENCY    | 仅声明 DEPENDENCY 的 Agent         |
 * | upgrade        | DEPENDENCY    | 仅声明 DEPENDENCY 的 Agent         |
 * | sendRequest    | NETWORK       | 仅声明 NETWORK 的 Agent            |
 * | base64Encode   | READ_ONLY     | 所有声明 READ_ONLY 的 Agent       |
 * | base64Decode   | READ_ONLY     | 所有声明 READ_ONLY 的 Agent       |
 * </pre>
 */
public enum AgentToolPolicy {

    ROUTER("RouterAgent"),

    CONFIRM_INTENT("ConfirmIntentAgent"),

    USER_FACE("UserFaceAssistant",
            ToolCategory.READ_ONLY,
            ToolCategory.TERMINAL,
            ToolCategory.GIT_READ),

    PLANNER("PlannerAgent",
            ToolCategory.READ_ONLY),

    // ===== CODE_WORKFLOW 专属 Agent =====

    CODE_WRITER("CodeWriterAgent",
            ToolCategory.READ_ONLY,
            ToolCategory.FILE_WRITE,
            ToolCategory.TERMINAL,
            ToolCategory.GIT_READ,
            ToolCategory.GIT_WRITE,
            ToolCategory.TEST,
            ToolCategory.DIAGNOSTIC,
            ToolCategory.SECURITY_SCAN,
            ToolCategory.DEPENDENCY,
            ToolCategory.NETWORK),

    CODE_TEST("CodeTestAgent",
            ToolCategory.READ_ONLY,
            ToolCategory.TEST,
            ToolCategory.DIAGNOSTIC,
            ToolCategory.GIT_READ,
            ToolCategory.NETWORK),

    CODE_FIX_ANALYST("CodeFixAnalystAgent",
            ToolCategory.READ_ONLY,
            ToolCategory.DIAGNOSTIC,
            ToolCategory.GIT_READ),

    CODE_REVIEW("CodeReviewAgent",
            ToolCategory.READ_ONLY,
            ToolCategory.DIAGNOSTIC,
            ToolCategory.SECURITY_SCAN,
            ToolCategory.GIT_READ),

    CODE_REPORT("CodeReportAgent",
            ToolCategory.GIT_READ,
            ToolCategory.READ_ONLY),

    // ===== DEBUG_WORKFLOW 专属 Agent =====

    FIX_APPLY("FixApplyAgent",
            ToolCategory.READ_ONLY,
            ToolCategory.FILE_WRITE,
            ToolCategory.TERMINAL,
            ToolCategory.GIT_READ,
            ToolCategory.GIT_WRITE,
            ToolCategory.TEST,
            ToolCategory.DIAGNOSTIC,
            ToolCategory.DEPENDENCY,
            ToolCategory.NETWORK),

    FIX_VERIFY("FixVerifyAgent",
            ToolCategory.READ_ONLY,
            ToolCategory.TEST,
            ToolCategory.DIAGNOSTIC,
            ToolCategory.GIT_READ,
            ToolCategory.NETWORK),

    FIX_ANALYZE("FixAnalyzeAgent",
            ToolCategory.READ_ONLY,
            ToolCategory.DIAGNOSTIC,
            ToolCategory.GIT_READ),

    FIX_REPORT("FixReportAgent",
            ToolCategory.GIT_READ,
            ToolCategory.READ_ONLY),

    // ===== WORD_WORKFLOW 专属 Agent =====

    DOC_WRITE("DocWriteAgent",
            ToolCategory.READ_ONLY,
            ToolCategory.FILE_WRITE,
            ToolCategory.GIT_READ,
            ToolCategory.GIT_WRITE),

    DOC_REVIEW("DocReviewAgent",
            ToolCategory.READ_ONLY,
            ToolCategory.GIT_READ),

    DOC_REPORT("DocReportAgent",
            ToolCategory.GIT_READ,
            ToolCategory.READ_ONLY),

    // ===== TEST_WORKFLOW 专属 Agent =====

    TEST_WRITE("TestWriteAgent",
            ToolCategory.READ_ONLY,
            ToolCategory.FILE_WRITE,
            ToolCategory.TERMINAL,
            ToolCategory.GIT_READ,
            ToolCategory.GIT_WRITE,
            ToolCategory.TEST,
            ToolCategory.DIAGNOSTIC,
            ToolCategory.DEPENDENCY,
            ToolCategory.NETWORK),

    TEST_RUN("TestRunAgent",
            ToolCategory.READ_ONLY,
            ToolCategory.TEST,
            ToolCategory.DIAGNOSTIC,
            ToolCategory.GIT_READ,
            ToolCategory.NETWORK),

    TEST_FIX_ANALYST("TestFixAnalystAgent",
            ToolCategory.READ_ONLY,
            ToolCategory.DIAGNOSTIC,
            ToolCategory.GIT_READ),

    TEST_REVIEW("TestReviewAgent",
            ToolCategory.READ_ONLY,
            ToolCategory.DIAGNOSTIC,
            ToolCategory.GIT_READ),

    TEST_REPORT("TestReportAgent",
            ToolCategory.GIT_READ,
            ToolCategory.READ_ONLY);

    private final String agentClassName;
    private final Set<ToolCategory> categories;

    AgentToolPolicy(String agentClassName, ToolCategory... categories) {
        this.agentClassName = agentClassName;
        this.categories = Set.of(categories);
    }

    public String agentClassName() {
        return agentClassName;
    }

    public Set<ToolCategory> categories() {
        return categories;
    }

    public static AgentToolPolicy fromAgentClass(Class<?> agentClass) {
        String className = agentClass.getSimpleName();
        for (AgentToolPolicy policy : values()) {
            if (policy.agentClassName.equals(className)) {
                return policy;
            }
        }
        throw new IllegalArgumentException("未知的Agent: " + className);
    }
}