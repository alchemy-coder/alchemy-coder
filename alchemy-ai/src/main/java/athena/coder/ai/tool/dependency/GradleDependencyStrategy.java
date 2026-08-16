package athena.coder.ai.tool.dependency;

import athena.coder.ai.tool.base.ToolResult;
import athena.coder.ai.util.ProjectTypeUtil;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class GradleDependencyStrategy extends AbstractDependencyStrategy {

    public GradleDependencyStrategy(DependencyStrategyFactory factory) {
        super(factory, DEFAULT_TIMEOUT);
    }

    @Override
    protected String getStrategyName() {
        return "GRADLE";
    }

    @Override
    public String getProjectType() {
        return ProjectTypeUtil.GRADLE;
    }

    @Override
    public ToolResult addDependency(Dependency dep) {
        try {
            Path workDir = factory.getWorkDirectory();
            Path gradleFile = findGradleBuildFile(workDir);

            if (gradleFile == null) {
                return ToolResult.error("未找到 build.gradle / build.gradle.kts");
            }

            String content = Files.readString(gradleFile);
            boolean isKotlinDsl = gradleFile.toString().endsWith(".kts");

            String depDeclaration = formatGradleDependency(dep, isKotlinDsl);

            String dependenciesBlock = "dependencies {";
            int depIndex = content.indexOf(dependenciesBlock);
            if (depIndex == -1) {
                return ToolResult.error("build 文件中未找到 dependencies 块");
            }

            int insertPos = depIndex + dependenciesBlock.length();
            String newContent = content.substring(0, insertPos) +
                    "\n    " + depDeclaration +
                    content.substring(insertPos);

            Files.writeString(gradleFile, newContent);

            return ToolResult.success(
                    String.format("已添加 Gradle 依赖: %s", dep.getCoordinates()));

        } catch (Exception e) {
            return ToolResult.error("添加Gradle依赖失败", e);
        }
    }

    @Override
    protected List<String> buildListCommand(boolean transitive) {
        String gradleCmd = factory.isWindowsPlatform() ? "gradle.bat" : "gradle";
        if (transitive) {
            return List.of(gradleCmd, "dependencies");
        }
        return List.of(gradleCmd, "dependencies", "--configuration", "compileClasspath");
    }

    @Override
    public ToolResult securityAudit() {
        return ToolResult.warn(
                "Gradle 项目建议使用 gradle-dependency-check 插件进行审计",
                "运行命令: ./gradlew dependencyCheckAnalyze");
    }

    @Override
    protected List<List<String>> buildUpgradeCommands(String packageName, String targetVersion, boolean testAfterUpgrade) {
        List<List<String>> commands = new ArrayList<>();
        commands.add(List.of(
                factory.isWindowsPlatform() ? "gradle.bat" : "gradle",
                "upgradeDependency",
                "--dependency " + packageName + ":" + targetVersion
        ));
        if (testAfterUpgrade) {
            commands.add(List.of(factory.isWindowsPlatform() ? "gradle.bat" : "gradle", "test"));
        }
        return commands;
    }

    private String formatGradleDependency(Dependency dep, boolean isKotlinDsl) {
        String coord = dep.getGroupId() + ":" + dep.getArtifactId() +
                (dep.getVersion() != null && !dep.getVersion().isBlank() ? ":" + dep.getVersion() : "");
        boolean hasScope = dep.getScope() != null &&
                !dep.getScope().isBlank() &&
                !"compile".equals(dep.getScope());

        if (isKotlinDsl) {
            return hasScope ?
                    String.format("implementation(\"%s\", \"%s\")", coord, dep.getScope()) :
                    String.format("implementation(\"%s\")", coord);
        } else {
            return hasScope ?
                    String.format("implementation '%s' // scope: %s", coord, dep.getScope()) :
                    String.format("implementation '%s'", coord);
        }
    }

    private Path findGradleBuildFile(Path workDir) {
        Path kts = workDir.resolve("build.gradle.kts");
        if (Files.exists(kts)) return kts;
        Path groovy = workDir.resolve("build.gradle");
        return Files.exists(groovy) ? groovy : null;
    }
}