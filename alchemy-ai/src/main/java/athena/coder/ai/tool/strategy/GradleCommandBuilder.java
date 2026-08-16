package athena.coder.ai.tool.strategy;

import java.util.List;

public class GradleCommandBuilder extends AbstractCommandBuilder {

    public GradleCommandBuilder() {
        super("gradle");
    }

    @Override
    protected String getBaseCommand() {
        return getExecutable("gradlew.bat", "./gradlew");
    }

    @Override
    protected void addTestFilterArgument(List<String> command, String filter) {
        command.add("--tests");
        command.add(filter);
    }

    @Override
    protected void appendAdditionalTestArgs(List<String> command) {
        command.add("--console=plain");
    }

    @Override
    public List<String> buildCompileCommand() {
        return List.of(getBaseCommand(), "compileJava", "--console=plain");
    }

    @Override
    public List<String> buildCoverageCommand() {
        return List.of(getBaseCommand(), "jacocoTestReport", "--console=plain");
    }
}