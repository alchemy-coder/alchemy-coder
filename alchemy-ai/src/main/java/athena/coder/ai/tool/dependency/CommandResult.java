package athena.coder.ai.tool.dependency;

public record CommandResult(int exitCode, String output, String error) {

    public boolean isSuccess() {
        return exitCode == 0;
    }
}