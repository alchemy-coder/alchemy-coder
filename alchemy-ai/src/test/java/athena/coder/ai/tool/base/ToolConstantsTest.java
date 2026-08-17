package athena.coder.ai.tool.base;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ToolConstantsTest {

    @Test
    void parseResult_matchesKnownPrefixes() {
        ToolConstants.CommandResult r = ToolConstants.parseResult("SUCCESS\nbody");
        assertEquals(ToolConstants.CommandStatus.SUCCESS, r.status());
        assertEquals("body", r.body());
        assertEquals(0, r.exitCode());
        assertTrue(r.isSuccess());
    }

    @Test
    void parseResult_unknownOrNull() {
        assertEquals(ToolConstants.CommandStatus.UNKNOWN,
                ToolConstants.parseResult("whatever").status());
        assertEquals(ToolConstants.CommandStatus.UNKNOWN,
                ToolConstants.parseResult(null).status());
        assertEquals("", ToolConstants.parseResult(null).body());
    }

    @Test
    void stripResultPrefix() {
        assertEquals("body", ToolConstants.stripResultPrefix("FAILED\nbody"));
        assertEquals("no-prefix", ToolConstants.stripResultPrefix("no-prefix"));
    }

    private static void assertTrue(boolean condition) {
        org.junit.jupiter.api.Assertions.assertTrue(condition);
    }
}
