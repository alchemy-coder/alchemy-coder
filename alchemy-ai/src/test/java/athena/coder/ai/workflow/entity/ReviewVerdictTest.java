package athena.coder.ai.workflow.entity;

import athena.coder.exception.RocAgentException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReviewVerdictTest {

    @Test
    void from_nullOrBlank_returnsBlocked() {
        assertEquals(ReviewVerdict.BLOCKED, ReviewVerdict.from(null));
        assertEquals(ReviewVerdict.BLOCKED, ReviewVerdict.from(""));
        assertEquals(ReviewVerdict.BLOCKED, ReviewVerdict.from("   "));
    }

    @Test
    void from_caseInsensitive() {
        assertEquals(ReviewVerdict.APPROVED, ReviewVerdict.from("approved"));
        assertEquals(ReviewVerdict.APPROVED_WITH_NOTES, ReviewVerdict.from("APPROVED_WITH_NOTES"));
        assertEquals(ReviewVerdict.REQUEST_CHANGES, ReviewVerdict.from("request_changes"));
        assertEquals(ReviewVerdict.BLOCKED, ReviewVerdict.from("BLOCKED"));
    }

    @Test
    void from_unknown_throws() {
        assertThrows(RocAgentException.class, () -> ReviewVerdict.from("WEIRD"));
    }
}
