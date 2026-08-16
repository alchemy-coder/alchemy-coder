package athena.coder.ai.tool.base;

import java.util.Arrays;
import java.util.stream.Collectors;

public class ToolResult {

    private final Status status;
    private final String message;
    private final String details;
    private ToolResult(Status status, String message, String details) {
        this.status = status;
        this.message = message;
        this.details = details;
    }

    public static ToolResult success(String message) {
        return new ToolResult(Status.SUCCESS, message, null);
    }

    public static ToolResult success(String message, String details) {
        return new ToolResult(Status.SUCCESS, message, details);
    }

    public static ToolResult warn(String message) {
        return new ToolResult(Status.WARN, message, null);
    }

    public static ToolResult warn(String message, String details) {
        return new ToolResult(Status.WARN, message, details);
    }

    public static ToolResult error(String message) {
        return new ToolResult(Status.ERROR, message, null);
    }

    public static ToolResult error(String message, Exception e) {
        String details = e != null ? getStackTrace(e) : null;
        return new ToolResult(Status.ERROR, message, details);
    }

    private static String getStackTrace(Exception e) {
        return Arrays.stream(e.getStackTrace())
                .limit(10)
                .map(element -> "    at " + element)
                .collect(Collectors.joining("\n"));
    }

    public String toDisplayString() {
        String prefix = switch (status) {
            case SUCCESS -> ToolConstants.OK_PREFIX;
            case WARN -> ToolConstants.WARN_PREFIX;
            case ERROR -> ToolConstants.ERR_PREFIX;
        };

        StringBuilder sb = new StringBuilder(prefix + message);

        if (details != null && !details.isBlank()) {
            sb.append("\n").append(details);
        }

        return sb.toString();
    }

    public enum Status {SUCCESS, WARN, ERROR}
}