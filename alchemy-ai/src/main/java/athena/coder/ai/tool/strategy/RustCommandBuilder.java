package athena.coder.ai.tool.strategy;

import java.util.List;

public class RustCommandBuilder extends AbstractCommandBuilder {

    public RustCommandBuilder() {
        super("rust");
    }

    @Override
    protected String getBaseCommand() {
        return "cargo";
    }

    @Override
    protected void addTestFilterArgument(List<String> command, String filter) {
        command.add(filter);
    }

    @Override
    protected void appendAdditionalTestArgs(List<String> command) {
        command.add("--");
        command.add("--nocapture");
    }

    @Override
    public List<String> buildCompileCommand() {
        return List.of("cargo", "check", "--message-format=short");
    }
}