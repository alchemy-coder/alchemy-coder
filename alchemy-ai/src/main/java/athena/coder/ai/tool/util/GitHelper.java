package athena.coder.ai.tool.util;

import athena.coder.ai.spi.ErrorLogger;
import athena.coder.exception.RocAgentException;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Git 命令静态工具类（供工作流节点调用）
 * <p>
 * 从 GitTool 提取的纯 git 命令封装，不依赖 Tool 实例状态，
 * 解耦 workflow 层对 tool 层的直接依赖。
 */
public final class GitHelper {

    private static final Logger LOG = Logger.getLogger(GitHelper.class.getName());
    private static final int GIT_TIMEOUT_SECONDS = 60;
    private static final ExecutorService GIT_EXECUTOR = Executors.newFixedThreadPool(
            Runtime.getRuntime().availableProcessors(), r -> {
                Thread t = new Thread(r, "git-executor");
                t.setDaemon(true);
                return t;
            });

    private GitHelper() {
    }

    /**
     * 执行 git 命令（异步流消费，防止管道缓冲区满导致死锁）
     *
     * @return 命令标准输出（trim后），失败时返回 null
     */
    public static String runGit(String projectPath, String... args) {
        Process process = null;
        try {
            List<String> cmd = new ArrayList<>();
            cmd.add("git");
            cmd.addAll(List.of(args));

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(Path.of(projectPath).toFile());
            pb.redirectErrorStream(true);

            process = pb.start();
            final Process proc = process;

            CompletableFuture<String> outputFuture = CompletableFuture.supplyAsync(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                    return reader.lines().collect(Collectors.joining("\n"));
                } catch (Exception e) {
                    ErrorLogger.warn("GitHelper.runGit", "读取 git 输出流异常: " + e.getMessage());
                    return "";
                }
            }, GIT_EXECUTOR);

            boolean finished = process.waitFor(GIT_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            if (!finished) {
                process.destroyForcibly();
                outputFuture.cancel(true);
                ErrorLogger.warn("GitHelper.runGit", "git 命令超时(" + GIT_TIMEOUT_SECONDS + "s): " + String.join(" ", args));
                return null;
            }

            String output = outputFuture.get(30, TimeUnit.SECONDS);

            if (process.exitValue() != 0) {
                ErrorLogger.warn("GitHelper.runGit", "git 命令失败(exit=" + process.exitValue() + "): "
                        + String.join(" ", args) + " -> " + output);
                return null;
            }

            return output.trim();
        } catch (Exception e) {
            ErrorLogger.warn("GitHelper.runGit", "git 命令执行异常: " + String.join(" ", args) + " | " + e.getMessage());
            return null;
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    /**
     * 隔离用户未提交的改动
     *
     * @return true 表示有改动被 stash 了
     */
    public static boolean stashUserChanges(String projectPath) {
        String status = runGit(projectPath, "status", "--porcelain");
        if (status == null || status.isBlank()) {
            return false;
        }
        String result = runGit(projectPath, "stash", "push", "--include-untracked", "-m", "auto-save-before-ai-coder");
        if (result == null) {
            ErrorLogger.warn("GitHelper.stashUserChanges", "git stash push 失败（返回 null)，用户改动未被 stash，后续 restore 将被跳过");
            return false;
        }
        LOG.log(Level.INFO, "已 stash 用户未提交的改动");
        return true;
    }

    /**
     * 恢复用户改动，冲突时保留 stash 条目并告警
     */
    public static void restoreUserChanges(String projectPath, boolean hasUserChanges) {
        if (!hasUserChanges) return;
        try {
            String result = runGit(projectPath, "stash", "apply", "stash@{0}");
            if (result == null) {
                ErrorLogger.warn("GitHelper.restoreUserChanges", "git stash apply stash@{0} 失败（可能产生冲突)！用户改动仍保留在 stash 中，请手动执行 'git stash pop' 解决冲突");
                runGit(projectPath, "reset", "--hard", "HEAD");
            } else {
                String dropResult = runGit(projectPath, "stash", "drop", "stash@{0}");
                if (dropResult == null) {
                    ErrorLogger.warn("GitHelper.restoreUserChanges", "git stash drop stash@{0} 失败，stash 条目可能残留，请手动检查 'git stash list'");
                } else {
                    LOG.info("已恢复用户改动");
                }
            }
        } catch (Exception e) {
            ErrorLogger.warn("GitHelper.restoreUserChanges", "git stash 恢复失败，用户改动仍保留在 stash 中: " + e.getMessage());
        }
    }

    /**
     * 校验目录是否为 git 仓库
     */
    public static boolean isGitRepo(String projectPath) {
        return java.nio.file.Files.exists(Path.of(projectPath, ".git"));
    }

    /**
     * 清理 AI 执行失败后的工作区：重置已跟踪文件 + 删除未跟踪文件/目录（遵循 .gitignore）。
     * 仅用于重试前，不恢复用户改动（stash 保持隔离）
     */
    public static void cleanAiWorkspace(String projectPath) {
        String untracked = runGit(projectPath, "ls-files", "--others", "--exclude-standard");
        if (untracked != null && !untracked.isBlank()) {
            ErrorLogger.warn("GitHelper.cleanAiWorkspace", "即将清理以下未跟踪文件:\n" + untracked);
        }
        runGit(projectPath, "reset", "--hard", "HEAD");
        runGit(projectPath, "clean", "-fd");
    }

    /**
     * 隔离提交（AI 变更的 git 安全编排，供各子流程的文件修改类节点共用）
     * <p>
     * 完整时序：stash 隔离用户改动 → 记录变更前 HEAD → 执行 work（AI 修改文件）
     * → git add + commit 记录 AI 变更 → 恢复用户改动；
     * work 抛异常时（业务已判定最终失败）清理 AI 工作区 + 恢复用户改动后上抛；
     * add/commit 失败时回滚到变更前 HEAD 并恢复用户改动后抛出。
     * <p>
     * 调用方若需在隔离期内重试，应在 work 内部自行完成（重试前可调用 {@link #cleanAiWorkspace}）
     *
     * @param commitMsg AI 变更的 commit message（如 "AI-CODER: task-123"）
     * @param work      实际执行文件修改的业务逻辑（通常为 Agent 调用，返回值随结果一并返回）
     * @return 隔离提交结果（aiCommit/diffRef 在无法获取 HEAD 时为空串，workResult 为 work 的返回值）
     */
    public static <T> IsolationResult<T> isolateAndCommit(String projectPath, String commitMsg,
                                                          Callable<T> work) throws Exception {
        // 1. 隔离用户改动
        boolean hasUserChanges = stashUserChanges(projectPath);

        // 2. 记录变更前的 HEAD
        String beforeCommit = runGit(projectPath, "rev-parse", "HEAD");
        if (beforeCommit == null || beforeCommit.isBlank()) {
            ErrorLogger.warn("GitHelper.isolateAndCommit", "无法获取 git HEAD，diff 追踪将不可用");
        }

        // 3. 执行业务变更；最终失败时清理 AI 工作区并恢复用户改动后上抛
        T workResult;
        try {
            workResult = work.call();
        } catch (Exception e) {
            cleanAiWorkspace(projectPath);
            restoreUserChanges(projectPath, hasUserChanges);
            throw e;
        }

        // 4. 将 AI 变更通过 git commit 记录
        String aiCommit = null;
        String diffRef = null;
        if (beforeCommit != null && !beforeCommit.isBlank()) {
            String addResult = runGit(projectPath, "add", "--", ".");
            if (addResult == null) {
                rollbackAndRestore(projectPath, beforeCommit, hasUserChanges, "git add");
            }
            String commitResult = runGit(projectPath, "commit", "--allow-empty", "-m", commitMsg);
            if (commitResult == null) {
                rollbackAndRestore(projectPath, beforeCommit, hasUserChanges, "git commit");
            }
            aiCommit = runGit(projectPath, "rev-parse", "HEAD");
            if (aiCommit != null && !aiCommit.isBlank()) {
                diffRef = beforeCommit.trim() + ".." + aiCommit.trim();
                LOG.log(Level.INFO, "AI 变更已提交: {0}", diffRef);
            }
        }

        // 5. 恢复用户改动
        restoreUserChanges(projectPath, hasUserChanges);

        return new IsolationResult<>(aiCommit != null ? aiCommit : "", diffRef != null ? diffRef : "", workResult);
    }

    /**
     * 提交失败时的统一回滚：重置到变更前 HEAD + 恢复用户改动，然后上抛
     */
    private static void rollbackAndRestore(String projectPath, String beforeCommit,
                                           boolean hasUserChanges, String failedStep) {
        runGit(projectPath, "reset", "--hard", beforeCommit.trim());
        restoreUserChanges(projectPath, hasUserChanges);
        throw new RocAgentException("GitHelper.isolateAndCommit: " + failedStep + " 失败，AI 变更无法记录，已重置工作区");
    }

    /**
     * 隔离提交结果：aiCommit 为 AI 变更的 commit hash，diffRef 为 变更前HEAD..aiCommit 区间引用，
     * workResult 为业务逻辑（work）的返回值
     */
    public record IsolationResult<T>(String aiCommit, String diffRef, T workResult) {
    }
}
