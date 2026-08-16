package athena.coder.ai.tool.dependency;

import athena.coder.ai.tool.base.ToolResult;
import athena.coder.ai.util.ProjectTypeUtil;

import java.util.ArrayList;
import java.util.List;

public class GoDependencyStrategy extends AbstractDependencyStrategy {

    public GoDependencyStrategy(DependencyStrategyFactory factory) {
        super(factory, DEFAULT_TIMEOUT);
    }

    @Override
    protected String getStrategyName() {
        return "GO";
    }

    @Override
    public String getProjectType() {
        return ProjectTypeUtil.GO;
    }

    @Override
    public ToolResult addDependency(Dependency dep) {
        try {
            String fullModule = dep.getArtifactId() +
                    (dep.getVersion() != null && !dep.getVersion().isBlank() ? "@" + dep.getVersion() : "");

            List<String> command = List.of("go", "get", fullModule);
            String rawResult = factory.executeToolCommand(command, factory.getWorkDirectory(), timeout);
            CommandResult result = factory.parseToCommandResult(rawResult);

            if (result.isSuccess()) {
                return ToolResult.success(
                        String.format("已添加 Go 依赖: %s", fullModule));
            } else {
                return ToolResult.error("go get 失败:\n" + result.error());
            }
        } catch (Exception e) {
            return ToolResult.error("添加Go依赖失败", e);
        }
    }

    @Override
    protected List<String> buildListCommand(boolean transitive) {
        return List.of("go", "list", "-m", "all");
    }

    @Override
    public ToolResult securityAudit() {
        try {
            if (factory.checkCommandExists("govulncheck")) {
                return executeSecurityAudit(List.of("govulncheck", "./..."));
            } else {
                return ToolResult.warn("未检测到 govulncheck 命令，建议安装专业扫描工具");
            }
        } catch (Exception e) {
            return ToolResult.error("安全审计失败", e);
        }
    }

    @Override
    protected List<List<String>> buildUpgradeCommands(String packageName, String targetVersion, boolean testAfterUpgrade) {
        List<List<String>> commands = new ArrayList<>();
        commands.add(List.of("go", "get", packageName + "@" + targetVersion));
        if (testAfterUpgrade) {
            commands.add(List.of("go", "test", "./..."));
        }
        return commands;
    }
}