package athena.coder.ai.tool.config;

/**
 * 工具类别枚举
 * <p>
 * 每个工具注册时声明自己属于哪些类别，
 * Agent 通过类别组合来声明自己需要的工具集，
 * 实现声明式的工具权限控制。
 * <p>
 * 类别设计原则：
 * - 语义内聚：同一类别的工具具有相同的操作语义
 * - 最小权限：Agent 只获得完成职责所需的最少类别
 * - 方法级覆盖：对混合读写工具类，通过 ToolRegistry.METHOD_CATEGORY_OVERRIDES 精确控制
 */
public enum ToolCategory {

    /** 只读工具：读文件、搜索代码、分析项目结构、分析日志 */
    READ_ONLY,

    /** 写文件工具：创建/修改/删除文件 */
    FILE_WRITE,

    /** 终端命令：执行shell命令、获取系统信息 */
    TERMINAL,

    /** Git只读：log、diff、show、status、blame */
    GIT_READ,

    /** Git写操作：add、commit */
    GIT_WRITE,

    /** 测试执行：运行测试套件、覆盖率报告 */
    TEST,

    /** 诊断工具：编译诊断、静态代码分析、错误日志提取 */
    DIAGNOSTIC,

    /** 安全扫描：OWASP漏洞检测、特定模式安全检查 */
    SECURITY_SCAN,

    /** 依赖管理：添加/升级/列出/审计项目依赖 */
    DEPENDENCY,

    /** 网络请求：发送HTTP请求、API测试 */
    NETWORK
}