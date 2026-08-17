package athena.coder.ai.util;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ProjectTypeTest {

    /**
     * 临时目录根，建在 target 下而非系统 java.io.tmpdir，
     * 避免沙箱 / 受限环境对 /var/folders 等系统临时目录的写限制。
     */
    private static Path base;

    @BeforeAll
    static void setUp() throws IOException {
        base = Files.createTempDirectory(Path.of("target"), "project-type-");
    }

    @AfterAll
    static void tearDown() throws IOException {
        if (base == null) {
            return;
        }
        try (Stream<Path> walk = Files.walk(base)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // 清理失败不影响测试结论
                }
            });
        }
    }

    @Test
    void detect_nullOrBlank_returnsUnknown() {
        assertEquals(ProjectType.UNKNOWN, ProjectType.detect((Path) null));
        assertEquals(ProjectType.UNKNOWN, ProjectType.detect((String) null));
        assertEquals(ProjectType.UNKNOWN, ProjectType.detect(""));
        assertEquals(ProjectType.UNKNOWN, ProjectType.detect("   "));
    }

    @Test
    void detect_byMarkerFile() throws Exception {
        assertDetect("pom.xml", ProjectType.MAVEN);
        assertDetect("build.gradle", ProjectType.GRADLE);
        assertDetect("build.gradle.kts", ProjectType.GRADLE);
        assertDetect("package.json", ProjectType.NODE);
        assertDetect("go.mod", ProjectType.GO);
        assertDetect("Cargo.toml", ProjectType.RUST);
        assertDetect("pyproject.toml", ProjectType.PYTHON);
        assertDetect("requirements.txt", ProjectType.PYTHON);
        assertDetect("setup.py", ProjectType.PYTHON);
        assertDetect("Pipfile", ProjectType.PYTHON);
    }

    @Test
    void detect_srcMainJava_isJavac() throws Exception {
        Path fresh = Files.createTempDirectory(base, "proj");
        Files.createDirectories(fresh.resolve("src/main/java"));
        assertEquals(ProjectType.JAVAC, ProjectType.detect(fresh));
    }

    @Test
    void detect_emptyDir_isUnknown() throws Exception {
        Path fresh = Files.createTempDirectory(base, "proj");
        assertEquals(ProjectType.UNKNOWN, ProjectType.detect(fresh));
    }

    @Test
    void key_and_executable() {
        assertEquals("maven", ProjectType.MAVEN.key());
        assertEquals("unknown", ProjectType.UNKNOWN.key());
        // 非 unknown 类型两种平台均有可执行文件，仅断言非空（平台无关）
        assertNotNull(ProjectType.MAVEN.executable());
        // unknown 无基础可执行文件
        assertNull(ProjectType.UNKNOWN.executable());
    }

    private void assertDetect(String marker, ProjectType expected) throws Exception {
        Path fresh = Files.createTempDirectory(base, "proj");
        Files.createFile(fresh.resolve(marker));
        assertEquals(expected, ProjectType.detect(fresh), marker);
    }
}
