package athena.coder.ai.tool.dependency;

import athena.coder.ai.tool.base.ToolResult;
import athena.coder.ai.util.ProjectTypeUtil;

import java.util.ArrayList;
import java.util.List;

public class NpmDependencyStrategy extends AbstractDependencyStrategy {

    public NpmDependencyStrategy(DependencyStrategyFactory factory) {
        super(factory, DEFAULT_TIMEOUT);
    }

    @Override
    protected String getStrategyName() {
        return "NPM";
    }

    @Override
    public String getProjectType() {
        return ProjectTypeUtil.NODE;
    }

    @Override
    public ToolResult addDependency(Dependency dep) {
        try {
            List<String> command = new ArrayList<>(List.of("npm", "install"));

            if ("dev".equalsIgnoreCase(dep.getScope()) || "optional".equalsIgnoreCase(dep.getScope())) {
                command.add("--save-dev");
            }

            String packageSpec = dep.getArtifactId() +
                    (dep.getVersion() != null && !dep.getVersion().isBlank() ? "@" + dep.getVersion() : "");
            command.add(packageSpec);

            String rawResult = factory.executeToolCommand(command, factory.getWorkDirectory(), timeout);
            CommandResult result = factory.parseToCommandResult(rawResult);

            if (result.isSuccess()) {
                return ToolResult.success(
                        String.format("已添加 npm 依赖: %s", packageSpec));
            } else {
                return ToolResult.error("npm install 失败:\n" + result.error());
            }
        } catch (Exception e) {
            return ToolResult.error("添加npm依赖失败", e);
        }
    }

    @Override
    protected List<String> buildListCommand(boolean transitive) {
        return List.of("npm", "ls", transitive ? "--all" : "--depth=0");
    }

    @Override
    public ToolResult securityAudit() {
        try {
            return executeSecurityAudit(
                    List.of("npm", "audit"));
        } catch (Exception e) {
            return ToolResult.error("安全审计失败", e);
        }
    }

    @Override
    protected List<List<String>> buildUpgradeCommands(String packageName, String targetVersion, boolean testAfterUpgrade) {
        List<List<String>> commands = new ArrayList<>();
        commands.add(List.of("npm", "install", packageName + "@" + targetVersion));
        if (testAfterUpgrade) {
            commands.add(List.of("npm", "test"));
        }
        return commands;
    }
}