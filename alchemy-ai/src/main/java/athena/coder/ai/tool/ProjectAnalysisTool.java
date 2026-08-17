package athena.coder.ai.tool;

import athena.coder.ai.tool.base.FileSystemBasedTool;
import athena.coder.ai.tool.util.FileTraversalHelper;
import athena.coder.ai.tool.exception.ErrorCode;
import athena.coder.ai.tool.exception.ToolValidationException;
import athena.coder.ai.util.ProjectType;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class ProjectAnalysisTool extends FileSystemBasedTool {

    private static final Set<String> ALLOWED_CONFIGS = Set.of(
            "pom.xml", "build.gradle", "build.gradle.kts", "settings.gradle",
            "application.yml", "application.yaml", "application.properties",
            "application-dev.yml", "application-prod.yml",
            "logback.xml", "log4j.properties", "log4j2.xml"
    );

    @Tool("分析项目整体结构，包括技术栈、构建工具、模块划分、主要包结构、核心入口类。")
    public String analyzeProjectStructure(
            @P("项目目录路径") String workingDir) {

        return executeSafely(() -> {
            Path dir = resolveAndValidate(workingDir);
            if (!safeFileExists(dir) || !safeIsDirectory(dir)) {
                throw new ToolValidationException(getToolName(), ErrorCode.PATH_INVALID, workingDir);
            }

            StringBuilder result = new StringBuilder();
            result.append("=== 项目结构分析 ===\n\n");

            result.append("构建工具: ").append(detectBuildTool(dir)).append("\n");

            result.append("\n技术栈:\n");
            List<String> techStack = detectTechStack(dir);
            for (String tech : techStack) {
                result.append("  - ").append(tech).append("\n");
            }

            result.append("\n包结构:\n");
            analyzePackageStructure(dir, result);

            result.append("\n入口类:\n");
            findMainClasses(dir, result);

            result.append("\n配置文件:\n");
            findConfigFiles(dir, result);

            result.append("\n代码统计:\n");
            countCodeStats(dir, result);

            return enforceOutputLimit(result.toString());

        }, "analyzeProjectStructure");
    }

    @Tool("读取并解析项目配置文件（pom.xml, build.gradle, application.yml, application.properties 等），返回关键配置信息。")
    public String readProjectConfig(
            @P("项目目录路径") String workingDir,
            @P("配置文件名，如 'pom.xml'、'application.yml'、'application.properties'") String configFile) {

        return executeSafely(() -> {
            if (configFile == null || configFile.isBlank()) {
                throw new ToolValidationException(getToolName(), ErrorCode.PARAM_MISSING, "configFile");
            }

            Path dir = resolveAndValidate(workingDir);

            if (!ALLOWED_CONFIGS.contains(configFile)) {
                return formatError(ErrorCode.PARAM_INVALID,
                        configFile + "\n支持的文件: " + String.join(", ", ALLOWED_CONFIGS));
            }

            Path configPath = findConfigFile(dir, configFile);
            if (configPath == null) {
                return formatError(ErrorCode.FILE_NOT_FOUND, configFile);
            }

            List<String> lines = safeReadAllLines(configPath);
            StringBuilder result = new StringBuilder();
            result.append("配置文件: ").append(getAllowedWorkDir().relativize(configPath)).append("\n");
            result.append("===\n");

            if (configFile.equals("pom.xml")) {
                parsePomXml(lines, result);
            } else if (configFile.endsWith(".properties")) {
                parseProperties(lines, result);
            } else {
                int maxLines = Math.min(100, lines.size());
                for (int i = 0; i < maxLines; i++) {
                    result.append(lines.get(i)).append("\n");
                }
                if (lines.size() > maxLines) {
                    result.append("... (共 ").append(lines.size()).append(" 行)\n");
                }
            }

            return enforceOutputLimit(result.toString());

        }, "readProjectConfig");
    }

    @Tool("分析某个 Java 类的依赖关系：它依赖哪些类、被哪些类引用、实现了哪些接口。")
    public String analyzeClassDependencies(
            @P("项目目录路径") String workingDir,
            @P("类名（简单名或全限定名）") String className) {

        return executeSafely(() -> {
            if (className == null || className.isBlank()) {
                throw new ToolValidationException(getToolName(), ErrorCode.PARAM_MISSING, "className");
            }

            Path dir = resolveAndValidate(workingDir);

            String simpleClassName = className.contains(".") ?
                    className.substring(className.lastIndexOf('.') + 1) : className;

            Path classFile = findClassFile(dir, simpleClassName);
            if (classFile == null) {
                return formatError(ErrorCode.FILE_NOT_FOUND, className);
            }

            List<String> lines = safeReadAllLines(classFile);
            StringBuilder result = new StringBuilder();
            result.append("类分析: ").append(className).append("\n");
            result.append("文件: ").append(getAllowedWorkDir().relativize(classFile)).append("\n");
            result.append("===\n");

            result.append("\n依赖的类（import）:\n");
            Pattern importPattern = patternRegistry.importStatement();
            Set<String> imports = new TreeSet<>();
            for (String line : lines) {
                Matcher matcher = importPattern.matcher(line);
                if (matcher.find()) {
                    imports.add(matcher.group(1));
                }
            }
            for (String imp : imports) {
                result.append("  - ").append(imp).append("\n");
            }

            result.append("\n类声明:\n");
            Pattern classPattern = patternRegistry.javaClassDeclaration();
            for (String line : lines) {
                Matcher matcher = classPattern.matcher(line);
                if (matcher.find()) {
                    result.append("  类型: ").append(matcher.group(1)).append("\n");
                    result.append("  名称: ").append(matcher.group(2)).append("\n");
                    if (matcher.group(3) != null) {
                        result.append("  继承: ").append(matcher.group(3)).append("\n");
                    }
                    if (matcher.group(4) != null) {
                        result.append("  实现: ").append(matcher.group(4).trim()).append("\n");
                    }
                    break;
                }
            }

            result.append("\n被引用情况:\n");
            int referenceCount = countReferences(dir, simpleClassName);
            result.append("  在其他文件中被引用约 ").append(referenceCount).append(" 次\n");

            return enforceOutputLimit(result.toString());

        }, "analyzeClassDependencies");
    }

    private String detectBuildTool(Path dir) {
        return switch (ProjectType.detect(dir)) {
            case MAVEN -> "Maven (Java)";
            case GRADLE -> "Gradle (Java/Kotlin)";
            case NODE -> "npm/yarn (Node.js)";
            case GO -> "Go Modules";
            case RUST -> "Cargo (Rust)";
            case PYTHON -> "Python";
            case JAVAC -> "纯 Java (javac)";
            case UNKNOWN -> "未知（未识别到构建工具配置文件）";
        };
    }

    private record LangStats(int fileCount, int lineCount) {
        LangStats add(int files, int lines) {
            return new LangStats(fileCount + files, lineCount + lines);
        }
    }

    private List<String> detectTechStack(Path dir) {
        List<String> techStack = new ArrayList<>();

        detectJavaStack(dir, techStack);
        detectGoStack(dir, techStack);
        detectRustStack(dir, techStack);
        detectPythonStack(dir, techStack);
        detectNodeStack(dir, techStack);

        if (techStack.isEmpty()) {
            techStack.add("未识别到技术栈依赖");
        }
        return techStack;
    }

    private void detectJavaStack(Path dir, List<String> techStack) {
        Path pomPath = dir.resolve("pom.xml");
        if (!safeFileExists(pomPath)) return;
        try {
            String content = safeReadString(pomPath);
            if (content.contains("spring-boot")) techStack.add("Spring Boot");
            if (content.contains("spring-cloud")) techStack.add("Spring Cloud");
            if (content.contains("javafx")) techStack.add("JavaFX");
            if (content.contains("langchain4j")) techStack.add("LangChain4j");
            if (content.contains("hibernate")) techStack.add("Hibernate");
            if (content.contains("mybatis")) techStack.add("MyBatis");
            if (content.contains("sqlite")) techStack.add("SQLite");
            if (content.contains("mysql")) techStack.add("MySQL");
            if (content.contains("postgresql")) techStack.add("PostgreSQL");
            if (content.contains("jdbi")) techStack.add("JDBI");
            if (content.contains("hikari")) techStack.add("HikariCP");
            if (content.contains("jackson")) techStack.add("Jackson");
            if (content.contains("gson")) techStack.add("Gson");
            if (content.contains("lombok")) techStack.add("Lombok");
            if (content.contains("junit")) techStack.add("JUnit");
            if (content.contains("mockito")) techStack.add("Mockito");
        } catch (Exception e) {
            logFine("Java技术栈检测失败: " + e.getMessage());
        }
    }

    private void detectGoStack(Path dir, List<String> techStack) {
        Path goMod = dir.resolve("go.mod");
        if (!safeFileExists(goMod)) return;
        techStack.add("Go Modules");
        try {
            String content = safeReadString(goMod);
            if (content.contains("gin-gonic/gin")) techStack.add("Gin Web Framework");
            if (content.contains("gorilla/mux")) techStack.add("Gorilla Mux");
            if (content.contains("gorm.io")) techStack.add("GORM");
            if (content.contains("go.uber.org/zap")) techStack.add("Zap Logger");
        } catch (Exception e) {
            logFine("Go技术栈检测失败: " + e.getMessage());
        }
    }

    private void detectRustStack(Path dir, List<String> techStack) {
        Path cargoToml = dir.resolve("Cargo.toml");
        if (!safeFileExists(cargoToml)) return;
        techStack.add("Cargo (Rust)");
        try {
            String content = safeReadString(cargoToml);
            if (content.contains("tokio")) techStack.add("Tokio Async Runtime");
            if (content.contains("actix-web")) techStack.add("Actix-Web");
            if (content.contains("diesel")) techStack.add("Diesel ORM");
            if (content.contains("serde")) techStack.add("Serde Serialization");
        } catch (Exception e) {
            logFine("Rust技术栈检测失败: " + e.getMessage());
        }
    }

    private void detectPythonStack(Path dir, List<String> techStack) {
        Path pyproject = dir.resolve("pyproject.toml");
        if (safeFileExists(pyproject)) {
            try {
                String content = safeReadString(pyproject);
                techStack.add("Python");
                if (content.contains("django")) techStack.add("Django");
                if (content.contains("flask")) techStack.add("Flask");
                if (content.contains("fastapi")) techStack.add("FastAPI");
                if (content.contains("pytest")) techStack.add("pytest");
                if (content.contains("numpy")) techStack.add("NumPy");
                if (content.contains("pandas")) techStack.add("Pandas");
            } catch (Exception e) {
                logFine("Python技术栈检测失败: " + e.getMessage());
            }
        }
        Path requirements = dir.resolve("requirements.txt");
        if (safeFileExists(requirements) && !techStack.contains("Python")) {
            techStack.add("Python (pip)");
        }
    }

    private void detectNodeStack(Path dir, List<String> techStack) {
        Path packageJson = dir.resolve("package.json");
        if (!safeFileExists(packageJson)) return;
        techStack.add("Node.js");
        try {
            String content = safeReadString(packageJson);
            if (content.contains("react")) techStack.add("React");
            if (content.contains("vue")) techStack.add("Vue.js");
            if (content.contains("next")) techStack.add("Next.js");
            if (content.contains("express")) techStack.add("Express");
            if (content.contains("typescript")) techStack.add("TypeScript");
        } catch (Exception e) {
            logFine("Node.js技术栈检测失败: " + e.getMessage());
        }
    }

    private void analyzePackageStructure(Path dir, StringBuilder result) {
        String[] srcDirs = {
                "src/main/java",
                "src/main/kotlin",
                "src",
                "lib",
                "app"
        };

        Path foundSrcDir = null;
        for (String srcPath : srcDirs) {
            Path candidate = dir.resolve(srcPath.replace("/", File.separator));
            if (safeFileExists(candidate) && safeIsDirectory(candidate)) {
                foundSrcDir = candidate;
                break;
            }
        }

        if (foundSrcDir == null) {
            result.append("  未找到标准源码目录\n");
            return;
        }

        String srcDirName = dir.relativize(foundSrcDir).toString().replace(File.separator, "/");
        result.append("  源码目录: ").append(srcDirName).append("/\n");

        final Path srcDir = foundSrcDir;
        try (Stream<Path> dirs = safeWalk(srcDir, 3)) {
            Set<String> packages = new TreeSet<>();
            dirs.filter(Files::isDirectory)
                    .filter(d -> !d.equals(srcDir))
                    .forEach(d -> {
                        String pkg = srcDir.relativize(d).toString().replace(File.separator, ".");
                        packages.add(pkg);
                    });

            if (packages.isEmpty()) {
                result.append("  未找到子目录结构\n");
            } else {
                for (String pkg : packages) {
                    String layer = identifyLayer(pkg);
                    if (layer != null) {
                        result.append("  ").append(pkg).append(" [").append(layer).append("]\n");
                    } else {
                        result.append("  ").append(pkg).append("\n");
                    }
                }
            }
        } catch (Exception e) {
            result.append("  分析失败: ").append(e.getMessage()).append("\n");
        }
    }

    private String identifyLayer(String packageName) {
        String lower = packageName.toLowerCase();
        if (lower.contains("controller") || lower.contains("web") || lower.contains("api")) return "控制层";
        if (lower.contains("service")) return "业务层";
        if (lower.contains("dao") || lower.contains("repository") || lower.contains("mapper")) return "数据层";
        if (lower.contains("entity") || lower.contains("model") || lower.contains("domain")) return "实体层";
        if (lower.contains("config")) return "配置层";
        if (lower.contains("util") || lower.contains("helper")) return "工具层";
        if (lower.contains("ui") || lower.contains("view") || lower.contains("fx")) return "UI层";
        return null;
    }

    private void findMainClasses(Path dir, StringBuilder result) {
        boolean found = false;

        found |= findMainClassesByPattern(dir, "src/main/java", ".java", "public static void main", "Java", result);
        found |= findMainClassesByPattern(dir, "", ".go", "func main()", "Go", result);

        Path rustMain = dir.resolve("src").resolve("main.rs");
        if (safeFileExists(rustMain)) {
            result.append("  [Rust] src/main.rs\n");
            found = true;
        }

        found |= findMainClassesByPattern(dir, "", ".py", "if __name__", "Python", result);

        if (!found) {
            result.append("  未找到明显的程序入口\n");
        }
    }

    private boolean findMainClassesByPattern(Path baseDir, String srcRelPath, String extension,
                                             String mainPattern, String langLabel, StringBuilder result) {
        Path searchDir = srcRelPath.isEmpty() ? baseDir : baseDir.resolve(srcRelPath.replace("/", java.io.File.separator));
        if (!safeFileExists(searchDir)) {
            return false;
        }

        boolean found = false;
        List<Path> mainClasses = new ArrayList<>();
        for (Path p : FileTraversalHelper.findFiles(searchDir,
                file -> file.getFileName().toString().endsWith(extension), 10, Integer.MAX_VALUE)) {
            try {
                if (safeReadString(p).contains(mainPattern)) {
                    mainClasses.add(p);
                }
            } catch (Exception e) {
                logFine("查找" + langLabel + "入口失败: " + p + " - " + e.getMessage());
            }
        }

        for (Path p : mainClasses) {
            String relativePath = searchDir.relativize(p).toString();
            String displayPath = relativePath.replace(java.io.File.separator, ".");
            if (extension.equals(".java")) {
                displayPath = displayPath.replace(".java", "");
            }
            result.append("  [").append(langLabel).append("] ").append(displayPath).append("\n");
            found = true;
        }

        return found;
    }

    private void findConfigFiles(Path dir, StringBuilder result) {
        String[] configPatterns = {
                "src/main/resources/application.yml",
                "src/main/resources/application.yaml",
                "src/main/resources/application.properties",
                "src/main/resources/logback.xml",
                "src/main/resources/log4j.properties"
        };

        boolean found = false;
        for (String pattern : configPatterns) {
            Path configPath = dir.resolve(pattern);
            if (safeFileExists(configPath)) {
                result.append("  - ").append(pattern).append("\n");
                found = true;
            }
        }

        if (!found) {
            result.append("  未找到常见配置文件\n");
        }
    }

    private void countCodeStats(Path dir, StringBuilder result) {
        String[][] langConfigs = {
                {".java", "Java"},
                {".scala", "Scala"},
                {".kt", "Kotlin"},
                {".go", "Go"},
                {".rs", "Rust"},
                {".py", "Python"},
                {".rb", "Ruby"},
                {".php", "PHP"},
                {".js", "JavaScript"},
                {".ts", "TypeScript"},
                {".jsx", "React JSX"},
                {".tsx", "React TSX"},
                {".cs", "C#"},
                {".c", "C"},
                {".cpp", "C++"},
                {".h", "C/C++ Header"}
        };

        Map<String, LangStats> stats = new LinkedHashMap<>();
        for (String[] config : langConfigs) {
            stats.put(config[1], new LangStats(0, 0));
        }

        for (Path p : FileTraversalHelper.findCodeFiles(dir, 8, Integer.MAX_VALUE)) {
            String name = p.getFileName().toString().toLowerCase();
            for (String[] config : langConfigs) {
                if (name.endsWith(config[0])) {
                    LangStats s = stats.get(config[1]);
                    try {
                        stats.put(config[1], s.add(1, safeReadAllLines(p).size()));
                    } catch (Exception e) {
                        logFine("统计代码行数失败: " + p + " - " + e.getMessage());
                        stats.put(config[1], s.add(1, 0));
                    }
                    break;
                }
            }
        }

        int totalFiles = 0;
        int totalLines = 0;
        for (Map.Entry<String, LangStats> entry : stats.entrySet()) {
            LangStats s = entry.getValue();
            if (s.fileCount() > 0) {
                result.append(String.format("  %s: %d 个文件, %d 行\n", entry.getKey(), s.fileCount(), s.lineCount()));
                totalFiles += s.fileCount();
                totalLines += s.lineCount();
            }
        }
        result.append(String.format("  总计: %d 个文件, %d 行\n", totalFiles, totalLines));
    }

    private Path findConfigFile(Path dir, String fileName) {
        String[] locations = {
                "",
                "src/main/resources/",
                "src/main/webapp/WEB-INF/",
                "config/"
        };

        for (String loc : locations) {
            Path path = dir.resolve(loc + fileName);
            if (safeFileExists(path)) {
                return path;
            }
        }
        return null;
    }

    private void parsePomXml(List<String> lines, StringBuilder result) {
        String content = String.join("\n", lines);

        Pattern pattern = Pattern.compile("<(groupId|artifactId|version)>([^<]+)</\\1>");
        Matcher matcher = pattern.matcher(content);
        Map<String, String> basicInfo = new LinkedHashMap<>();
        int count = 0;
        while (matcher.find() && count < 3) {
            basicInfo.put(matcher.group(1), matcher.group(2));
            count++;
        }

        result.append("基本信息:\n");
        basicInfo.forEach((k, v) -> result.append("  ").append(k).append(": ").append(v).append("\n"));

        result.append("\n依赖列表:\n");
        Pattern depPattern = Pattern.compile("<dependency>.*?<groupId>([^<]+)</groupId>.*?<artifactId>([^<]+)</artifactId>.*?</dependency>",
                Pattern.DOTALL);
        Matcher depMatcher = depPattern.matcher(content);
        while (depMatcher.find()) {
            result.append("  - ").append(depMatcher.group(1)).append(":").append(depMatcher.group(2)).append("\n");
        }
    }

    private void parseProperties(List<String> lines, StringBuilder result) {
        result.append("配置项:\n");
        for (String line : lines) {
            line = line.trim();
            if (!line.isEmpty() && !line.startsWith("#")) {
                result.append("  ").append(line).append("\n");
            }
        }
    }

    private Path findClassFile(Path dir, String simpleClassName) {
        Path srcDir = dir.resolve("src").resolve("main").resolve("java");
        if (!safeFileExists(srcDir)) {
            return null;
        }

        List<Path> matches = FileTraversalHelper.findFiles(srcDir,
                p -> p.getFileName().toString().equals(simpleClassName + ".java"), 10, 1);
        return matches.isEmpty() ? null : matches.get(0);
    }

    private int countReferences(Path dir, String className) {
        Path srcDir = dir.resolve("src").resolve("main").resolve("java");
        if (!safeFileExists(srcDir)) {
            return 0;
        }

        int count = 0;
        for (Path p : FileTraversalHelper.findFiles(srcDir,
                file -> file.toString().endsWith(".java"), 10, Integer.MAX_VALUE)) {
            try {
                String content = safeReadString(p);
                int idx = 0;
                while ((idx = content.indexOf(className, idx)) != -1) {
                    count++;
                    idx += className.length();
                }
            } catch (Exception e) {
                logFine("统计引用失败: " + p + " - " + e.getMessage());
            }
        }
        return count;
    }
}