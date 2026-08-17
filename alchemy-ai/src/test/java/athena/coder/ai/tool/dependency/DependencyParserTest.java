package athena.coder.ai.tool.dependency;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DependencyParserTest {

    private final DependencyParser parser = new DependencyParser();

    @Test
    void mavenCoordinates() {
        Dependency d = parser.parse("org.example:foo:1.0", null, null);
        assertEquals("org.example", d.getGroupId());
        assertEquals("foo", d.getArtifactId());
        assertEquals("1.0", d.getVersion());
        assertEquals("compile", d.getScope());
        assertEquals("org.example:foo:1.0", d.getCoordinates());
    }

    @Test
    void npmStyle() {
        Dependency d = parser.parse("lodash@4.17.0", null, "runtime");
        assertNull(d.getGroupId());
        assertEquals("lodash", d.getArtifactId());
        assertEquals("4.17.0", d.getVersion());
        assertEquals("runtime", d.getScope());
        assertEquals("lodash@4.17.0", d.getCoordinates());
    }

    @Test
    void pythonStyle() {
        Dependency d = parser.parse("requests==2.31.0", null, null);
        assertNull(d.getGroupId());
        assertEquals("requests", d.getArtifactId());
        assertEquals("2.31.0", d.getVersion());
    }

    @Test
    void bareArtifact() {
        Dependency d = parser.parse("commons-lang3", null, null);
        assertNull(d.getGroupId());
        assertEquals("commons-lang3", d.getArtifactId());
        assertEquals("", d.getVersion());
        assertEquals("compile", d.getScope());
        assertEquals("commons-lang3", d.getCoordinates());
    }

    @Test
    void explicitVersion_overridesParsedVersion() {
        Dependency d = parser.parse("org.example:foo:1.0", "2.0", null);
        assertEquals("2.0", d.getVersion());
    }

    @Test
    void blankDependency_returnsEmptyDependency() {
        Dependency d = parser.parse("   ", null, null);
        assertNull(d.getGroupId());
        assertEquals("", d.getArtifactId());
    }
}
