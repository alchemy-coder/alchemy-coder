package athena.coder.ai.tool.dependency;

import athena.coder.ai.tool.base.ToolConstants;
import athena.coder.ai.tool.base.ToolResult;
import athena.coder.ai.spi.ErrorLogger;

import java.util.List;

public abstract class AbstractDependencyStrategy implements DependencyStrategy {

    protected static final int DEFAULT_TIMEOUT = 120;

    protected final DependencyStrategyFactory factory;
    protected final int timeout;
    private final String projectType;

    protected AbstractDependencyStrategy(DependencyStrategyFactory factory, int timeout, String projectType) {
        this.factory = factory;
        this.timeout = timeout;
        this.projectType = projectType;
    }

    protected String getStrategyName() {
        return projectType.toUpperCase();
    }

    @Override
    public String getProjectType() {
        return projectType;
    }

    /**
     * 命令式 addDependency 的通用执行：执行命令 → 解析结果 → 成功/失败包装。
     * 供 Go/Npm/Rust 等策略复用，消除重复的 execute/parse/error 样板。
     */
    protected ToolResult runInstallCommand(List<String> command, String successMessage, String commandLabel) {
        try {
            String rawResult = factory.executeToolCommand(command, factory.getWorkDirectory(), timeout);
            ToolConstants.CommandResult result = factory.parseToCommandResult(rawResult);
            if (result.isSuccess()) {
                return ToolResult.success(successMessage);
            }
            return ToolResult.error(commandLabel + " 失败:\n" + result.body());
        } catch (Exception e) {
            return ToolResult.error("添加" + getStrategyName() + "依赖失败", e);
        }
    }

    @Override
    public ToolResult listDependencies(boolean transitive) {
        try {
            List<String> command = buildListCommand(transitive);
            return factory.executeListDependencies(command, timeout, getStrategyName());
        } catch (Exception e) {
            ErrorLogger.log(getStrategyName() + ".listDependencies", e);
            return ToolResult.error("列出" + getStrategyName() + "依赖失败", e);
        }
    }

    protected abstract List<String> buildListCommand(boolean transitive);

    @Override
    public ToolResult securityAudit() {
        return ToolResult.warn(getStrategyName() + " 项目建议使用专业安全审计工具进行扫描");
    }

    protected ToolResult executeSecurityAudit(List<String> command) {
        try {
            return factory.executeSecurityAudit(command, timeout, null);
        } catch (Exception e) {
            ErrorLogger.log(getStrategyName() + ".securityAudit", e);
            return ToolResult.error("安全审计失败", e);
        }
    }

    @Override
    public ToolResult upgrade(String packageName, String targetVersion, boolean testAfterUpgrade) {
        try {
            List<List<String>> commands = buildUpgradeCommands(packageName, targetVersion, testAfterUpgrade);
            return factory.executeUpgradeCommands(commands, timeout, packageName, targetVersion, testAfterUpgrade);
        } catch (Exception e) {
            ErrorLogger.log(getStrategyName() + ".upgrade", e);
            return ToolResult.error("升级" + getStrategyName() + "依赖失败", e);
        }
    }

    protected abstract List<List<String>> buildUpgradeCommands(String packageName, String targetVersion,
                                                               boolean testAfterUpgrade);

}
