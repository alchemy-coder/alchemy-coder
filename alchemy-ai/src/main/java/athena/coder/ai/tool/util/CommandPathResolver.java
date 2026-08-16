package athena.coder.ai.tool.util;

import athena.coder.ai.util.ProjectType;

import java.util.concurrent.TimeUnit;

/**
 * 命令存在性探测：统一 where/which 逻辑，供依赖策略与终端工具复用。
 */
public final class CommandPathResolver {

    private CommandPathResolver() {
    }

    /**
     * 解析命令在 PATH 上的实际路径，找不到时返回 null。
     * <p>
     * 平台探测：Windows 用 {@code where}，其它平台用 {@code which}。
     */
    public static String resolve(String command) {
        if (command == null || command.isBlank()) {
            return null;
        }
        String probe = ProjectType.isWindows() ? "where" : "which";
        try {
            Process process = new ProcessBuilder(probe, command).start();
            boolean completed = process.waitFor(2, TimeUnit.SECONDS);
            if (!completed || process.exitValue() != 0) {
                return null;
            }
            try (var reader = process.inputReader()) {
                String first = reader.readLine();
                return (first == null || first.isBlank()) ? command : first.trim();
            }
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean exists(String command) {
        return resolve(command) != null;
    }
}
