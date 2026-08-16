package athena.coder.ai.tool.base;

import athena.coder.ai.tool.exception.ErrorCode;
import athena.coder.ai.tool.exception.ToolExecutionException;
import athena.coder.ai.spi.ErrorLogger;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.service.tool.ToolExecutor;

import java.util.Map;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 工具调用日志记录器，包装 ToolExecutor，在调用前后打印入参和出参。
 * <p>
 * 同时提供静态 ThreadLocal 进度回调，节点可在调用 Agent 前注册回调，
 * 工具每次执行完成后通过回调向用户输出进度摘要。
 * <p>
 * 摘要生成基于预定义的 {@link ToolSummaryConfig} 语义模板表，
 * 每个工具方法配置对应的动词、关键参数名和兜底描述，
 * 确保任何入参都能产出可读的自然语言摘要（≤20字），杜绝裸出方法名。
 */
public class ToolInvocationLogger implements ToolExecutor {

    private static final Logger LOG = Logger.getLogger(ToolInvocationLogger.class.getName());

    /**
     * 工具执行进度回调（ThreadLocal，节点调用 Agent 前设置，调用后清理）
     * <p>
     * 第一个参数：工具中文摘要（如 "📖 读取 UserService.java"）
     * 第二个参数：工具原始名称（如 "FileOperationTool.readFile"）
     */
    private static final ThreadLocal<BiConsumer<String, String>> progressCallback = new ThreadLocal<>();
    /**
     * 全部 41 个工具方法的语义模板表，逐个精确覆盖，无遗漏。
     */
    private static final Map<String, ToolSummary> SUMMARY_CONFIG = Map.ofEntries(
            // ── FileOperationTool ──
            Map.entry("readFile", t("读取", "filePath", "文件")),
            Map.entry("writeFile", t("编辑", "filePath", "文件")),
            Map.entry("appendToFile", t("追加", "filePath", "文件")),
            Map.entry("deleteFile", t("删除", "filePath", "文件")),
            Map.entry("listDirectory", t("列出", "dirPath", "目录")),

            // ── BasicTerminalTool ──
            Map.entry("getOperatingSystemInfo", t("查看系统信息", null, null)),
            Map.entry("execute", t("运行", "command", "命令")),
            Map.entry("executeWithStatus", t("运行", "command", "命令")),
            Map.entry("which", t("查找命令", "command", null)),

            // ── CodeSearchTool ──
            Map.entry("findFiles", t("搜索文件", "pattern", null)),
            Map.entry("searchContent", t("搜索", "query", "代码")),
            Map.entry("searchCodebase", t("搜索", "query", "代码")),

            // ── GitTool ──
            Map.entry("gitStatus", t("查看状态", null, null)),
            Map.entry("gitLog", t("查看日志", "filePath", "提交日志")),
            Map.entry("gitDiff", t("对比差异", "filePath", "差异")),
            Map.entry("gitBlame", t("查看注释", "filePath", "文件")),
            Map.entry("gitAdd", t("暂存", "files", "文件")),
            Map.entry("gitCommit", t("提交", "message", "代码")),
            Map.entry("gitShow", t("查看提交", "commitHash", "提交")),

            // ── DependencyManagerTool ──
            Map.entry("addDependency", t("安装", "dependency", "依赖")),
            Map.entry("listDependencies", t("列出依赖", null, null)),
            Map.entry("securityAudit", t("安全检查", null, null)),
            Map.entry("upgrade", t("升级", "dependency", "依赖")),

            // ── APITestClientTool ──
            Map.entry("sendRequest", t("请求", "url", "接口")),
            Map.entry("base64Encode", t("Base64编码", "url", null)),
            Map.entry("base64Decode", t("Base64解码", null, null)),

            // ── DiagnosticTool ──
            Map.entry("getCompilationDiagnostics", t("编译诊断", null, null)),
            Map.entry("getRuntimeErrors", t("查看错误日志", "logFile", "日志")),
            Map.entry("analyzeCodeProblems", t("代码检查", "filePath", "文件")),

            // ── LogAnalysisTool ──
            Map.entry("analyzeLog", t("日志分析", "logFilePath", "日志")),
            Map.entry("extractExceptions", t("提取异常", "logFilePath", "日志")),
            Map.entry("searchLogs", t("搜索日志", "pattern", "日志")),

            // ── ProjectAnalysisTool ──
            Map.entry("analyzeProjectStructure", t("分析结构", null, null)),
            Map.entry("readProjectConfig", t("读取配置", "configFile", null)),
            Map.entry("analyzeClassDependencies", t("分析依赖", "className", null)),

            // ── SecurityScannerTool ──
            Map.entry("sastScan", t("安全扫描", "target", null)),
            Map.entry("checkPattern", t("检查漏洞", "target", null)),

            // ── TestExecutionTool ──
            Map.entry("runTests", t("运行测试", "testFilter", "测试")),
            Map.entry("runSingleTest", t("运行测试", "testClass", "测试")),
            Map.entry("getTestCoverage", t("查看覆盖率", null, null))
    );
    private final ToolExecutor delegate;

    // ---- 语义模板 ----
    private final String toolName;

    public ToolInvocationLogger(ToolExecutor delegate, String toolName) {
        this.delegate = delegate;
        this.toolName = toolName;
    }

    /**
     * 设置当前线程的工具执行进度回调。
     *
     * @param callback 进度回调，传 null 清除
     */
    public static void setProgressCallback(BiConsumer<String, String> callback) {
        if (callback == null) {
            progressCallback.remove();
        } else {
            progressCallback.set(callback);
        }
    }

    // ---- 委托 ----

    /**
     * 移除当前线程的进度回调（等效于 setProgressCallback(null)）
     */
    public static void clearProgressCallback() {
        progressCallback.remove();
    }

    private static ToolSummary t(String verb, String paramKey, String fallback) {
        return new ToolSummary(verb, paramKey, fallback);
    }

    /**
     * 从 JSON 入参中提取指定参数的值。
     */
    private static String extractArg(String argumentsJson, String paramKey) {
        if (argumentsJson == null || argumentsJson.isBlank()) return null;
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("\"" + paramKey + "\"\\s*:\\s*\"([^\"]+)\"")
                .matcher(argumentsJson);
        return m.find() ? m.group(1) : null;
    }

    /**
     * 按参数类型格式化值：路径取文件名，命令/查询截断40字，其余截断30字。
     */
    private static String formatParamValue(String paramKey, String raw) {
        if ("filePath".equals(paramKey) || "configFile".equals(paramKey)
                || "logFilePath".equals(paramKey) || "dirPath".equals(paramKey)
                || "files".equals(paramKey)) {
            return basename(raw);
        }
        if ("command".equals(paramKey) || "query".equals(paramKey) || "pattern".equals(paramKey)) {
            return truncate(raw, 40);
        }
        return truncate(raw, 30);
    }

    // ==================== 语义摘要生成 ====================

    /**
     * 从路径中提取末级文件名。
     */
    private static String basename(String path) {
        int sep = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return sep >= 0 ? path.substring(sep + 1) : path;
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    @Override
    public String execute(ToolExecutionRequest request, Object memoryId) {
        LOG.log(Level.INFO, "[工具调用] {0}", toolName);
        LOG.log(Level.INFO, "  入参: {0}", request.arguments());

        long startTime = System.currentTimeMillis();
        try {
            String result = delegate.execute(request, memoryId);

            long elapsed = System.currentTimeMillis() - startTime;
            LOG.log(Level.INFO, "[工具调用] {0} 完成，耗时 {1}ms", new Object[]{toolName, elapsed});
            LOG.log(Level.FINE, "  出参: {0}",
                    result.length() > 500 ? result.substring(0, 500) + "...[截断]" : result);

            // 通知进度回调
            BiConsumer<String, String> cb = progressCallback.get();
            if (cb != null) {
                String summary = formatSummary(request.arguments());
                cb.accept(summary, toolName);
            }

            return result;

        } catch (Exception e) {
            ErrorLogger.log(toolName, e);
            throw new ToolExecutionException(toolName, ErrorCode.INTERNAL_ERROR, e);
        }
    }

    /**
     * 基于语义模板表生成工具调用摘要，绝不出方法名。
     * <p>
     * 策略：查模板 → 提取关键参数值 → 格式化为自然语言。
     * 参数缺失时用兜底名词，无参数工具直接用配置的动词短语。
     */
    private String formatSummary(String argumentsJson) {
        String methodName = shortName();
        ToolSummary cfg = SUMMARY_CONFIG.get(methodName);

        // 1. 有模板且配置了关键参数 → 尝试提取参数值
        if (cfg != null && cfg.paramKey() != null) {
            String val = extractArg(argumentsJson, cfg.paramKey());
            if (val != null && !val.isBlank()) {
                return cfg.verb() + " " + formatParamValue(cfg.paramKey(), val);
                // "读取 UserService.java" / "运行 mvn compile" / "搜索 @Autowired"
            }
        }

        // 2. 有模板，参数缺失但有兜底 → 动词 + 兜底名词
        if (cfg != null && cfg.fallback() != null) {
            return cfg.verb() + " " + cfg.fallback();
            // "读取文件" / "运行命令" / "查看提交日志"
        }

        // 3. 有模板，无参数的纯动词短语 → 直接返回
        if (cfg != null) {
            return cfg.verb();
            // "编译诊断" / "查看覆盖率" / "查看状态"
        }

        // 4. 未配置模板（防御性兜底，不应发生）
        return methodName;
    }

    /**
     * 从全限定名中提取简短方法名（如 FileOperationTool.readFile → readFile）
     */
    private String shortName() {
        int dot = toolName.lastIndexOf('.');
        return dot >= 0 ? toolName.substring(dot + 1) : toolName;
    }

    /**
     * 工具语义摘要配置。
     *
     * @param verb     动词（2-4字），如 "读取"、"运行"、"查看日志"
     * @param paramKey 优先提取的参数名（从 JSON 入参中提取），无核心参数时为 null
     * @param fallback 参数缺失时的兜底名词（1-3字），如 "文件"、"命令"；无参数工具为 null
     */
    private record ToolSummary(String verb, String paramKey, String fallback) {
    }
}