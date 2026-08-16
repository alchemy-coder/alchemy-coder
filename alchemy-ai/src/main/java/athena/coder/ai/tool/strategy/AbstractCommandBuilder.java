package athena.coder.ai.tool.strategy;

import athena.coder.ai.tool.AbstractBaseTool;

import java.util.ArrayList;
import java.util.List;

public abstract class AbstractCommandBuilder implements CommandBuilderStrategy {

    private final String projectType;

    protected AbstractCommandBuilder(String projectType) {
        this.projectType = projectType.toLowerCase();
    }

    protected static String getExecutable(String windowsCmd, String unixCmd) {
        return AbstractBaseTool.isWindows() ? windowsCmd : unixCmd;
    }

    @Override
    public String getProjectType() {
        return this.projectType;
    }

    @Override
    public List<String> buildDiagnosticsCommand() {
        return buildCompileCommand();
    }

    @Override
    public final List<String> buildTestCommand(String testFilter) {
        List<String> command = new ArrayList<>();
        command.add(getBaseCommand());
        command.add(getTestSubcommand());

        if (testFilter != null && !testFilter.isBlank()) {
            addTestFilterArgument(command, testFilter);
        }

        appendAdditionalTestArgs(command);
        return command;
    }

    protected abstract String getBaseCommand();

    protected abstract void addTestFilterArgument(List<String> command, String filter);

    protected void appendAdditionalTestArgs(List<String> command) {
    }

    protected String getTestSubcommand() {
        return "test";
    }
}