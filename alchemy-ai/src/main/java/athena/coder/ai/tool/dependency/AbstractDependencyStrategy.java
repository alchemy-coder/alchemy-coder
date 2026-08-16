package athena.coder.ai.tool.dependency;

import athena.coder.ai.tool.base.ToolResult;
import athena.coder.ai.spi.ErrorLogger;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class AbstractDependencyStrategy implements DependencyStrategy {

    protected static final Logger LOG = Logger.getLogger(AbstractDependencyStrategy.class.getName());

    protected static final int DEFAULT_TIMEOUT = 120;

    protected final DependencyStrategyFactory factory;
    protected final int timeout;

    protected AbstractDependencyStrategy(DependencyStrategyFactory factory, int timeout) {
        this.factory = factory;
        this.timeout = timeout;
    }

    protected abstract String getStrategyName();

    @Override
    public abstract String getProjectType();

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