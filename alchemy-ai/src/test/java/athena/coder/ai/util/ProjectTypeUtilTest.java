package athena.coder.ai.util;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProjectTypeUtilTest {

    @Test
    void constants_matchEnumKeys() {
        assertEquals(ProjectType.MAVEN.key(), ProjectTypeUtil.MAVEN);
        assertEquals(ProjectType.GRADLE.key(), ProjectTypeUtil.GRADLE);
        assertEquals(ProjectType.NODE.key(), ProjectTypeUtil.NODE);
        assertEquals(ProjectType.GO.key(), ProjectTypeUtil.GO);
        assertEquals(ProjectType.RUST.key(), ProjectTypeUtil.RUST);
        assertEquals(ProjectType.PYTHON.key(), ProjectTypeUtil.PYTHON);
        assertEquals(ProjectType.JAVAC.key(), ProjectTypeUtil.JAVAC);
        assertEquals(ProjectType.UNKNOWN.key(), ProjectTypeUtil.UNKNOWN);
    }

    @Test
    void detect_delegatesToEnum() {
        assertEquals(ProjectType.UNKNOWN.key(), ProjectTypeUtil.detect((Path) null));
        assertEquals(ProjectType.UNKNOWN.key(), ProjectTypeUtil.detect((String) null));
    }
}
