package athena.coder.ai.tool.strategy;

import java.util.List;

public class MavenCommandBuilder extends AbstractCommandBuilder {

    public MavenCommandBuilder() {
        super("maven");
    }

    @Override
    protected String getBaseCommand() {
        return getExecutable("mvn.cmd", "mvn");
    }

    @Override
    protected void addTestFilterArgument(List<String> command, String filter) {
        command.add("-Dtest=" + filter);
    }

    @Override
    public List<String> buildCompileCommand() {
        return List.of(getBaseCommand(), "compile");
    }

    @Override
    public List<String> buildCoverageCommand() {
        return List.of(getBaseCommand(), "jacoco:report");
    }
}