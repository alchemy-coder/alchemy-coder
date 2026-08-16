package athena.coder.ai.tool;

import athena.coder.ai.tool.base.ProcessBasedTool;
import athena.coder.ai.tool.validation.NotBlank;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

import java.nio.file.Path;
import java.util.List;

public class BasicTerminalTool extends ProcessBasedTool {

    private static final String OS_TYPE;
    private static final String OS_NAME;
    private static final String OS_VERSION;
    private static final String OS_ARCH;

    static {
        String os = System.getProperty("os.name").toLowerCase();
        OS_NAME = System.getProperty("os.name");
        OS_VERSION = System.getProperty("os.version");
        OS_ARCH = System.getProperty("os.arch");
        if (os.contains("mac")) {
            OS_TYPE = "mac";
        } else if (os.contains("win")) {
            OS_TYPE = "windows";
        } else {
            OS_TYPE = "linux";
        }
    }

    @Tool("获取当前操作系统信息，包含系统类型、版本、架构")
    public String getOperatingSystemInfo() {
        return executeSafely(() -> String.format("类型: %s | 系统: %s | 版本: %s | 架构: %s",
                OS_TYPE, OS_NAME, OS_VERSION, OS_ARCH), "getOperatingSystemInfo");
    }

    @Tool("执行终端命令并返回输出结果。支持 shell 命令，自动处理工作目录和超时。")
    public String execute(
            @P("要执行的命令，如: ls -la, git status, mvn clean install") @NotBlank(fieldName = "命令") String command) {

        return executeWithAutoValidation(() -> {
            Path workDir = getAllowedWorkDir();
            List<String> cmdList = buildCommandList(command.trim());

            String result = executeCommand(cmdList, workDir, getMyTimeout());

            if (result.startsWith(OK_PREFIX)) {
                logInfo(String.format("命令执行成功: %s", maskSensitiveInfo(command)));
            }

            return result;
        }, "execute", command);
    }

    @Tool("执行命令并返回结构化结果（包含退出码、stdout、stderr）")
    public String executeWithStatus(
            @P("要执行的命令") @NotBlank(fieldName = "命令") String command) {

        return executeWithAutoValidation(() -> {
            Path workDir = getAllowedWorkDir();
            List<String> cmdList = buildCommandList(command.trim());
            validateCommandSafety(cmdList);

            long startTime = System.currentTimeMillis();
            String rawOutput = executor.execute(cmdList, workDir, getMyTimeout());
            long durationMs = System.currentTimeMillis() - startTime;

            StringBuilder sb = new StringBuilder();
            sb.append("=== 命令执行结果 ===\n\n");
            sb.append(String.format("命令: %s\n", maskSensitiveInfo(command)));
            sb.append(String.format("耗时: %dms\n\n", durationMs));

            if (rawOutput.startsWith(PREFIX_SUCCESS)) {
                String stdout = stripPrefix(rawOutput);
                if (!stdout.isEmpty()) {
                    sb.append("--- 标准输出 ---\n");
                    sb.append(stdout);
                    sb.append("\n");
                }
            } else if (rawOutput.startsWith(PREFIX_ERROR)) {
                String stderr = stripPrefix(rawOutput);
                if (!stderr.isEmpty()) {
                    sb.append("--- 错误输出 ---\n");
                    sb.append(stderr);
                    sb.append("\n");
                }
            } else {
                sb.append("--- 输出 ---\n");
                sb.append(rawOutput);
                sb.append("\n");
            }

            return enforceOutputLimit(sb.toString());
        }, "executeWithStatus", command);
    }

    @Tool("测试命令是否可用")
    public String which(
            @P("要检查的命令名称") @NotBlank(fieldName = "命令名称") String commandName) {

        return executeWithAutoValidation(() -> {
            Path workDir = getAllowedWorkDir();

            String result = tryWhichCommand(commandName, workDir);
            if (result != null) {
                return result;
            }

            if (IS_WINDOWS) {
                result = tryWhereCommand(commandName, workDir);
                if (result != null) {
                    return result;
                }
            }

            return ERR_PREFIX + String.format("命令 '%s' 未找到或不可执行", commandName);
        }, "which", commandName);
    }

    private String tryWhichCommand(String commandName, Path workDir) {
        try {
            String result = executor.execute(List.of("which", commandName), workDir, 5000);
            if (result.startsWith(PREFIX_SUCCESS)) {
                return OK_PREFIX + String.format("命令 '%s' 可用: %s", commandName, stripPrefix(result).trim());
            }
        } catch (Exception e) {
            logFine("which 命令检查失败: " + commandName);
        }
        return null;
    }

    private String tryWhereCommand(String commandName, Path workDir) {
        try {
            String result = executor.execute(List.of("where", commandName), workDir, 5000);
            if (result.startsWith(PREFIX_SUCCESS)) {
                return OK_PREFIX + String.format("命令 '%s' 可用:\n%s", commandName, stripPrefix(result).trim());
            }
        } catch (Exception e) {
            logFine("where 命令检查失败: " + commandName);
        }
        return null;
    }
}