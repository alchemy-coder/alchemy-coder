package athena.coder.ai.util;

import java.nio.file.Path;

/**
 * 项目类型 String key 门面，委托 {@link ProjectType} 枚举（唯一来源）。
 * <p>
 * 保留 String 常量以兼容既有的 String 签名调用点；新代码请直接使用 {@link ProjectType}。
 */
public final class ProjectTypeUtil {

    public static final String MAVEN = ProjectType.MAVEN.key();
    public static final String GRADLE = ProjectType.GRADLE.key();
    public static final String NODE = ProjectType.NODE.key();
    public static final String GO = ProjectType.GO.key();
    public static final String RUST = ProjectType.RUST.key();
    public static final String PYTHON = ProjectType.PYTHON.key();
    public static final String JAVAC = ProjectType.JAVAC.key();
    public static final String UNKNOWN = ProjectType.UNKNOWN.key();

    private ProjectTypeUtil() {
    }

    public static String detect(Path dir) {
        return ProjectType.detect(dir).key();
    }

    public static String detect(String dirPath) {
        return ProjectType.detect(dirPath).key();
    }
}
