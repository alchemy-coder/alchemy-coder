package athena.coder.ai.tool;

import athena.coder.ai.tool.base.ProcessBasedTool;
import athena.coder.ai.tool.util.ProjectContextHelper;
import athena.coder.ai.tool.exception.ErrorCode;
import athena.coder.ai.tool.exception.ToolValidationException;
import athena.coder.ai.tool.strategy.CommandBuilderStrategy;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 测试执行工具
 * 运行项目测试并返回结构化结果，让 AI 能验证代码正确性
 * <p>
 * 特性：
 * - 继承 ProcessBasedTool 的命令执行能力
 * - 统一异常处理和输出脱敏
 * - 动态超时配置
 * - 多项目类型支持（通过 ProjectContextHelper）
 */
public class TestExecutionTool extends ProcessBasedTool {

    private static final String[] COVERAGE_REPORT_PATHS = {
            "target/site/jacoco.csv",
            "build/reports/jacoco/test/jacocoTestReport.csv",
            "coverage.out",
            "htmlcov/index.html",
            ".coverage"
    };

    /**
     * 各项目类型测试输出的关键行关键词（大小写敏感），命中即视为摘要行
     */
    private static final Map<String, String[]> SUMMARY_KEYWORDS = Map.of(
            "maven", new String[]{"Tests run:", "BUILD SUCCESS", "BUILD FAILURE"},
            "go", new String[]{"ok ", "FAIL", "---", "passed", "failed"},
            "rust", new String[]{"test result:", "running", "failures:"},
            "python", new String[]{"passed", "failed", "error", "test session", "========", "warnings"},
            "node", new String[]{"passing", "failing", "pending", "passed", "failed"}
    );

    // ==================== Tool 入口 ====================

    @Tool("运行项目的单元测试。自动检测项目类型（Maven/Gradle/Go/Rust/Python/Node.js），执行测试并返回结果。")
    public String runTests(
            @P("项目目录路径") String workingDir,
            @P("测试过滤条件，可以是测试类名（如 'UserServiceTest'）、方法名（如 'UserServiceTest#testLogin'）、或为空运行全部测试") String testFilter) {

        return executeSafely(() -> {
            Path workDir = resolveAndValidate(workingDir);

            CommandBuilderStrategy strategy = getProjectStrategy(workDir);
            List<String> command = strategy.buildTestCommand(testFilter);

            String result = executeCommand(command, workDir, getMyTimeout());
            return formatTestResult(result, ProjectContextHelper.detectProjectType(workDir));
        }, "runTests");
    }

    @Tool("运行单个测试类或测试方法，并返回详细的执行结果和错误信息。")
    public String runSingleTest(
            @P("项目目录路径") String workingDir,
            @P("测试类全限定名，如 com.example.UserServiceTest") String testClass,
            @P("测试方法名，为空则运行整个类") String testMethod) {

        return executeSafely(() -> {
            if (testClass == null || testClass.isBlank()) {
                throw new ToolValidationException(getToolName(), ErrorCode.PARAM_MISSING, "测试类名");
            }

            String filter = testClass;
            if (testMethod != null && !testMethod.isBlank()) {
                filter = testClass + "#" + testMethod;
            }

            Path workDir = resolveAndValidate(workingDir);
            CommandBuilderStrategy strategy = getProjectStrategy(workDir);
            List<String> command = strategy.buildTestCommand(filter);
            String result = executeCommand(command, workDir, getMyTimeout());
            return formatTestResult(result, ProjectContextHelper.detectProjectType(workDir));
        }, "runSingleTest");
    }

    @Tool("查看测试覆盖率报告（支持 JaCoCo/go test -cover/pytest-cov 等覆盖率工具）。")
    public String getTestCoverage(
            @P("项目目录路径") String workingDir) {

        return executeSafely(() -> {
            Path workDir = resolveAndValidate(workingDir);
            String projectType = ProjectContextHelper.detectProjectType(workDir);

            Path reportPath = findCoverageReport(workDir);
            if (reportPath != null && safeFileExists(reportPath)) {
                List<String> lines = safeReadAllLines(reportPath);
                StringBuilder result = new StringBuilder();
                result.append("覆盖率报告:\n");

                int maxLines = Math.min(50, lines.size());
                for (int i = 0; i < maxLines; i++) {
                    result.append(lines.get(i)).append("\n");
                }
                return result.toString();
            }

            CommandBuilderStrategy strategy = getProjectStrategy(workDir);
            List<String> command = strategy.buildCoverageCommand();

            String result = executeCommand(command, workDir, getMyTimeout());
            if (result.startsWith(OK_PREFIX)) {
                reportPath = findCoverageReport(workDir);
                if (reportPath != null && safeFileExists(reportPath)) {
                    return "覆盖率报告已生成:\n" + reportPath;
                }
                return "覆盖率任务执行成功，但未找到报告文件";
            } else {
                return "生成覆盖率报告失败: " + stripPrefix(result);
            }
        }, "getTestCoverage");
    }

    // ==================== 辅助方法 ====================

    private String formatTestResult(String result, String projectType) {
        if (result.startsWith(OK_PREFIX)) {
            String output = stripPrefix(result);
            String summary = extractTestSummary(output, projectType);
            return "测试通过\n" + summary;
        } else {
            return "测试失败\n" + stripPrefix(result);
        }
    }

    private String extractTestSummary(String output, String projectType) {
        String[] lines = output.split("\n");
        if (projectType == null) {
            return fallbackSummary(lines);
        }
        String type = projectType.toLowerCase();
        if ("gradle".equals(type)) {
            String summary = gradleSummary(lines);
            return summary.isEmpty() ? fallbackSummary(lines) : summary;
        }
        String[] keywords = SUMMARY_KEYWORDS.get(type);
        if (keywords != null) {
            String summary = keywordSummary(lines, keywords);
            return summary.isEmpty() ? fallbackSummary(lines) : summary;
        }
        return fallbackSummary(lines);
    }

    private static String keywordSummary(String[] lines, String[] keywords) {
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            String t = line.trim();
            for (String kw : keywords) {
                if (t.contains(kw)) {
                    sb.append(t).append("\n");
                    break;
                }
            }
        }
        return sb.toString();
    }

    private static String gradleSummary(String[] lines) {
        StringBuilder sb = new StringBuilder();
        boolean inTestSection = false;
        for (String line : lines) {
            String t = line.trim();
            if (t.contains("> Task :test") || t.contains("> Task :")) inTestSection = true;
            if (inTestSection && (t.contains("tests completed") || t.contains("tests found") ||
                    t.contains("FAILED") || t.contains("PASSED") || t.contains("BUILD SUCCESSFUL") ||
                    t.contains("BUILD FAILED"))) {
                sb.append(t).append("\n");
            }
        }
        return sb.toString();
    }

    private static String fallbackSummary(String[] lines) {
        StringBuilder sb = new StringBuilder();
        boolean hasSummary = false;
        for (String line : lines) {
            String lower = line.toLowerCase();
            if (lower.contains("tests run:") || lower.contains("test result:") ||
                    lower.contains("tests passed") || lower.contains("tests failed") ||
                    lower.contains("BUILD SUCCESS") || lower.contains("BUILD FAILURE") ||
                    ((lower.contains("failed") || lower.contains("passed")) &&
                            (lower.contains("test") || lower.contains(":")))) {
                sb.append(line.trim()).append("\n");
                hasSummary = true;
            }
        }
        if (!hasSummary) {
            int start = Math.max(0, lines.length - 20);
            for (int i = start; i < lines.length; i++) {
                sb.append(lines[i]).append("\n");
            }
        }
        return sb.toString();
    }

    private Path findCoverageReport(Path dir) {
        for (String reportPath : COVERAGE_REPORT_PATHS) {
            Path path = dir.resolve(reportPath);
            if (safeFileExists(path)) {
                return path;
            }
        }

        // 尝试查找 HTML 报告
        try (Stream<Path> walk = safeWalk(dir, 3)) {
            return walk.filter(p -> {
                        String name = p.getFileName().toString();
                        return name.equals("jacoco.csv") ||
                                name.equals("coverage.xml") ||
                                name.equals("cov.xml");
                    })
                    .findFirst()
                    .orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

}