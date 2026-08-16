package athena.coder.ai.tool;

import athena.coder.ai.tool.base.FileSystemBasedTool;
import athena.coder.ai.tool.util.FileTypeConstants;
import athena.coder.ai.tool.exception.ErrorCode;
import athena.coder.ai.tool.exception.ToolValidationException;
import athena.coder.ai.tool.validation.FilePath;
import athena.coder.ai.tool.validation.NotBlank;
import athena.coder.ai.tool.validation.Range;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

public class FileOperationTool extends FileSystemBasedTool {

    private static final int MAX_DIR_ENTRIES = 200;

    @Tool("读取文件内容。支持文本文件，自动截断超大文件。返回带行号的文件内容，方便定位。")
    public String readFile(
            @P("文件路径（绝对路径或相对于项目根目录的路径）") @NotBlank(fieldName = "文件路径") @FilePath(mustExist = true, allowBinary = false) String filePath,
            @P("起始行号（从1开始），默认为1") @Range(min = 1, max = 1000000) int startLine,
            @P("结束行号（含），-1表示读到文件末尾") int endLine) {

        return executeWithAutoValidation(() -> readTextFile(filePath, startLine, endLine),
                "readFile", filePath, startLine, endLine);
    }

    @Tool("创建或覆盖写入文件内容。写入前会自动备份原文件。支持文本文件写入。")
    public String writeFile(
            @P("文件路径") @NotBlank(fieldName = "文件路径") String filePath,
            @P("要写入的内容") String content,
            @P("操作描述（用于日志和备份文件名）") String description) {

        return executeWithAutoValidation(() -> {
            writeTextFile(filePath, content, description);
            return OK_PREFIX + "文件已" + (description != null ? description : "写入") + ": " + filePath;
        }, "writeFile", filePath, content, description);
    }

    @Tool("追加内容到文件末尾。如果文件不存在则创建新文件。")
    public String appendToFile(
            @P("文件路径") @NotBlank(fieldName = "文件路径") String filePath,
            @P("要追加的内容") String content) {

        return executeWithAutoValidation(() -> {
            appendToTextFile(filePath, content);
            return OK_PREFIX + "内容已追加到: " + filePath;
        }, "appendToFile", filePath, content);
    }

    @Tool("删除文件或空目录。非空目录不会被删除以防止误删。")
    public String deleteFile(
            @P("要删除的文件或空目录路径") @NotBlank(fieldName = "文件路径") @FilePath(mustExist = true) String filePath) {

        return executeWithAutoValidation(() -> {
            Path path = resolveAndValidate(filePath);

            if (safeIsDirectory(path)) {
                try (Stream<Path> entries = safeList(path)) {
                    if (entries.findFirst().isPresent()) {
                        throw new ToolValidationException(getToolName(), ErrorCode.DIRECTORY_NOT_EMPTY, filePath);
                    }
                }
                safeDelete(path);
            } else {
                safeDelete(path);
            }

            return OK_PREFIX + "已删除: " + filePath;

        }, "deleteFile", filePath);
    }

    @Tool("列出目录内容。返回目录下的文件和子目录列表。")
    public String listDirectory(
            @P("目录路径") @NotBlank(fieldName = "目录路径") @FilePath(mustExist = true) String dirPath,
            @P("是否递归列出子目录内容，默认false") boolean recursive) {

        return executeWithAutoValidation(() -> {
            Path dir = resolveAndValidate(dirPath);

            if (!safeIsDirectory(dir)) {
                throw new ToolValidationException(getToolName(), ErrorCode.NOT_DIRECTORY, dirPath);
            }

            StringBuilder result = new StringBuilder();
            result.append(String.format("目录: %s\n", dir.getFileName()));
            result.append("---\n");

            AtomicInteger fileCount = new AtomicInteger(0);
            AtomicInteger dirCount = new AtomicInteger(0);

            try (Stream<Path> stream = recursive ? safeWalk(dir, 3) : safeList(dir)) {
                stream.filter(p -> !FileTypeConstants.IGNORED_DIRS.contains(p.getFileName().toString()))
                        .limit(MAX_DIR_ENTRIES)
                        .forEach(p -> {
                            if (safeIsDirectory(p)) {
                                result.append("[DIR]  ").append(dir.relativize(p)).append("\n");
                                dirCount.incrementAndGet();
                            } else {
                                long size = safeFileSize(p);
                                result.append(String.format("%-6s ", formatSize(size)))
                                        .append(dir.relativize(p)).append("\n");
                                fileCount.incrementAndGet();
                            }
                        });
            }

            result.append(String.format("\n总计: %d 个文件, %d 个子目录", fileCount.get(), dirCount.get()));
            return enforceOutputLimit(result.toString());

        }, "listDirectory", dirPath, recursive);
    }
}