package athena.coder.ai.tool;

import athena.coder.ai.tool.base.ToolConstants;
import athena.coder.ai.tool.config.ToolConfigCenter;
import athena.coder.ai.tool.util.FileTypeConstants;
import athena.coder.ai.tool.util.PatternRegistry;
import athena.coder.ai.tool.exception.*;
import athena.coder.ai.tool.executor.CommandExecutor;
import athena.coder.ai.tool.executor.ProcessCommandExecutor;
import athena.coder.ai.tool.security.OutputSanitizer;
import athena.coder.ai.tool.security.ToolRateLimiter;
import athena.coder.ai.tool.validation.ParameterValidator;
import athena.coder.ai.spi.ErrorLogger;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

public abstract class AbstractBaseTool {

    private final Logger log = Logger.getLogger(getClass().getName());

    // ==================== 子类日志包装方法 ====================

    protected void logInfo(String msg) { log.info(msg); }

    protected void logFine(String msg) { log.fine(msg); }

    protected static final String ERR_PREFIX = ToolConstants.ERR_PREFIX;
    protected static final String WARN_PREFIX = ToolConstants.WARN_PREFIX;
    protected static final String OK_PREFIX = ToolConstants.OK_PREFIX;

    protected static final String PREFIX_SUCCESS = ToolConstants.PREFIX_SUCCESS;
    protected static final String PREFIX_FAILED = ToolConstants.PREFIX_FAILED;
    protected static final String PREFIX_ERROR = ToolConstants.PREFIX_ERROR;
    protected static final String PREFIX_TIMEOUT = ToolConstants.PREFIX_TIMEOUT;
    protected static final boolean IS_WINDOWS = System.getProperty("os.name", "").toLowerCase().contains("win");
    protected final ToolConfigCenter config;
    protected final ToolRateLimiter rateLimiter;
    protected final OutputSanitizer sanitizer;
    protected final PatternRegistry patternRegistry;
    protected final ParameterValidator parameterValidator;
    protected CommandExecutor executor;

    protected AbstractBaseTool() {
        this(false);
    }

    protected AbstractBaseTool(boolean needCommandExecutor) {
        this.config = ToolConfigCenter.getInstance();
        this.patternRegistry = PatternRegistry.getInstance();
        this.rateLimiter = new ToolRateLimiter(config);
        this.sanitizer = new OutputSanitizer(config, this.patternRegistry);
        this.parameterValidator = new ParameterValidator(this);

        if (needCommandExecutor) {
            this.executor = new ProcessCommandExecutor();
        }
    }

    public static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024));
    }

    public static boolean isWindows() {
        return IS_WINDOWS;
    }

    /**
     * 提取匹配行的上下文行，用于搜索结果展示
     *
     * @param lines        所有行
     * @param matchIndex   匹配行索引
     * @param contextLines 上下文行数
     * @return 带前缀的上下文行数组，匹配行前有 ">>>" 标记
     */
    public static String[] extractContextLines(List<String> lines, int matchIndex, int contextLines) {
        if (contextLines <= 0) {
            return new String[0];
        }
        int start = Math.max(0, matchIndex - contextLines);
        int end = Math.min(lines.size(), matchIndex + contextLines + 1);
        String[] context = new String[end - start];
        for (int j = start, idx = 0; j < end; j++, idx++) {
            String prefix = (j == matchIndex) ? ">>>" : "   ";
            context[idx] = String.format("%s %4d | %s", prefix, j + 1, lines.get(j));
        }
        return context;
    }

    public Path resolveAndValidate(String path) throws ToolValidationException, ToolSecurityException {
        if (path == null || path.isBlank()) {
            throw new ToolValidationException(getToolName(), ErrorCode.PARAM_MISSING, "路径");
        }

        if (path.contains("..")) {
            throw new ToolSecurityException(getToolName(), ErrorCode.PATH_TRAVERSAL, path);
        }

        Path resolved = Paths.get(path);
        if (!resolved.isAbsolute()) {
            resolved = config.getAllowedWorkDir().resolve(path);
        }

        Path allowedDir = config.getAllowedWorkDir();
        if (!resolved.startsWith(allowedDir)) {
            ErrorLogger.log(getToolName() + ".resolveAndValidate", new ToolSecurityException(getToolName(), ErrorCode.PATH_INVALID, path));
            throw new ToolSecurityException(getToolName(), ErrorCode.PATH_INVALID, path);
        }

        return resolved;
    }

    protected <T> T executeSafely(Supplier<T> action, String operationName) {
        sanitizer.setContext(getToolName());

        try {
            rateLimiter.checkRateLimit(getToolName());
            long start = System.currentTimeMillis();
            T result = action.get();

            long elapsed = System.currentTimeMillis() - start;
            log.info(String.format("[%s.%s] 完成，耗时 %dms", getToolName(), operationName, elapsed));

            if (result instanceof String strResult) {
                return (T) sanitizer.process(strResult);
            }

            return result;

        } catch (ToolValidationException e) {
            ErrorLogger.warn(getToolName() + "." + operationName, "参数错误: " + e.getMessage());
            throw e;

        } catch (ToolSecurityException e) {
            ErrorLogger.log(getToolName() + "." + operationName, e);
            throw e;

        } catch (ToolExecutionException e) {
            ErrorLogger.log(getToolName() + "." + operationName, e);
            throw e;

        } catch (Exception e) {
            ErrorLogger.log(getToolName() + "." + operationName, e);
            throw new ToolExecutionException(getToolName(), ErrorCode.INTERNAL_ERROR, e);
        }
    }

    protected <T> T executeWithAutoValidation(Supplier<T> action, String operationName, Object... args) {
        parameterValidator.validateParameters(operationName, args);
        return executeSafely(action, operationName);
    }

    private volatile String cachedToolName;

    public String getToolName() {
        if (cachedToolName == null) {
            String name = this.getClass().getSimpleName();
            int dollarIdx = name.indexOf("$$");
            cachedToolName = dollarIdx > 0 ? name.substring(0, dollarIdx) : name;
        }
        return cachedToolName;
    }

    protected String enforceOutputLimit(String output) {
        return sanitizer.process(output);
    }

    protected String formatError(ErrorCode code, Object... args) {
        return new ToolException(getToolName(), code, args).toDisplayString();
    }

    protected int getMyTimeout() {
        return config.getTimeout(getToolName());
    }

    protected Path getAllowedWorkDir() {
        return config.getAllowedWorkDir();
    }

    protected boolean isBinaryFile(Path path) {
        return FileTypeConstants.isBinaryFile(path);
    }

    protected boolean isCodeFile(String fileName) {
        return FileTypeConstants.isCodeFile(fileName);
    }

    public void checkFileExists(Path path) throws ToolValidationException {
        if (!Files.exists(path)) {
            throw new ToolValidationException(getToolName(), ErrorCode.FILE_NOT_FOUND, path.toString());
        }
    }

    public void checkNotBinary(Path path) throws ToolValidationException {
        if (isBinaryFile(path)) {
            throw new ToolValidationException(getToolName(), ErrorCode.BINARY_FILE, path.toString());
        }
    }

    /**
     * 从结果字符串中去除前缀
     */
    protected String stripPrefix(String result) {
        return ToolConstants.stripResultPrefix(result);
    }

    // ==================== 安全文件操作工具方法 ====================

    /**
     * 获取文件扩展名（含点号，如 ".java"）
     */
    protected String getFileExtensionWithDot(String filePath) {
        if (filePath == null || filePath.isBlank()) return "";
        int lastDot = filePath.lastIndexOf('.');
        if (lastDot <= 0 || lastDot == filePath.length() - 1) return "";
        return filePath.substring(lastDot);
    }

    protected List<String> safeReadAllLines(Path path) {
        return safeFileOp(() -> Files.readAllLines(path), ErrorCode.FILE_READ_ERROR, path.toString());
    }

    protected String safeReadString(Path path) {
        return safeFileOp(() -> Files.readString(path), ErrorCode.FILE_READ_ERROR, path.toString());
    }

    protected void safeWriteString(Path path, String content, OpenOption... options) {
        safeFileOp(() -> { Files.writeString(path, content, options); return null; }, ErrorCode.FILE_WRITE_ERROR, path.toString());
    }

    protected long safeFileSize(Path path) {
        return safeFileOp(() -> Files.size(path), ErrorCode.FILE_ACCESS_ERROR, path.toString());
    }

    protected boolean safeFileExists(Path path) {
        return Files.exists(path);
    }

    protected boolean safeIsRegularFile(Path path) {
        return Files.isRegularFile(path);
    }

    public boolean safeIsDirectory(Path path) {
        return Files.isDirectory(path);
    }

    protected void safeCreateDirectories(Path dir) {
        safeFileOp(() -> { Files.createDirectories(dir); return null; }, ErrorCode.FILE_WRITE_ERROR, dir.toString());
    }

    protected void safeDelete(Path path) {
        safeFileOp(() -> { Files.delete(path); return null; }, ErrorCode.FILE_DELETE_ERROR, path.toString());
    }

    protected void safeCopy(Path source, Path target) {
        safeFileOp(() -> { Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING); return null; },
                ErrorCode.FILE_COPY_ERROR, source + " -> " + target);
    }

    protected Stream<Path> safeList(Path dir) {
        return safeFileOp(() -> Files.list(dir), ErrorCode.FILE_LIST_ERROR, dir.toString());
    }

    protected Stream<Path> safeWalk(Path dir, int maxDepth) {
        return safeFileOp(() -> Files.walk(dir, maxDepth), ErrorCode.FILE_LIST_ERROR, dir.toString());
    }

    // ─── 核心泛型文件操作包装 ───

    @FunctionalInterface
    private interface FileOp<T> {
        T execute() throws IOException;
    }

    /**
     * 统一文件操作包装：执行 IO 操作，失败时记录日志并抛出 ToolExecutionException
     */
    private <T> T safeFileOp(FileOp<T> op, ErrorCode code, String target) {
        try {
            return op.execute();
        } catch (IOException e) {
            ErrorLogger.log(getToolName() + ".fileOp(" + code + ")", e);
            throw new ToolExecutionException(getToolName(), code, e, target);
        }
    }
}