package athena.coder.ai.tool.strategy;

import athena.coder.ai.util.ProjectType;

import java.util.List;

public class NodeCommandBuilder extends AbstractCommandBuilder {

    public NodeCommandBuilder() {
        super("node");
    }

    @Override
    protected String getBaseCommand() {
        return ProjectType.NODE.executable();
    }

    @Override
    protected void addTestFilterArgument(List<String> command, String filter) {
        command.add("--");
        command.add("--grep");
        command.add(filter);
    }
}