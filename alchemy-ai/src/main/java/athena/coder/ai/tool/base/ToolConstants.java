package athena.coder.ai.tool.base;

/**
 * 工具层常量定义
 * <p>
 * 两套前缀的用途区分：
 * <ul>
 *   <li>中文前缀（OK_PREFIX / WARN_PREFIX / ERR_PREFIX）—— 面向用户的展示前缀，用于 Tool 返回结果的可读性标注</li>
 *   <li>英文前缀（PREFIX_SUCCESS / PREFIX_FAILED / PREFIX_ERROR / PREFIX_TIMEOUT）—— CommandExecutor 内部协议前缀，
 *       用于标识命令执行结果状态，由 {@link #parseResult(String)} 统一解析</li>
 * </ul>
 */
public final class ToolConstants {

    public static final String OK_PREFIX = "成功：";
    public static final String WARN_PREFIX = "警告：";
    public static final String ERR_PREFIX = "错误：";

    public static final String PREFIX_SUCCESS = "SUCCESS\n";
    public static final String PREFIX_FAILED = "FAILED\n";
    public static final String PREFIX_ERROR = "ERROR\n";
    public static final String PREFIX_TIMEOUT = "TIMEOUT\n";

    private ToolConstants() {
    }

    /**
     * 命令执行结果状态，与协议前缀一一对应。
     */
    public enum CommandStatus {
        SUCCESS(PREFIX_SUCCESS, 0),
        FAILED(PREFIX_FAILED, 1),
        ERROR(PREFIX_ERROR, 1),
        TIMEOUT(PREFIX_TIMEOUT, -1),
        UNKNOWN(null, -1);

        private final String prefix;
        private final int exitCode;

        CommandStatus(String prefix, int exitCode) {
            this.prefix = prefix;
            this.exitCode = exitCode;
        }

        public String prefix() {
            return prefix;
        }

        public int exitCode() {
            return exitCode;
        }
    }

    /**
     * 命令执行结果：状态 + 剥离前缀后的正文。
     */
    public record CommandResult(CommandStatus status, String body) {

        public boolean isSuccess() {
            return status == CommandStatus.SUCCESS;
        }

        public int exitCode() {
            return status.exitCode();
        }
    }

    /**
     * 统一解析命令执行结果前缀，返回状态与正文。无匹配前缀时按 {@link CommandStatus#UNKNOWN} 处理。
     */
    public static CommandResult parseResult(String raw) {
        if (raw == null) {
            return new CommandResult(CommandStatus.UNKNOWN, "");
        }
        for (CommandStatus status : CommandStatus.values()) {
            if (status.prefix() != null && raw.startsWith(status.prefix())) {
                return new CommandResult(status, raw.substring(status.prefix().length()));
            }
        }
        return new CommandResult(CommandStatus.UNKNOWN, raw);
    }

    public static String stripResultPrefix(String result) {
        return parseResult(result).body();
    }
}
