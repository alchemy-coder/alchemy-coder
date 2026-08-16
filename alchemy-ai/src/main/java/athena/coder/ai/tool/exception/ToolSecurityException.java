package athena.coder.ai.tool.exception;

import java.io.Serial;

public class ToolSecurityException extends ToolException {
    @Serial
    private static final long serialVersionUID = 1L;

    public ToolSecurityException(String toolName, ErrorCode code, Object... args) {
        super(toolName, code, args);
    }
}