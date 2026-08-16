package athena.coder.ai.tool.base;

import athena.coder.ai.tool.AbstractBaseTool;
import athena.coder.ai.tool.exception.ErrorCode;
import athena.coder.ai.tool.exception.ToolValidationException;

import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public abstract class FileSystemBasedTool extends AbstractBaseTool {
    private static final DateTimeFormatter BACKUP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    protected FileSystemBasedTool() {
        super(false);
    }

    protected String readTextFile(String path, int startLine, int endLine) {
        Path resolved = resolveAndValidate(path);
        checkFileExists(resolved);
        if (!safeIsRegularFile(resolved)) {
            throw new ToolValidationException(getToolName(), ErrorCode.NOT_FILE, path);
        }
        checkNotBinary(resolved);

        long maxReadSize = config.getMaxReadSize();
        long fileSize = safeFileSize(resolved);
        if (fileSize > maxReadSize * 2) {
            return WARN_PREFIX + String.format("文件较大（%d KB），请使用 startLine/endLine 参数分段读取。\n总大小: %d bytes",
                    fileSize / 1024, fileSize);
        }

        List<String> lines = safeReadAllLines(resolved);
        int totalLines = lines.size();

        int start = Math.max(1, startLine);
        int end = (endLine == -1) ? totalLines : Math.min(endLine, totalLines);

        if (start > totalLines) {
            return WARN_PREFIX + String.format("起始行号 %d 超出文件总行数 %d", start, totalLines);
        }

        StringBuilder result = new StringBuilder();
        result.append(String.format("文件: %s (共 %d 行，当前显示 %d-%d 行)\n", resolved.getFileName(), totalLines, start, end));
        result.append("---\n");

        for (int i = start - 1; i < end; i++) {
            result.append(String.format("%4d | %s\n", i + 1, lines.get(i)));
        }

        return enforceOutputLimit(result.toString());
    }

    protected void writeTextFile(String path, String content, String description) {
        Path resolved = resolveAndValidate(path);

        long maxWriteSize = config.getMaxWriteSize();
        if (content != null && content.length() > maxWriteSize) {
            throw new ToolValidationException(getToolName(), ErrorCode.FILE_TOO_LARGE,
                    content.length() / 1024, maxWriteSize / 1024);
        }

        backupIfExists(resolved);
        ensureParentDirectory(resolved);
        safeWriteString(resolved, content != null ? content : "");
        logFine("文件已" + (description != null ? description : "写入") + ": " + path);
    }

    protected void appendToTextFile(String path, String content) {
        Path resolved = resolveAndValidate(path);

        long maxSize = config.getMaxWriteSize();
        if (safeFileExists(resolved)) {
            long currentSize = safeFileSize(resolved);
            if (currentSize + content.length() > maxSize) {
                throw new ToolValidationException(getToolName(), ErrorCode.FILE_TOO_LARGE,
                        (currentSize + content.length()) / 1024, maxSize / 1024);
            }
        } else {
            ensureParentDirectory(resolved);
        }

        safeWriteString(resolved, content != null ? content : "", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    private void backupIfExists(Path path) {
        if (!safeFileExists(path)) return;

        Path parent = path.getParent();
        if (parent == null || !safeFileExists(parent)) return;

        Path backupDir = parent.resolve(".backup");
        if (!safeFileExists(backupDir)) {
            safeCreateDirectories(backupDir);
        }

        String timestamp = LocalDateTime.now().format(BACKUP_FORMAT);
        String backupName = path.getFileName().toString() + "." + timestamp + ".bak";
        Path backupPath = backupDir.resolve(backupName);

        safeCopy(path, backupPath);
        logFine("备份文件: " + path + " -> " + backupPath);
    }

    private void ensureParentDirectory(Path path) {
        Path parent = path.getParent();
        if (parent != null && !safeFileExists(parent)) {
            safeCreateDirectories(parent);
        }
    }
}