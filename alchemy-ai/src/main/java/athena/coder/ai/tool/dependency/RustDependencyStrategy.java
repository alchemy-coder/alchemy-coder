package athena.coder.ai.tool.dependency;

import athena.coder.ai.tool.base.ToolResult;
import athena.coder.ai.util.ProjectTypeUtil;

import java.util.ArrayList;
import java.util.List;

public class RustDependencyStrategy extends AbstractDependencyStrategy {

    public RustDependencyStrategy(DependencyStrategyFactory factory) {
        super(factory, DEFAULT_TIMEOUT);
    }

    @Override
    protected String getStrategyName() {
        return "RUST";
    }

    @Override
    public String getProjectType() {
        return ProjectTypeUtil.RUST;
    }

    @Override
    public ToolResult addDependency(Dependency dep) {
        try {
            List<String> command = new ArrayList<>(List.of("cargo", "add", dep.getArtifactId()));

            if (dep.getVersion() != null && !dep.getVersion().isBlank()) {
                command.add("--vers");
                command.add(dep.getVersion());
            }

            String rawResult = factory.executeToolCommand(command, factory.getWorkDirectory(), timeout);
            CommandResult result = factory.parseToCommandResult(rawResult);

            if (result.isSuccess()) {
                return ToolResult.success(
                        String.format("已添加 Rust 依赖: %s%s",
                                dep.getArtifactId(),
                                dep.getVersion() != null ? "@" + dep.getVersion() : ""));
            } else {
                return ToolResult.error("cargo add 失败:\n" + result.error());
            }
        } catch (Exception e) {
            return ToolResult.error("添加Rust依赖失败", e);
        }
    }

    @Override
    protected List<String> buildListCommand(boolean transitive) {
        return List.of("cargo", "tree", "--depth", transitive ? "∞" : "1");
    }

    @Override
    public ToolResult securityAudit() {
        try {
            if (factory.checkCommandExists("cargo-audit")) {
                return executeSecurityAudit(List.of("cargo", "audit"));
            } else {
                return ToolResult.warn("未检测到 cargo-audit 命令，建议安装专业扫描工具");
            }
        } catch (Exception e) {
            return ToolResult.error("安全审计失败", e);
        }
    }

    @Override
    protected List<List<String>> buildUpgradeCommands(String packageName, String targetVersion, boolean testAfterUpgrade) {
        List<List<String>> commands = new ArrayList<>();
        commands.add(List.of("cargo", "update", packageName, "--precise", targetVersion));
        if (testAfterUpgrade) {
            commands.add(List.of("cargo", "test"));
        }
        return commands;
    }
}