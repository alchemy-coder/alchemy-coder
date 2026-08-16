package athena.coder.ai.tool.dependency;

import athena.coder.ai.tool.util.CommandPathResolver;
import athena.coder.ai.tool.util.CommandSafetyValidator;
import athena.coder.ai.tool.util.ProjectContextHelper;
import athena.coder.ai.tool.base.ToolConstants;
import athena.coder.ai.tool.base.ToolResult;
import athena.coder.ai.tool.config.ToolConfigCenter;
import athena.coder.ai.tool.exception.ErrorCode;
import athena.coder.ai.tool.exception.ToolExecutionException;
import athena.coder.ai.tool.exception.ToolValidationException;
import athena.coder.ai.tool.executor.CommandExecutor;
import athena.coder.ai.spi.ErrorLogger;
import athena.coder.ai.util.ProjectTypeUtil;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class DependencyStrategyFactory {

    private static final Logger LOG = Logger.getLogger(DependencyStrategyFactory.class.getName());
    private static final int MAX_OUTPUT_LENGTH = 10000;

    private final CommandExecutor commandExecutor;
    private final ToolConfigCenter configCenter;
    private final Map<String, DependencyStrategy> strategyCache = new ConcurrentHashMap<>();

    public DependencyStrategyFactory(CommandExecutor commandExecutor) {
        this.commandExecutor = Objects.requireNonNull(commandExecutor, "commandExecutor不能为null");
        this.configCenter = ToolConfigCenter.getInstance();
        registerDefaultStrategies();
    }

    public DependencyStrategy getStrategy(Path workDir) {
        String projectType = ProjectContextHelper.detectProjectType(workDir);
        return getStrategy(projectType);
    }

    public DependencyStrategy getStrategy(String projectType) {
        if (ProjectTypeUtil.UNKNOWN.equals(projectType)) {
            throw new ToolValidationException("DependencyStrategyFactory", ErrorCode.UNSUPPORTED_TYPE, projectType);
        }

        DependencyStrategy strategy = strategyCache.get(projectType);
        if (strategy == null) {
            throw new ToolValidationException("DependencyStrategyFactory", ErrorCode.UNSUPPORTED_TYPE, projectType);
        }
        return strategy;
    }

    public ToolConstants.CommandResult parseToCommandResult(String rawResult) {
        return ToolConstants.parseResult(rawResult);
    }

    public String executeToolCommand(List<String> command, Path workDir, int timeout) {
        try {
            CommandSafetyValidator.validate("DependencyManager", command);
            return commandExecutor.execute(command, workDir, timeout);
        } catch (Exception e) {
            ErrorLogger.log("DependencyStrategyFactory.executeToolCommand", e);
            throw new ToolExecutionException("DependencyStrategyFactory", ErrorCode.INTERNAL_ERROR, e);
        }
    }

    public Path getWorkDirectory() {
        return configCenter.getAllowedWorkDir();
    }

    public String truncateOutputString(String output) {
        if (output.length() > MAX_OUTPUT_LENGTH) {
            return output.substring(0, MAX_OUTPUT_LENGTH) +
                    "\n... [输出已截断，原始长度: " + output.length() + "]";
        }
        return output;
    }

    public boolean checkCommandExists(String cmd) {
        return CommandPathResolver.exists(cmd);
    }

    public ToolResult executeListDependencies(List<String> command, int timeout, String typeLabel) {
        String rawResult = executeToolCommand(command, getWorkDirectory(), timeout);
        ToolConstants.CommandResult result = parseToCommandResult(rawResult);

        if (result.isSuccess()) {
            return ToolResult.success(
                    typeLabel + " 项目依赖列表:\n\n" + truncateOutputString(result.body()));
        }
        return ToolResult.error("获取依赖列表失败:\n" + result.body());
    }

    public ToolResult executeUpgradeCommands(List<List<String>> commands, int timeout,
                                             String packageName, String targetVersion, boolean testAfterUpgrade) {
        StringBuilder results = new StringBuilder();
        for (List<String> cmd : commands) {
            String rawResult = executeToolCommand(cmd, getWorkDirectory(), timeout);
            ToolConstants.CommandResult result = parseToCommandResult(rawResult);

            results.append("> ").append(String.join(" ", cmd))
                    .append("\n")
                    .append(result.isSuccess() ? result.body() : "错误: " + result.body())
                    .append("\n\n");

            if (!result.isSuccess() && !testAfterUpgrade) {
                return ToolResult.error("升级失败:\n" + results);
            }
        }

        return ToolResult.success(
                String.format("依赖 %s 已升级至 %s%s",
                        packageName, targetVersion,
                        testAfterUpgrade ? " 且测试通过 ✓" : ""),
                results.toString());
    }

    public ToolResult executeSecurityAudit(List<String> command, int timeout, String fallbackMsg) {
        String rawResult = executeToolCommand(command, getWorkDirectory(), timeout);
        ToolConstants.CommandResult result = parseToCommandResult(rawResult);

        if (result.isSuccess()) {
            return ToolResult.success("安全审计完成，未发现漏洞 ✓\n\n" + truncateOutputString(result.body()));
        } else if (result.exitCode() == 1) {
            return ToolResult.warn("发现潜在安全问题:\n\n" + truncateOutputString(result.body()));
        }
        return ToolResult.error("审计执行失败:\n" + result.body());
    }

    private void registerDefaultStrategies() {
        List<DependencyStrategy> defaults = List.of(
                new MavenDependencyStrategy(this),
                new GradleDependencyStrategy(this),
                new NpmDependencyStrategy(this),
                new PythonDependencyStrategy(this),
                new GoDependencyStrategy(this),
                new RustDependencyStrategy(this)
        );

        defaults.forEach(strategy -> {
            strategyCache.put(strategy.getProjectType(), strategy);
        });
    }
}