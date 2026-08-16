package athena.coder.ai.tool.exception;

import java.io.Serial;

public class ToolExecutionException extends ToolException {
    @Serial
    private static final long serialVersionUID = 1L;

    public ToolExecutionException(String toolName, ErrorCode code, Throwable cause) {
        super(toolName, code, cause, cause.getMessage());
    }

    public ToolExecutionException(String toolName, ErrorCode code, Throwable cause, Object... args) {
        super(toolName, code, cause, args);
    }
}