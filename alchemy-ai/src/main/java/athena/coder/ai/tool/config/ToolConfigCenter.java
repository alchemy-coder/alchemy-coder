package athena.coder.ai.tool.config;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * 工具配置中心
 * <p>
 * 统一管理所有工具的配置参数，支持：
 * - 默认值管理
 * - 运行时动态调整
 * - 工具级别个性化配置
 */
public class ToolConfigCenter {

    private static final Logger LOG = Logger.getLogger(ToolConfigCenter.class.getName());
    private static final Path WORK_DIR = Path.of(System.getProperty("user.dir"));
    private final Map<String, Integer> toolTimeouts = new ConcurrentHashMap<>();
    private final int defaultTimeout = 30;
    private final long maxFileSize = 10 * 1024 * 1024;       // 10MB
    private final long maxReadSize = 100 * 1024;             // 100KB
    private final long maxWriteSize = 10 * 1024 * 1024;      // 10MB
    private final int maxOutputChars = 50000;
    private final int maxScanFiles = 500;
    private final boolean outputSanitizationEnabled = true;
    private final boolean rateLimitEnabled = true;
    private final int rateLimitPerMinute = 60;

    private ToolConfigCenter() {
        loadDefaults();
    }

    public static ToolConfigCenter getInstance() {
        return Holder.INSTANCE;
    }

    private void loadDefaults() {
        // Git 操作可能需要更长时间
        toolTimeouts.put("GitTool", 60);
        toolTimeouts.put("TestExecutionTool", 120);
        toolTimeouts.put("DiagnosticTool", 120);

        toolTimeouts.put("DependencyManagerTool", 60);
    }

    // ==================== 超时配置 ====================

    public int getTimeout(String toolName) {
        return toolTimeouts.getOrDefault(toolName, defaultTimeout);
    }

    public long getMaxFileSize() {
        return maxFileSize;
    }

    // ==================== 文件大小限制 ====================

    public long getMaxReadSize() {
        return maxReadSize;
    }

    public long getMaxWriteSize() {
        return maxWriteSize;
    }

    public int getMaxOutputChars() {
        return maxOutputChars;
    }

    // ==================== 输出控制 ====================

    public int getMaxScanFiles() {
        return maxScanFiles;
    }

    public boolean isOutputSanitizationEnabled() {
        return outputSanitizationEnabled;
    }

    // ==================== 安全特性开关 ====================

    public boolean isRateLimitEnabled() {
        return rateLimitEnabled;
    }

    public int getRateLimitPerMinute() {
        return rateLimitPerMinute;
    }

    public Path getAllowedWorkDir() {
        return WORK_DIR;
    }

    // ==================== 工作目录 ====================

    private static class Holder {
        static final ToolConfigCenter INSTANCE = new ToolConfigCenter();
    }
}