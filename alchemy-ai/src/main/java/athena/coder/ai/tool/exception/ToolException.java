package athena.coder.ai.tool.exception;

import java.io.Serial;

public class ToolException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;
    private final String toolName;
    private final ErrorCode errorCode;

    public ToolException(String toolName, ErrorCode errorCode, Object... args) {
        super(formatMessage(errorCode, args));
        this.toolName = toolName;
        this.errorCode = errorCode;
    }

    public ToolException(String toolName, ErrorCode errorCode, Throwable cause, Object... args) {
        super(formatMessage(errorCode, args), cause);
        this.toolName = toolName;
        this.errorCode = errorCode;
    }

    private static String formatMessage(ErrorCode code, Object... args) {
        try {
            return String.format(code.getMessageTemplate(), args);
        } catch (Exception e) {
            return code.getMessageTemplate();
        }
    }

    public String getToolName() {
        return toolName;
    }

    public String toDisplayString() {
        return String.format("[错误-%d] %s", errorCode.getCode(), getMessage());
    }
}