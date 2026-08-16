package athena.coder.ai.tool;

import athena.coder.ai.tool.base.ToolInvocationLogger;
import athena.coder.ai.tool.config.AgentToolPolicy;
import athena.coder.ai.tool.config.ToolCategory;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.service.tool.DefaultToolExecutor;
import dev.langchain4j.service.tool.ToolExecutor;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

/**
 * 工具注册中心
 * <p>
 * 统一管理所有注册到 AI 智能体的工具实例，支持动态注册和按 Agent 类别查询。
 * 通过 {@link ToolCategory} 实现工具分类，通过 {@link AgentToolPolicy} 实现 Agent 工具权限控制。
 * <p>
 * 安全机制（两层过滤）：
 * <ol>
 *   <li>类级别过滤：工具注册时声明类别，Agent 按类别获取工具</li>
 *   <li>方法级别覆盖：对包含读写混合方法的工具类，通过 {@link #METHOD_CATEGORY_OVERRIDES} 精确控制
 *       每个方法的类别，防止写方法泄露给只读 Agent</li>
 * </ol>
 */
public class ToolRegistry {

    private static final Logger LOG = Logger.getLogger(ToolRegistry.class.getName());
    /**
     * 方法级别类别覆盖映射
     * <p>
     * 用于精确控制混合读写工具类中每个方法的类别。
     * 键为方法名，值为该方法所属的类别集合。
     * 未在此映射中的方法，继承其所属类的类别。
     */
    private static final Map<String, Set<ToolCategory>> METHOD_CATEGORY_OVERRIDES = Map.of(
            // FileOperationTool: 写方法 → FILE_WRITE
            "writeFile", Set.of(ToolCategory.FILE_WRITE),
            "appendToFile", Set.of(ToolCategory.FILE_WRITE),
            "deleteFile", Set.of(ToolCategory.FILE_WRITE),
            // GitTool: 写方法 → GIT_WRITE
            "gitAdd", Set.of(ToolCategory.GIT_WRITE),
            "gitCommit", Set.of(ToolCategory.GIT_WRITE),
            // DependencyManagerTool: 写方法 → DEPENDENCY
            "addDependency", Set.of(ToolCategory.DEPENDENCY),
            "upgrade", Set.of(ToolCategory.DEPENDENCY),
            // APITestClientTool: 网络请求 → NETWORK，编码工具 → READ_ONLY
            "sendRequest", Set.of(ToolCategory.NETWORK),
            "base64Encode", Set.of(ToolCategory.READ_ONLY),
            "base64Decode", Set.of(ToolCategory.READ_ONLY)
    );
    private final Map<Class<?>, Method[]> methodCache = new ConcurrentHashMap<>();
    private final List<Object> tools = new CopyOnWriteArrayList<>();
    private final Map<Class<?>, Set<ToolCategory>> toolCategories = new ConcurrentHashMap<>();

    private ToolRegistry() {
        registerDefaults();
    }

    private static ToolRegistry getInstance() {
        return Holder.INSTANCE;
    }

    public static Map<ToolSpecification, ToolExecutor> getToolsForAgent(Class<?> agentClass) {
        AgentToolPolicy policy = AgentToolPolicy.fromAgentClass(agentClass);
        return getInstance().getToolMapByCategory(policy.categories());
    }

    public static void shutdownAll() {
        getInstance().shutdown();
    }

    private void registerDefaults() {
        registerTool(new BasicTerminalTool(), ToolCategory.TERMINAL);
        registerTool(new FileOperationTool(), ToolCategory.READ_ONLY, ToolCategory.FILE_WRITE);
        registerTool(new CodeSearchTool(), ToolCategory.READ_ONLY);
        registerTool(new GitTool(), ToolCategory.GIT_READ, ToolCategory.GIT_WRITE);
        registerTool(new TestExecutionTool(), ToolCategory.TEST);
        registerTool(new ProjectAnalysisTool(), ToolCategory.READ_ONLY);
        registerTool(new DiagnosticTool(), ToolCategory.DIAGNOSTIC);
        registerTool(new LogAnalysisTool(), ToolCategory.READ_ONLY);
        registerTool(new SecurityScannerTool(), ToolCategory.SECURITY_SCAN);
        registerTool(new DependencyManagerTool(), ToolCategory.READ_ONLY, ToolCategory.DIAGNOSTIC, ToolCategory.DEPENDENCY);
        registerTool(new APITestClientTool(), ToolCategory.READ_ONLY, ToolCategory.NETWORK);

//        LOG.info("工具注册中心初始化完成，已注册 " + tools.size() + " 个工具");
    }

    // ==================== 注册 ====================

    public void registerTool(Object tool, ToolCategory... categories) {
        if (tool == null) {
            return;
        }
        tools.add(tool);
        if (categories.length > 0) {
            toolCategories.put(tool.getClass(), EnumSet.copyOf(Arrays.asList(categories)));
        }
//        LOG.info("已注册工具: " + tool.getClass().getSimpleName()
//                + "，类别: " + Arrays.toString(categories));
    }

    private Map<ToolSpecification, ToolExecutor> buildToolMapWithFilter(List<Object> toolList, Set<ToolCategory> allowedCategories) {
        Map<ToolSpecification, ToolExecutor> map = new LinkedHashMap<>();

        for (Object tool : toolList) {
            Class<?> clazz = tool.getClass();
            Method[] methods = getCachedMethods(clazz);

            for (Method method : methods) {
                if (method.isAnnotationPresent(Tool.class)) {
                    if (allowedCategories != null && !isMethodAllowed(method, clazz, allowedCategories)) {
                        continue;
                    }

                    ToolSpecification spec = ToolSpecifications.toolSpecificationFrom(method);
                    DefaultToolExecutor executor = new DefaultToolExecutor(tool, method);
                    String toolName = clazz.getSimpleName() + "." + method.getName();
                    ToolInvocationLogger logger = new ToolInvocationLogger(executor, toolName);
                    map.put(spec, logger);
                }
            }
        }

        return map;
    }

    // ==================== 构建工具Map ====================

    /**
     * 判断方法是否在允许的类别范围内
     * <p>
     * 优先检查方法级覆盖（METHOD_CATEGORY_OVERRIDES），
     * 未覆盖则回退到类级别检查。
     */
    private boolean isMethodAllowed(Method method, Class<?> clazz, Set<ToolCategory> allowedCategories) {
        Set<ToolCategory> methodCategories = METHOD_CATEGORY_OVERRIDES.get(method.getName());
        if (methodCategories != null) {
            return methodCategories.stream().anyMatch(allowedCategories::contains);
        }
        return isClassInCategories(clazz, allowedCategories);
    }

    private Method[] getCachedMethods(Class<?> clazz) {
        return methodCache.computeIfAbsent(clazz, Class::getDeclaredMethods);
    }

    private Map<ToolSpecification, ToolExecutor> getToolMapByCategory(Set<ToolCategory> allowedCategories) {
        LOG.fine("按类别过滤工具，允许类别: " + allowedCategories);
        return buildToolMapWithFilter(tools, allowedCategories);
    }

    // ==================== 类别过滤 ====================

    private boolean isClassInCategories(Class<?> toolClass, Set<ToolCategory> allowedCategories) {
        Set<ToolCategory> categories = toolCategories.get(toolClass);
        if (categories == null) {
            return false;
        }
        return !Collections.disjoint(categories, allowedCategories);
    }

    private void shutdown() {
        LOG.info("所有工具资源已关闭");
        methodCache.clear();
    }

    // ==================== 生命周期 ====================

    private static class Holder {
        static final ToolRegistry INSTANCE = new ToolRegistry();
    }
}