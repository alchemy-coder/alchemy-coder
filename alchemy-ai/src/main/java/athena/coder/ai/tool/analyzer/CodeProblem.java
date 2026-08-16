package athena.coder.ai.tool.analyzer;

import org.jspecify.annotations.NonNull;

public record CodeProblem(int line, String severity, String message) {

    @Override
    public @NonNull String toString() {
        String location = line > 0 ? "行 " + line + ": " : "";
        return "[" + severity + "] " + location + message;
    }
}