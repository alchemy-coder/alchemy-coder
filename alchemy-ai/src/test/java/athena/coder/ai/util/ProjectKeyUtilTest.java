package athena.coder.ai.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectKeyUtilTest {

    @Test
    void key_isStable16Hex() {
        String key = ProjectKeyUtil.projectKey("/Users/a/b");
        assertEquals(16, key.length());
        assertTrue(key.matches("[0-9a-f]{16}"), key);
        assertEquals(key, ProjectKeyUtil.projectKey("/Users/a/b"));
    }

    @Test
    void differentPaths_yieldDifferentKeys() {
        assertNotEquals(ProjectKeyUtil.projectKey("/a"), ProjectKeyUtil.projectKey("/b"));
    }
}
