package athena.coder.ai.tool.strategy;

import java.util.List;

public class NodeCommandBuilder extends AbstractCommandBuilder {

    public NodeCommandBuilder() {
        super("node");
    }

    @Override
    protected String getBaseCommand() {
        return "npm";
    }

    @Override
    protected void addTestFilterArgument(List<String> command, String filter) {
        command.add("--");
        command.add("--grep");
        command.add(filter);
    }

    @Override
    public List<String> buildCompileCommand() {
        return List.of("npm", "run", "build", "--if-present");
    }

    @Override
    public List<String> buildDiagnosticsCommand() {
        return List.of("npx", "tsc", "--noEmit");
    }
}