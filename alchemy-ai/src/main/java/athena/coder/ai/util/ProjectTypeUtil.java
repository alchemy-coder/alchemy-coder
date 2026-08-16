package athena.coder.ai.util;

import java.nio.file.Files;
import java.nio.file.Path;

public final class ProjectTypeUtil {

    public static final String MAVEN = "maven";
    public static final String GRADLE = "gradle";
    public static final String NODE = "node";
    public static final String GO = "go";
    public static final String RUST = "rust";
    public static final String PYTHON = "python";
    public static final String JAVAC = "javac";
    public static final String UNKNOWN = "unknown";

    private ProjectTypeUtil() {
    }

    /**
     * 检测项目类型
     * <p>
     * 返回值: maven / gradle / node / go / rust / python / javac / unknown
     */
    public static String detect(Path dir) {
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

    public static String detect(String dirPath) {
        if (dirPath == null || dirPath.isBlank()) return UNKNOWN;
        return detect(Path.of(dirPath));
    }
}