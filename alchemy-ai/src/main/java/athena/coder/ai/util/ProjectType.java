package athena.coder.ai.util;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 项目类型：构建工具身份 / 项目检测 / 基础可执行文件的唯一来源。
 * <p>
 * 命令构建（CommandBuilder）与依赖管理（DependencyStrategy）两套策略层级共享此枚举，
 * 避免各自硬编码可执行文件解析与项目类型检测。
 * <p>
 * 已知例外（保留在各策略内的字面量）：
 * <ul>
 *   <li>Python 依赖管理用 {@code pip}（非 {@code python3}）</li>
 *   <li>Gradle 命令构建用 wrapper（{@code ./gradlew}），依赖管理用裸 {@code gradle}</li>
 * </ul>
 */
public enum ProjectType {

    MAVEN("maven", "mvn", "mvn.cmd"),
    GRADLE("gradle", "gradle", "gradle.bat"),
    NODE("node", "npm", "npm"),
    GO("go", "go", "go"),
    RUST("rust", "cargo", "cargo"),
    PYTHON("python", "python3", "python"),
    JAVAC("javac", "javac", "javac"),
    UNKNOWN("unknown", null, null);

    private final String key;
    private final String unixExecutable;
    private final String windowsExecutable;

    ProjectType(String key, String unixExecutable, String windowsExecutable) {
        this.key = key;
        this.unixExecutable = unixExecutable;
        this.windowsExecutable = windowsExecutable;
    }

    public String key() {
        return key;
    }

    /**
     * 平台感知的基础可执行文件（构建/测试/诊断上下文）。
     */
    public String executable() {
        return isWindows() ? windowsExecutable : unixExecutable;
    }

    public static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    /**
     * 检测项目类型：pom.xml / build.gradle* / package.json / go.mod / Cargo.toml / pyproject… / src/main/java。
     */
    public static ProjectType detect(Path dir) {
        if (dir == null) return UNKNOWN;
        if (Files.exists(dir.resolve("pom.xml"))) return MAVEN;
        if (Files.exists(dir.resolve("build.gradle")) || Files.exists(dir.resolve("build.gradle.kts"))) return GRADLE;
        if (Files.exists(dir.resolve("package.json"))) return NODE;
        if (Files.exists(dir.resolve("go.mod"))) return GO;
        if (Files.exists(dir.resolve("Cargo.toml"))) return RUST;
        if (Files.exists(dir.resolve("pyproject.toml")) ||
                Files.exists(dir.resolve("requirements.txt")) ||
                Files.exists(dir.resolve("setup.py")) ||
                Files.exists(dir.resolve("Pipfile"))) return PYTHON;
        if (Files.exists(dir.resolve("src").resolve("main").resolve("java"))) return JAVAC;
        return UNKNOWN;
    }

    public static ProjectType detect(String dirPath) {
        if (dirPath == null || dirPath.isBlank()) return UNKNOWN;
        return detect(Path.of(dirPath));
    }
}
