package athena.coder.ai.tool.util;

import athena.coder.ai.tool.strategy.CommandBuilderFactory;
import athena.coder.ai.tool.strategy.CommandBuilderStrategy;
import athena.coder.ai.util.ProjectTypeUtil;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public final class ProjectContextHelper {

    private static final Logger LOG = Logger.getLogger(ProjectContextHelper.class.getName());

    private static final Map<Path, String> projectTypeCache = new ConcurrentHashMap<>();
    private static final Map<Path, CommandBuilderStrategy> strategyCache = new ConcurrentHashMap<>();

    private ProjectContextHelper() {
    }

    public static String detectProjectType(Path workDir) {
        Path absolutePath = workDir.toAbsolutePath().normalize();
        return projectTypeCache.computeIfAbsent(absolutePath, key -> {
            LOG.fine("检测项目类型: " + key);
            return ProjectTypeUtil.detect(key);
        });
    }

    public static CommandBuilderStrategy getCommandStrategy(Path workDir) {
        Path absolutePath = workDir.toAbsolutePath().normalize();
        return strategyCache.computeIfAbsent(absolutePath, key -> {
            String type = detectProjectType(key);
            LOG.fine("获取构建策略: " + key + " [" + type + "]");
            return CommandBuilderFactory.getStrategy(type);
        });
    }
}