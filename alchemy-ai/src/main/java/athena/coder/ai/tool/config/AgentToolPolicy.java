package athena.coder.ai.tool.config;

import java.util.Set;

/**
 * Agent 工具策略配置（按「角色」而非「类名」键控）
 * <p>
 * 工具权限是「角色 × 工作流」的属性，不是 Agent 类的属性——同一个 {@code GenericWriterAgent}
 * 在编码/文档场景拿到的工具集不同。因此本枚举只声明角色，权限由 Node 在构造时显式传入
 * {@code newChatAssistant(model, agentClass, AgentToolPolicy.XXX)}，不再按类名查表。
 * <p>
 * 设计原则：
 * - 最小权限原则：每个角色只获得完成职责所需的最少工具
 * - 方法级精确控制：通过 ToolRegistry.METHOD_CATEGORY_OVERRIDES 防止写方法泄露
 * - 只读角色真正只读：writeFile/gitCommit/addDependency 等写方法不会出现在只读角色的工具列表中
 * <p>
 * 工具权限矩阵（角色 → 权限特点）：
 * <pre>
 * | 角色           | 权限特点                            | 使用方                      |
 * |---------------|-------------------------------------|-----------------------------|
 * | ROUTER        | 无工具（纯 LLM 推理）                | RouterNode                  |
 * | CONFIRM_INTENT| 无工具（纯 LLM 推理）                | PlanConfirmNode             |
 * | USER_FACE     | 只读 + 终端 + git 只读              | UserFaceNode                |
 * | PLANNER       | 只读                                | PlanNode                    |
 * | CODE_WRITER   | 全量写（含安全扫描）                | 编码工作流写角色            |
 * | WRITER        | 全量写（无安全扫描）                | 修复/补测工作流写角色       |
 * | DOC_WRITER    | 仅文件写 + git 写，无测试/诊断/依赖 | 文档工作流写角色            |
 * | TESTER        | 测试 + 诊断 + 网络（只读验证）      | 所有工作流测试执行角色      |
 * | ANALYST       | 只读 + 诊断                          | 所有工作流根因分析角色      |
 * | CODE_REVIEWER | 只读 + 诊断 + 安全扫描               | 编码工作流审查角色          |
 * | TEST_REVIEWER | 只读 + 诊断                          | 补测工作流审查角色          |
 * | DOC_REVIEWER  | 只读                                | 文档工作流审查角色          |
 * | REPORTER      | 只读                                | 所有工作流收尾报告角色      |
 * </pre>
 * <p>
 * 方法级精确控制（METHOD_CATEGORY_OVERRIDES）：
 * <pre>
 * | 方法名          | 所属类别       | 只有声明该类别才能调用               |
 * |----------------|---------------|-----------------------------------|
 * | writeFile      | FILE_WRITE    | 仅写角色                           |
 * | appendToFile   | FILE_WRITE    | 仅写角色                           |
 * | deleteFile     | FILE_WRITE    | 仅写角色                           |
 * | gitAdd         | GIT_WRITE     | 仅声明 GIT_WRITE 的写角色          |
 * | gitCommit      | GIT_WRITE     | 仅声明 GIT_WRITE 的写角色          |
 * | addDependency  | DEPENDENCY    | 仅声明 DEPENDENCY 的角色           |
 * | upgrade        | DEPENDENCY    | 仅声明 DEPENDENCY 的角色           |
 * | sendRequest    | NETWORK       | 仅声明 NETWORK 的角色              |
 * | base64Encode   | READ_ONLY     | 所有声明 READ_ONLY 的角色          |
 * | base64Decode   | READ_ONLY     | 所有声明 READ_ONLY 的角色          |
 * </pre>
 */
public enum AgentToolPolicy {

    ROUTER,

    CONFIRM_INTENT,

    USER_FACE(
            ToolCategory.READ_ONLY,
            ToolCategory.TERMINAL,
            ToolCategory.GIT_READ),

    PLANNER(
            ToolCategory.READ_ONLY),

    // ===== 写角色 =====

    CODE_WRITER(
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

    WRITER(
            ToolCategory.READ_ONLY,
            ToolCategory.FILE_WRITE,
            ToolCategory.TERMINAL,
            ToolCategory.GIT_READ,
            ToolCategory.GIT_WRITE,
            ToolCategory.TEST,
            ToolCategory.DIAGNOSTIC,
            ToolCategory.DEPENDENCY,
            ToolCategory.NETWORK),

    DOC_WRITER(
            ToolCategory.READ_ONLY,
            ToolCategory.FILE_WRITE,
            ToolCategory.GIT_READ,
            ToolCategory.GIT_WRITE),

    // ===== 测试 / 分析角色 =====

    TESTER(
            ToolCategory.READ_ONLY,
            ToolCategory.TEST,
            ToolCategory.DIAGNOSTIC,
            ToolCategory.GIT_READ,
            ToolCategory.NETWORK),

    ANALYST(
            ToolCategory.READ_ONLY,
            ToolCategory.DIAGNOSTIC,
            ToolCategory.GIT_READ),

    // ===== 审查角色 =====

    CODE_REVIEWER(
            ToolCategory.READ_ONLY,
            ToolCategory.DIAGNOSTIC,
            ToolCategory.SECURITY_SCAN,
            ToolCategory.GIT_READ),

    TEST_REVIEWER(
            ToolCategory.READ_ONLY,
            ToolCategory.DIAGNOSTIC,
            ToolCategory.GIT_READ),

    DOC_REVIEWER(
            ToolCategory.READ_ONLY,
            ToolCategory.GIT_READ),

    // ===== 收尾角色 =====

    REPORTER(
            ToolCategory.GIT_READ,
            ToolCategory.READ_ONLY);

    private final Set<ToolCategory> categories;

    AgentToolPolicy(ToolCategory... categories) {
        this.categories = Set.of(categories);
    }

    public Set<ToolCategory> categories() {
        return categories;
    }
}
