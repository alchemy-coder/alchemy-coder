package athena.coder.ai.tool;

import athena.coder.ai.tool.analyzer.CodeAnalyzerFactory;
import athena.coder.ai.tool.util.FileTraversalHelper;
import athena.coder.ai.tool.util.ProjectContextHelper;
import athena.coder.ai.tool.analyzer.CodeProblem;
import athena.coder.ai.tool.base.ProcessBasedTool;
import athena.coder.ai.tool.exception.ErrorCode;
import athena.coder.ai.tool.exception.ToolValidationException;
import athena.coder.ai.tool.strategy.CommandBuilderStrategy;
import athena.coder.ai.spi.ErrorLogger;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Stream;

public class DiagnosticTool extends ProcessBasedTool {

    private static final String[] DEFAULT_LOG_PATHS = {
            "target/surefire-reports", "target/failsafe-reports",
            "build/reports/tests/test", "build/test-results", "logs", "log"
    };

    private final Map<String, Function<Path, String>> customDiagnosticsHandlers;

    public DiagnosticTool() {
        super();
        this.customDiagnosticsHandlers = Map.of(
                "javac", this::compileWithJavac,
                "python", this::runPythonDiagnostics,
                "node", this::runNodeDiagnostics
        );
    }

    @Tool("对项目执行完整的编译/检查诊断，返回所有编译错误或静态分析问题的详细信息（文件、行号、错误类型、错误描述）。支持 Java/Go/Rust/Python/Node.js 项目。")
    public String getCompilationDiagnostics(
            @P("项目目录路径") String workingDir) {

        return executeSafely(() -> {
            Path workDir = resolveAndValidate(workingDir);
            String projectType = ProjectContextHelper.detectProjectType(workDir);

            Function<Path, String> customHandler = customDiagnosticsHandlers.get(projectType);
            if (customHandler != null) {
                return customHandler.apply(workDir);
            }

            CommandBuilderStrategy strategy = getProjectStrategy(workDir);
            List<String> command = strategy.buildDiagnosticsCommand();

            String result = executeCommand(command, workDir, getMyTimeout());

            if (result.startsWith(OK_PREFIX)) {
                return "检查通过，无错误";
            } else {
                return parseCompilationErrors(stripPrefix(result));
            }
        }, "getCompilationDiagnostics");
    }

    @Tool("获取最近一次程序运行的错误日志或异常堆栈信息。从控制台输出或日志文件中提取错误。")
    public String getRuntimeErrors(
            @P("项目目录路径") String workingDir,
            @P("可选，指定日志文件路径。为空则查看默认位置（target/surefire-reports 或 logs/ 目录）") String logFile) {

        return executeSafely(() -> {
            Path workDir = resolveAndValidate(workingDir);

            if (logFile != null && !logFile.isBlank()) {
                Path logPath = workDir.resolve(logFile);
                checkFileExists(logPath);
                return extractErrorsFromFile(logPath);
            }

            List<Path> logFiles = findLogFiles(workDir);

            if (logFiles.isEmpty()) {
                return "未找到错误日志文件。请指定日志文件路径或先运行程序/测试。";
            }

            StringBuilder result = new StringBuilder();
            result.append("找到 ").append(logFiles.size()).append(" 个日志文件:\n===\n");

            for (Path logPath : logFiles) {
                String relativePath = workDir.relativize(logPath).toString();
                result.append("\n[文件] ").append(relativePath).append(":\n");
                result.append(extractErrorsFromFile(logPath));
                result.append("\n");
            }

            return result.toString();
        }, "getRuntimeErrors");
    }

    @Tool("检查源代码文件的代码问题，包括未使用的导入、缺少注解、潜在的空指针、未使用变量、未处理错误等静态分析。支持 Java(.java)、Go(.go)、Python(.py)、Rust(.rs)、TypeScript(.ts/.tsx)、JavaScript(.js/.jsx) 文件。")
    public String analyzeCodeProblems(
            @P("源文件路径") String filePath) {

        return executeSafely(() -> {
            if (filePath == null || filePath.isBlank()) {
                throw new ToolValidationException(getToolName(), ErrorCode.PARAM_MISSING, "文件路径");
            }

            Path path = resolveAndValidate(filePath);
            checkFileExists(path);
            checkNotBinary(path);

            String ext = getFileExtensionWithDot(filePath);

            if (!CodeAnalyzerFactory.isSupportedLanguage(ext)) {
                throw new ToolValidationException(getToolName(), ErrorCode.UNSUPPORTED_TYPE, ext);
            }

            List<String> lines = safeReadAllLines(path);
            List<CodeProblem> problems = CodeAnalyzerFactory.analyze(ext, lines);

            if (problems.isEmpty()) {
                return "未发现明显的代码问题";
            }

            StringBuilder result = new StringBuilder();
            result.append("发现 ").append(problems.size()).append(" 个潜在问题:\n");
            result.append("===\n");

            problems.sort(Comparator.comparing(CodeProblem::severity));

            for (CodeProblem problem : problems) {
                result.append(problem.toString()).append("\n");
            }

            return result.toString();
        }, "analyzeCodeProblems");
    }

    private List<Path> findLogFiles(Path workDir) {
        List<Path> logFiles = new ArrayList<>();

        for (String logPath : DEFAULT_LOG_PATHS) {
            Path path = workDir.resolve(logPath);
            if (safeFileExists(path) && safeIsDirectory(path)) {
                try (Stream<Path> stream = safeList(path)) {
                    stream.filter(p -> !safeIsDirectory(p))
                            .filter(p -> {
                                String name = p.getFileName().toString().toLowerCase();
                                return name.endsWith(".log") || name.endsWith(".txt") ||
                                        name.endsWith(".xml") || name.endsWith(".json");
                            })
                            .forEach(logFiles::add);
                } catch (Exception e) {
                    logFine("无法读取日志目录: " + path);
                }
            }
        }


        if (!logFiles.isEmpty()) {
            logFiles.sort(Comparator.comparingLong(this::safeFileSize).reversed());
            if (logFiles.size() > 10) {
                logFiles = new ArrayList<>(logFiles.subList(0, 10));
            }
        }


        return logFiles;
    }

    private String extractErrorsFromFile(Path logPath) {
        try {
            List<String> lines = safeReadAllLines(logPath);
            StringBuilder errors = new StringBuilder();

            boolean inErrorBlock = false;
            int errorCount = 0;
            int maxErrors = 50;

            for (String line : lines) {
                String lowerLine = line.toLowerCase();

                boolean isErrorLine = lowerLine.contains("error") || lowerLine.contains("exception") ||
                        lowerLine.contains("failed") || lowerLine.contains("fatal");

                if (isErrorLine || inErrorBlock) {
                    if (isErrorLine) {
                        errorCount++;
                        if (errorCount > maxErrors) {
                            errors.append("\n... (更多错误已省略，共 ").append(errorCount).append(" 个错误)");
                            break;
                        }
                        errors.append("\n").append(line);
                        inErrorBlock = true;
                    } else if (line.trim().isEmpty() || line.startsWith("\t") || line.startsWith("    ")) {
                        errors.append("\n").append(line);
                    } else {
                        inErrorBlock = false;
                    }
                }
            }

            if (errors.isEmpty()) {
                return "该文件中未检测到明显的错误信息";
            }

            return errors.toString();
        } catch (Exception e) {
            return "解析日志文件时出错: " + e.getMessage();
        }
    }

    private String parseCompilationErrors(String output) {
        StringBuilder result = new StringBuilder();
        result.append("编译错误详情:\n===\n");

        String[] lines = output.split("\n");
        int errorCount = 0;

        for (String line : lines) {
            if (line.toLowerCase().contains("error:") ||
                    line.toLowerCase().contains("错误") ||
                    line.contains("[ERROR]") ||
                    line.matches(".*:\\d+: error:.*")) {

                result.append(line.trim()).append("\n");
                errorCount++;

                if (errorCount >= 100) {
                    result.append("\n... (更多错误已省略)");
                    break;
                }
            }
        }

        if (errorCount == 0) {
            result.append(output);
        } else {
            result.insert(0, "共发现 " + errorCount + " 个编译错误\n\n");
        }

        return result.toString();
    }

    private String compileWithJavac(Path dir) {
        Path tempDir;
        try {
            tempDir = Files.createTempDirectory("javac-classes");
        } catch (Exception e) {
            return "编译检查失败: 无法创建临时目录 - " + e.getMessage();
        }

        List<String> command = new ArrayList<>();
        command.add("javac");
        command.add("-d");
        command.add(tempDir.toString());
        command.add("-sourcepath");
        command.add("src/main/java");


        var files = FileTraversalHelper.findCodeFiles(dir.resolve("src"), 10, 200);
        for (Path file : files) {
            if (file.toString().endsWith(".java")) {
                command.add(file.toString());
            }
        }

        if (command.size() <= 4) {
            command.clear();
            command.addAll(Arrays.asList("mvn", "compile"));
        }

        try {
            String result = executeCommand(command, dir, getMyTimeout());
            if (result.startsWith(OK_PREFIX)) {
                return "Java 编译检查通过";
            } else {
                return parseCompilationErrors(stripPrefix(result));
            }
        } catch (Exception e) {
            return "编译检查失败: " + e.getMessage();
        }
    }

    private String runDiagnosticsWithFallback(Path dir, List<String> primaryCmd, List<String> fallbackCmd,
                                              String primaryLabel, String secondaryLabel, String fallbackErrorMsg) {
        try {
            String result = executeCommand(primaryCmd, dir, getMyTimeout());
            if (result.startsWith(OK_PREFIX)) {
                return "检查通过，无错误";
            }
            return primaryLabel + "\n" + stripPrefix(result);
        } catch (Exception e) {
            ErrorLogger.warn("DiagnosticTool." + primaryLabel, "主方案执行失败，尝试回退方案: " + e.getMessage());
            try {
                String result = executeCommand(fallbackCmd, dir, getMyTimeout());
                return secondaryLabel + "\n" + stripPrefix(result);
            } catch (Exception ex) {
                ErrorLogger.warn("DiagnosticTool." + secondaryLabel, "回退方案执行也失败: " + ex.getMessage());
                return fallbackErrorMsg;
            }
        }
    }

    private String runPythonDiagnostics(Path dir) {
        return runDiagnosticsWithFallback(
                dir,
                List.of("python3", "-m", "mypy", "--ignore-missing-imports", "."),
                List.of("python3", "-m", "pylint", "--disable=C,R", "."),
                "Mypy 静态分析结果",
                "Pylint 分析结果",
                "Python 项目检查失败: mypy 和 pylint 均不可用，请确保已安装: pip install mypy pylint");
    }

    private String runNodeDiagnostics(Path dir) {
        return runDiagnosticsWithFallback(
                dir,
                List.of("npx", "tsc", "--noEmit"),
                List.of("npx", "eslint", ".", "--format=stylish"),
                "TypeScript 类型检查结果",
                "ESLint 检查结果（TypeScript不可用）",
                "Node.js 项目检查失败: TypeScript 和 ESLint 均不可用，请确保已安装: npm install -D typescript eslint");
    }
}