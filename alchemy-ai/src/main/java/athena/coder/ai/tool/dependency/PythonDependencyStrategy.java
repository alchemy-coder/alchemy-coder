package athena.coder.ai.tool.dependency;

import athena.coder.ai.tool.base.ToolResult;
import athena.coder.ai.util.ProjectTypeUtil;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class PythonDependencyStrategy extends AbstractDependencyStrategy {

    public PythonDependencyStrategy(DependencyStrategyFactory factory) {
        super(factory, DEFAULT_TIMEOUT);
    }

    @Override
    protected String getStrategyName() {
        return "PYTHON";
    }

    @Override
    public String getProjectType() {
        return ProjectTypeUtil.PYTHON;
    }

    @Override
    public ToolResult addDependency(Dependency dep) {
        try {
            String fullPackage = dep.getArtifactId() +
                    (dep.getVersion() != null && !dep.getVersion().isBlank() ? "==" + dep.getVersion() : "");

            List<String> command = List.of("pip", "install", fullPackage);
            String rawResult = factory.executeToolCommand(command, factory.getWorkDirectory(), timeout);
            CommandResult result = factory.parseToCommandResult(rawResult);

            if (result.isSuccess()) {
                Path reqFile = factory.getWorkDirectory().resolve("requirements.txt");
                if (Files.exists(reqFile)) {
                    String content = Files.readString(reqFile);
                    if (!content.contains(dep.getArtifactId())) {
                        Files.writeString(reqFile, content + "\n" + fullPackage + "\n");
                    }
                }
                return ToolResult.success(
                        String.format("已添加 Python 依赖: %s", fullPackage));
            } else {
                return ToolResult.error("pip install 失败:\n" + result.error());
            }
        } catch (Exception e) {
            return ToolResult.error("添加Python依赖失败", e);
        }
    }

    @Override
    protected List<String> buildListCommand(boolean transitive) {
        return List.of("pip", "list");
    }

    @Override
    public ToolResult securityAudit() {
        try {
            if (factory.checkCommandExists("pip-audit")) {
                return executeSecurityAudit(List.of("pip-audit"));
            } else {
                return executeSecurityAudit(List.of("pip", "list", "--outdated"));
            }
        } catch (Exception e) {
            return ToolResult.error("安全审计失败", e);
        }
    }

    @Override
    protected List<List<String>> buildUpgradeCommands(String packageName, String targetVersion, boolean testAfterUpgrade) {
        List<List<String>> commands = new ArrayList<>();
        commands.add(List.of("pip", "install", packageName + "==" + targetVersion));
        if (testAfterUpgrade) {
            commands.add(List.of("pytest"));
        }
        return commands;
    }
}