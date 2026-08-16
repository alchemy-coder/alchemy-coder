package athena.coder.ai.tool.strategy;

import java.util.List;

public class PythonCommandBuilder extends AbstractCommandBuilder {

    public PythonCommandBuilder() {
        super("python");
    }

    @Override
    protected String getBaseCommand() {
        return getExecutable("python", "python3");
    }

    @Override
    protected String getTestSubcommand() {
        return "-m";
    }

    @Override
    protected void addTestFilterArgument(List<String> command, String filter) {
        if (!command.contains("pytest")) {
            command.add("pytest");
        }
        command.add("-k");
        command.add(filter);
    }

    @Override
    protected void appendAdditionalTestArgs(List<String> command) {
        if (!command.contains("-v")) {
            command.add("-v");
        }
    }

    @Override
    public List<String> buildCompileCommand() {
        return List.of(getBaseCommand(), "-m", "compileall", ".");
    }

    @Override
    public List<String> buildDiagnosticsCommand() {
        return List.of(getBaseCommand(), "-m", "pyflakes", ".");
    }

    @Override
    public List<String> buildCoverageCommand() {
        return List.of(getBaseCommand(), "-m", "pytest", "--cov=.", "--cov-report=term");
    }
}