package athena.coder.ai.tool.dependency;

import athena.coder.ai.tool.base.ToolResult;
import athena.coder.ai.util.ProjectTypeUtil;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class MavenDependencyStrategy extends AbstractDependencyStrategy {

    public MavenDependencyStrategy(DependencyStrategyFactory factory) {
        super(factory, DEFAULT_TIMEOUT);
    }

    @Override
    protected String getStrategyName() {
        return "MAVEN";
    }

    @Override
    public String getProjectType() {
        return ProjectTypeUtil.MAVEN;
    }

    @Override
    public ToolResult addDependency(Dependency dep) {
        try {
            Path workDir = factory.getWorkDirectory();
            Path pomPath = workDir.resolve("pom.xml");

            if (!Files.exists(pomPath)) {
                return ToolResult.error("pom.xml 不存在");
            }

            String content = Files.readString(pomPath);
            String depXml = formatMavenDependencyXml(dep);

            int insertIndex = findDependenciesInsertPosition(content);
            if (insertIndex == -1) {
                return ToolResult.error("pom.xml 中未找到 <dependencies>");
            }

            String newContent = content.substring(0, insertIndex) +
                    "\n" + depXml +
                    content.substring(insertIndex);

            Files.writeString(pomPath, newContent);

            return ToolResult.success(
                    String.format("已添加 Maven 依赖: %s (scope: %s)",
                            dep.getCoordinates(), dep.getScope()));

        } catch (Exception e) {
            return ToolResult.error("添加Maven依赖失败", e);
        }
    }

    @Override
    protected List<String> buildListCommand(boolean transitive) {
        return List.of(factory.isWindowsPlatform() ? "mvn.cmd" : "mvn", "dependency:tree");
    }

    @Override
    public ToolResult securityAudit() {
        return ToolResult.warn(
                "Maven 项目建议使用 mvn dependency-check 或 OWASP 插件进行审计",
                "运行命令: mvn org.owasp:dependency-check-maven:check");
    }

    @Override
    protected List<List<String>> buildUpgradeCommands(String packageName, String targetVersion, boolean testAfterUpgrade) {
        List<List<String>> commands = new ArrayList<>();
        commands.add(List.of(
                factory.isWindowsPlatform() ? "mvn.cmd" : "mvn",
                "versions:use-dep-version",
                "-Dincludes=" + packageName,
                "-DdepVersion=" + targetVersion
        ));
        if (testAfterUpgrade) {
            commands.add(List.of(factory.isWindowsPlatform() ? "mvn.cmd" : "mvn", "test"));
        }
        return commands;
    }

    private String formatMavenDependencyXml(Dependency dep) {
        return String.format("""
                        <dependency>
                            <groupId>%s</groupId>
                            <artifactId>%s</artifactId>
                            %s
                            <scope>%s</scope>
                        </dependency>""",
                dep.getGroupId(),
                dep.getArtifactId(),
                dep.getVersion() != null && !dep.getVersion().isBlank() ?
                        "<version>" + dep.getVersion() + "</version>" : "",
                dep.getScope()
        );
    }

    private int findDependenciesInsertPosition(String content) {
        int index = content.indexOf("<dependencies>");
        return index == -1 ? -1 : index + "<dependencies>".length();
    }
}