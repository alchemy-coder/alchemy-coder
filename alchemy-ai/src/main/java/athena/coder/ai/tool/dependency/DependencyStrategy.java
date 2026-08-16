package athena.coder.ai.tool.dependency;

import athena.coder.ai.tool.base.ToolResult;

public interface DependencyStrategy {

    String getProjectType();

    ToolResult addDependency(Dependency dependency);

    ToolResult listDependencies(boolean transitive);

    ToolResult securityAudit();

    ToolResult upgrade(String packageName, String targetVersion, boolean testAfterUpgrade);

}