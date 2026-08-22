package athena.coder.ai.workflow.node;

import athena.coder.ai.workflow.entity.StepRole;
import athena.coder.ai.workflow.entity.WorkflowState;
import athena.coder.exception.RocAgentException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbstractAgentNodeTest {

    /** 最小子类：仅用于暴露 protected static 工具方法 */
    private static final class Probe extends AbstractAgentNode {
        @Override
        protected Map<String, Object> doApply(WorkflowState state, NodeContext ctx) {
            return Map.of();
        }

        @Override
        protected StepRole stepRole() {
            return StepRole.TESTER;
        }
    }

    private static final Probe PROBE = new Probe();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void requireUpstream_blankThrows() {
        assertThrows(RocAgentException.class, () -> PROBE.requireUpstream(null, "缺计划"));
        assertThrows(RocAgentException.class, () -> PROBE.requireUpstream("  ", "缺计划"));
    }

    @Test
    void requireUpstream_returnsValue() {
        assertEquals("PLAN", PROBE.requireUpstream("PLAN", "缺计划"));
    }

    @Test
    void buildChangeSummary_empty_isValidJson() throws Exception {
        JsonNode node = MAPPER.readTree(PROBE.buildChangeSummary(null, null));
        assertTrue(node.path("changedFiles").isArray());
        assertTrue(node.path("changedFiles").isEmpty());
        assertEquals("", node.path("diffRef").asText());
    }

    @Test
    void buildChangeSummary_populatesFilesAndDiff() throws Exception {
        JsonNode node = MAPPER.readTree(PROBE.buildChangeSummary("A.java, B.java", "a..b"));
        assertEquals(2, node.path("changedFiles").size());
        assertEquals("A.java", node.path("changedFiles").get(0).asText());
        assertEquals("B.java", node.path("changedFiles").get(1).asText());
        assertEquals("a..b", node.path("diffRef").asText());
    }

    @Test
    void buildChangeSummary_escapesSpecialChars() throws Exception {
        String json = PROBE.buildChangeSummary("A\"B.java", null);
        JsonNode node = MAPPER.readTree(json); // 不抛 = 合法 JSON
        assertEquals("A\"B.java", node.path("changedFiles").get(0).asText());
    }

    @Test
    void truncate_nullShortLong() {
        assertEquals("null", PROBE.truncate(null, 5));
        assertEquals("abc", PROBE.truncate("abc", 5));
        assertEquals("abcd...", PROBE.truncate("abcdefgh", 4));
    }

    @Test
    void sessionId_is8Chars() {
        String id = PROBE.sessionId();
        assertEquals(8, id.length());
        assertTrue(id.matches("[0-9a-f]{8}"), id); // UUID 前 8 位为小写 hex
    }
}
