package athena.coder.ai.tool;

import athena.coder.ai.tool.base.ProcessBasedTool;
import athena.coder.ai.tool.exception.ErrorCode;
import athena.coder.ai.tool.exception.ToolSecurityException;
import athena.coder.ai.tool.exception.ToolValidationException;
import athena.coder.ai.tool.validation.NotBlank;
import athena.coder.ai.tool.validation.PatternRegex;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.jspecify.annotations.NonNull;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Git 版本控制工具
 * 提供 Git 操作能力，包括状态查看、差异比较、提交等
 * <p>
 * 安全特性：
 * - 继承 ProcessBasedTool 的命令执行能力
 * - 硬编码黑名单：禁止危险操作
 * - 工作目录严格校验
 * - 统一异常处理和输出脱敏
 */
public class GitTool extends ProcessBasedTool {

    private static final Map<String, Set<String>> DANGEROUS_PARAMS = Map.of(
            "push", Set.of("--force", "-f", "--force-with-lease"),
            "reset", Set.of("--hard"),
            "clean", Set.of("-fd", "-fxd", "-f", "-d", "-x"),
            "checkout", Set.of("--", "."),
            "revert", Set.of("--hard"),
            "rm", Set.of("--cached", "-r", "-rf")
    );

    private static @NonNull List<String> buildDiffCommand(String target, String filePath) {
        List<String> command = new ArrayList<>(List.of("git", "diff"));

        if ("staged".equalsIgnoreCase(target)) {
            command.add("--cached");
        } else if (target != null && !target.isBlank() && !"unstaged".equalsIgnoreCase(target)) {
            command.add(target);
        }

        if (filePath != null && !filePath.isBlank()) {
            command.add("--");
            command.add(filePath);
        }
        return command;
    }

    @Tool("获取 Git 仓库状态信息，包括当前分支、未提交的修改、未跟踪的文件等。类似 git status。")
    public String gitStatus(
            @P("Git 仓库目录路径") String workingDir) {

        return executeSafely(() -> {
            Path workDir = resolveGitWorkDir(workingDir);
            List<String> command = List.of("git", "status", "--short", "--branch");
            String result = executeGitCommand(command, workDir);

            return formatGitResult(result, "当前 Git 状态", "获取状态失败");
        }, "gitStatus");
    }

    @Tool("查看 Git 提交历史。返回最近的提交记录，包含提交哈希、作者、日期、提交信息。")
    public String gitLog(
            @P("Git 仓库目录路径") String workingDir,
            @P("最大返回提交数，默认20") int maxCount,
            @P("可选，限定查看某个文件的提交历史，为空则查看整个仓库") String filePath) {

        return executeSafely(() -> {
            Path workDir = resolveGitWorkDir(workingDir);

            int count = maxCount <= 0 ? 20 : Math.min(maxCount, 100);

            List<String> command = new ArrayList<>(List.of(
                    "git", "log",
                    "--oneline",
                    "--format=%h | %an | %ar | %s",
                    "-n", String.valueOf(count)
            ));

            if (filePath != null && !filePath.isBlank()) {
                command.add("--");
                command.add(filePath);
            }

            String result = executeGitCommand(command, workDir);
            return formatGitResult(result, String.format("提交历史（最近 %d 条）", count), "获取历史失败");
        }, "gitLog");
    }

    @Tool("查看文件差异。可以查看工作区与暂存区的差异，或两次提交之间的差异。")
    public String gitDiff(
            @P("Git 仓库目录路径") String workingDir,
            @P("差异目标：'staged'表示已暂存的修改，'unstaged'表示未暂存的修改，或传入commit hash查看与HEAD的差异") String target,
            @P("可选，限定查看某个文件的差异") String filePath) {

        return executeSafely(() -> {
            Path workDir = resolveGitWorkDir(workingDir);
            List<String> command = buildDiffCommand(target, filePath);
            String result = executeGitCommand(command, workDir);

            return formatGitResult(result, "差异内容", "获取差异失败");
        }, "gitDiff");
    }

    @Tool("查看某个文件的 Git blame 信息，显示每一行的最后修改者和提交时间。")
    public String gitBlame(
            @P("Git 仓库目录路径") String workingDir,
            @NotBlank(fieldName = "文件路径") @P("文件路径（相对于仓库根目录）") String filePath) {

        return executeWithAutoValidation(() -> {
            Path workDir = resolveGitWorkDir(workingDir);

            List<String> command = List.of("git", "blame", "--line-porcelain", filePath);
            String result = executeGitCommand(command, workDir);
            return formatGitResult(result, "Blame 信息", "获取 blame 失败");
        }, "gitBlame", workingDir, filePath);
    }

    @Tool("将文件添加到 Git 暂存区（git add）。")
    public String gitAdd(
            @P("Git 仓库目录路径") String workingDir,
            @NotBlank(fieldName = "文件路径") @P("要添加的文件路径，'.'表示添加所有修改") String filePath) {

        return executeWithAutoValidation(() -> {
            Path workDir = resolveGitWorkDir(workingDir);

            List<String> command = List.of("git", "add", filePath);
            String result = executeGitCommand(command, workDir);

            if (result.startsWith(OK_PREFIX)) {
                return "已添加到暂存区: " + filePath;
            }
            return formatGitResult(result, "已添加", "添加失败");
        }, "gitAdd", workingDir, filePath);
    }

    @Tool("创建 Git 提交（git commit）。请确保已经 git add 添加了要提交的文件。")
    public String gitCommit(
            @P("Git 仓库目录路径") String workingDir,
            @NotBlank(fieldName = "提交信息") @P("提交信息，应清晰描述本次修改的内容") String message) {

        return executeWithAutoValidation(() -> {
            Path workDir = resolveGitWorkDir(workingDir);

            List<String> command = List.of("git", "commit", "-m", message);
            String result = executeGitCommand(command, workDir);
            return formatGitResult(result, "提交成功", "提交失败");
        }, "gitCommit", workingDir, message);
    }

    // ==================== 辅助方法 ====================

    @Tool("查看某个 commit 的详细内容，包括修改了哪些文件和具体差异。")
    public String gitShow(
            @P("Git 仓库目录路径") String workingDir,
            @NotBlank(fieldName = "提交哈希") @PatternRegex(regexp = "[a-fA-F0-9]+", message = "提交哈希格式无效") @P("提交哈希（支持短哈希）") String commitHash) {

        return executeWithAutoValidation(() -> {
            Path workDir = resolveGitWorkDir(workingDir);

            List<String> command = List.of("git", "show", "--stat", commitHash);
            String result = executeGitCommand(command, workDir);
            return formatGitResult(result, "提交详情", "获取提交详情失败");
        }, "gitShow", workingDir, commitHash);
    }

    private Path resolveGitWorkDir(String workingDir) {
        Path workDir = resolveAndValidate(workingDir);
        checkGitRepository(workDir);
        return workDir;
    }

    private String formatGitResult(String result, String successPrefix, String failPrefix) {
        if (result.startsWith(OK_PREFIX)) {
            String output = result.substring(OK_PREFIX.length());
            if (output.isEmpty()) {
                return successPrefix + "（无输出）";
            }
            return successPrefix + ":\n" + output;
        }
        return failPrefix + ": " + result;
    }

    private String executeGitCommand(List<String> command, Path workDir) {
        String dangerous = detectDangerousCommand(command);
        if (dangerous != null) {
            throw new ToolSecurityException(getToolName(), ErrorCode.DANGEROUS_COMMAND, dangerous);
        }

        return executeCommand(command, workDir, getMyTimeout());
    }

    private void checkGitRepository(Path workDir) throws ToolValidationException {
        Path gitDir = workDir.resolve(".git");
        if (!safeFileExists(gitDir)) {
            throw new ToolValidationException(getToolName(), ErrorCode.FILE_NOT_FOUND, ".git 目录不存在");
        }
    }

    /**
     * 检测命令列表中是否包含危险操作
     * 解析 git 子命令及其参数，逐一与黑名单做精确匹配
     * 支持嵌套子命令场景（如 git push --force origin main）
     *
     * @return 命中的危险命令描述，未命中返回 null
     */
    private String detectDangerousCommand(List<String> command) {
        int gitIdx = -1;
        for (int i = 0; i < command.size(); i++) {
            if ("git".equals(command.get(i).trim())) {
                gitIdx = i;
                break;
            }
        }
        if (gitIdx < 0 || gitIdx + 1 >= command.size()) {
            return null;
        }

        String subCommand = command.get(gitIdx + 1).trim().toLowerCase();
        if (!DANGEROUS_PARAMS.containsKey(subCommand)) {
            return null;
        }

        Set<String> dangerousArgs = DANGEROUS_PARAMS.get(subCommand);
        for (int i = gitIdx + 2; i < command.size(); i++) {
            String arg = command.get(i).trim();
            if (arg.startsWith("-") && dangerousArgs.contains(arg)) {
                return "git " + subCommand + " " + arg;
            }
        }

        return null;
    }

}