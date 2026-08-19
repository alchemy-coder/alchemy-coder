package athena.coder.ai.workflow.entity;

import athena.coder.exception.RocAgentException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TesterStatusTest {

    @Test
    void from_nullOrBlank_returnsError() {
        assertEquals(TesterStatus.ERROR, TesterStatus.from(null));
        assertEquals(TesterStatus.ERROR, TesterStatus.from(""));
        assertEquals(TesterStatus.ERROR, TesterStatus.from("   "));
    }

    @Test
    void from_caseInsensitive() {
        assertEquals(TesterStatus.PASS, TesterStatus.from("pass"));
        assertEquals(TesterStatus.FAIL, TesterStatus.from("FAIL"));
        assertEquals(TesterStatus.SKIP, TesterStatus.from("Skip"));
        assertEquals(TesterStatus.ERROR, TesterStatus.from("ERROR"));
    }

    @Test
    void from_unknown_throws() {
        assertThrows(RocAgentException.class, () -> TesterStatus.from("WEIRD"));
    }
}
