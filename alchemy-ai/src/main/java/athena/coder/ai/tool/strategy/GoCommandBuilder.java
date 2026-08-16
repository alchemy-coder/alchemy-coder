package athena.coder.ai.tool.strategy;

import athena.coder.ai.util.ProjectType;

import java.util.List;

public class GoCommandBuilder extends AbstractCommandBuilder {

    public GoCommandBuilder() {
        super("go");
    }

    @Override
    protected String getBaseCommand() {
        return ProjectType.GO.executable();
    }

    @Override
    protected void addTestFilterArgument(List<String> command, String filter) {
        command.add("-run");
        command.add(filter);
    }

    @Override
    protected void appendAdditionalTestArgs(List<String> command) {
        command.add("-v");
        command.add("./...");
    }

    @Override
    public List<String> buildCompileCommand() {
        return List.of("go", "build", "./...");
    }

    @Override
    public List<String> buildCoverageCommand() {
        return List.of("go", "test", "-coverprofile=coverage.out", "./...");
    }
}