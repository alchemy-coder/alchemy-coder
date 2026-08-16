package athena.coder.ai.tool;

import athena.coder.ai.tool.base.ProcessBasedTool;
import athena.coder.ai.tool.base.ToolResult;
import athena.coder.ai.tool.dependency.*;
import athena.coder.ai.tool.exception.*;
import athena.coder.ai.tool.validation.NotBlank;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

import java.nio.file.Path;

/**
 * 依赖管理工具
 * 统一管理多语言项目的依赖操作
 * <p>
 * 特性：
 * - 继承 ProcessBasedTool 的命令执行能力
 * - 统一异常处理
 * - 策略模式支持多项目类型
 * - 安全审计能力
 */
public class DependencyManagerTool extends ProcessBasedTool {

    private final DependencyStrategyFactory strategyFactory;
    private final DependencyParser parser;

    public DependencyManagerTool() {
        super();
        this.strategyFactory = new DependencyStrategyFactory(executor);
        this.parser = new DependencyParser();
    }

    @Tool("添加项目依赖，自动检测项目类型并写入正确的配置文件")
    public String addDependency(
            @NotBlank(fieldName = "依赖标识符") @P("依赖标识符") String dependency,
            @P("版本号（可选）") String version,
            @P("作用域：compile/test/provided/runtime/dev/optional（可选）") String scope) {

        return executeWithAutoValidation(() -> {
            Path workDir = getAllowedWorkDir();
            Dependency dep = parser.parse(dependency, version, scope);
            DependencyStrategy strategy = strategyFactory.getStrategy(workDir);

            logInfo("添加依赖: " + dep.getCoordinates() +
                    " [" + strategy.getClass().getSimpleName() + "]");

            ToolResult result = strategy.addDependency(dep);
            return result.toDisplayString();
        }, "addDependency", dependency, version, scope);
    }

    @Tool("列出项目所有依赖及其版本信息")
    public String listDependencies(
            @P("是否包含传递依赖") boolean includeTransitive) {

        return executeSafely(() -> {
            Path workDir = getAllowedWorkDir();
            DependencyStrategy strategy = strategyFactory.getStrategy(workDir);

            logInfo("列出依赖 [包含传递=" + includeTransitive + "]");

            ToolResult result = strategy.listDependencies(includeTransitive);
            return result.toDisplayString();
        }, "listDependencies");
    }

    @Tool("检查依赖是否存在已知CVE安全漏洞")
    public String securityAudit() {

        return executeSafely(() -> {
            Path workDir = getAllowedWorkDir();
            DependencyStrategy strategy = strategyFactory.getStrategy(workDir);

            logInfo("执行安全审计");

            ToolResult result = strategy.securityAudit();
            return result.toDisplayString();
        }, "securityAudit");
    }

    @Tool("升级指定依赖到目标版本并验证兼容性")
    public String upgrade(
            @NotBlank(fieldName = "依赖名称") @P("依赖名称或坐标") String packageName,
            @NotBlank(fieldName = "目标版本") @P("目标版本") String targetVersion,
            @P("是否运行测试验证兼容性") boolean testAfterUpgrade) {

        return executeWithAutoValidation(() -> {
            Path workDir = getAllowedWorkDir();
            DependencyStrategy strategy = strategyFactory.getStrategy(workDir);

            logInfo("升级依赖: " + packageName + " → " + targetVersion +
                    " [测试=" + testAfterUpgrade + "]");

            ToolResult result = strategy.upgrade(packageName, targetVersion, testAfterUpgrade);
            return result.toDisplayString();
        }, "upgrade", packageName, targetVersion, testAfterUpgrade);
    }
}