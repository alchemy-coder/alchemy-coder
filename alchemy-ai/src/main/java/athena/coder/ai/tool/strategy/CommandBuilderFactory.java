package athena.coder.ai.tool.strategy;

import athena.coder.ai.tool.exception.ToolValidationException;
import athena.coder.ai.tool.exception.ErrorCode;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public class CommandBuilderFactory {

    private static final Logger LOG = Logger.getLogger(CommandBuilderFactory.class.getName());

    private static final Map<String, CommandBuilderStrategy> STRATEGIES = new HashMap<>();

    static {
        registerStrategy(new MavenCommandBuilder());
        registerStrategy(new GradleCommandBuilder());
        registerStrategy(new GoCommandBuilder());
        registerStrategy(new RustCommandBuilder());
        registerStrategy(new PythonCommandBuilder());
        registerStrategy(new NodeCommandBuilder());

        LOG.info("命令构建策略工厂初始化完成，已注册 " + STRATEGIES.size() + " 种项目类型策略");
    }

    private static void registerStrategy(CommandBuilderStrategy strategy) {
        STRATEGIES.put(strategy.getProjectType().toLowerCase(), strategy);
    }

    public static CommandBuilderStrategy getStrategy(String projectType) {
        if (projectType == null || projectType.isBlank()) {
            throw new ToolValidationException("CommandBuilderFactory", ErrorCode.PARAM_MISSING, "projectType");
        }

        CommandBuilderStrategy strategy = STRATEGIES.get(projectType.toLowerCase());
        if (strategy == null) {
            throw new ToolValidationException("CommandBuilderFactory", ErrorCode.UNSUPPORTED_TYPE,
                    projectType + "\n支持的项目类型: " + String.join(", ", STRATEGIES.keySet()));
        }

        return strategy;
    }
}