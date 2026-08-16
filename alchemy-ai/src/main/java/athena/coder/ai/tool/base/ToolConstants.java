package athena.coder.ai.tool.base;

/**
 * 工具层常量定义
 * <p>
 * 两套前缀的用途区分：
 * <ul>
 *   <li>中文前缀（OK_PREFIX / WARN_PREFIX / ERR_PREFIX）—— 面向用户的展示前缀，用于 Tool 返回结果的可读性标注</li>
 *   <li>英文前缀（PREFIX_SUCCESS / PREFIX_FAILED / PREFIX_ERROR / PREFIX_TIMEOUT）—— CommandExecutor 内部协议前缀，
 *       用于标识命令执行结果状态，由 {@link #stripResultPrefix(String)} 剥离后再传递给业务层</li>
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

    public static String stripResultPrefix(String result) {
        if (result == null) {
            return "";
        }
        if (result.startsWith(PREFIX_SUCCESS)) {
            return result.substring(PREFIX_SUCCESS.length());
        }
        if (result.startsWith(PREFIX_FAILED)) {
            return result.substring(PREFIX_FAILED.length());
        }
        if (result.startsWith(PREFIX_ERROR)) {
            return result.substring(PREFIX_ERROR.length());
        }
        if (result.startsWith(PREFIX_TIMEOUT)) {
            return result.substring(PREFIX_TIMEOUT.length());
        }
        return result;
    }
}