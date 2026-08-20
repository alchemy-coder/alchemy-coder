package athena.coder.ai.workflow.node;

import athena.coder.ai.spi.AgentExecution;
import athena.coder.ai.spi.AgentExecutionSink;
import athena.coder.ai.spi.AiInfra;
import athena.coder.ai.workflow.entity.WorkflowState;
import athena.coder.entity.chat.ChatEnum;
import athena.coder.entity.model.ModelEnum;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NodeExecutionPersistenceTest {

    private static final String SESSION_ID = "session-uuid-123";

    private static WorkflowState state() {
        Map<String, Object> m = new HashMap<>();
        m.put(WorkflowState.INIT_TASK_ID, 1L);
        m.put(WorkflowState.INIT_WORK_FULL_PATH, System.getProperty("user.dir"));
        m.put(WorkflowState.INIT_USER_MESSAGE, "实现登录");
        m.put(WorkflowState.INIT_MODEL_TYPE, ModelEnum.QIANWEN37MAX);
        m.put(WorkflowState.INIT_BOT_RESPONSE, (BiConsumer<String, ChatEnum>) (msg, type) -> {});
        m.put(WorkflowState.INIT_SESSION_ID, SESSION_ID);
        return new WorkflowState(m);
    }

    private static final class RecordingSink implements AgentExecutionSink {
        final List<AgentExecution> records = new ArrayList<>();

        @Override
        public void record(AgentExecution execution) {
            records.add(execution);
        }
    }

    private static final class RecordingProbe extends AbstractAgentNode {
        @Override
        protected Map<String, Object> doApply(WorkflowState state, NodeContext ctx) {
            return Map.<String, Object>of(WorkflowState.NEXT_NODE, "ROUTER");
        }

        @Override
        protected String stepLabel() {
            return "[测试]";
        }
    }

    private static final class ThrowingProbe extends AbstractAgentNode {
        @Override
        protected Map<String, Object> doApply(WorkflowState state, NodeContext ctx) {
            throw new RuntimeException("boom");
        }

        @Override
        protected String stepLabel() {
            return "[测试]";
        }
    }

    @Test
    void apply_success_recordsEndSnapshot() throws Exception {
        RecordingSink sink = new RecordingSink();
        AiInfra.bind(null, null, null, null, null, sink);
        try {
            new RecordingProbe().apply(state());

            assertEquals(1, sink.records.size());
            AgentExecution r = sink.records.getFirst();
            assertEquals(AgentExecution.Kind.NODE, r.kind());
            assertEquals("RecordingProbe", r.nodeName());
            assertEquals("END", r.phase());
            assertEquals(1L, r.taskId());
            assertEquals(SESSION_ID, r.sessionId());
            assertNull(r.toolName());
            assertNotNull(r.inputJson());
            assertNotNull(r.outputJson());
            assertNotNull(r.stateJson());
            assertNull(r.errorMsg());
            assertTrue(r.costMs() >= 0);
        } finally {
            AiInfra.bind(null, null, null, null, null, null);
        }
    }

    @Test
    void apply_exception_recordsErrorSnapshot() {
        RecordingSink sink = new RecordingSink();
        AiInfra.bind(null, null, null, null, null, sink);
        try {
            assertThrows(RuntimeException.class, () -> new ThrowingProbe().apply(state()));

            assertEquals(1, sink.records.size());
            AgentExecution r = sink.records.getFirst();
            assertEquals(AgentExecution.Kind.NODE, r.kind());
            assertEquals("ThrowingProbe", r.nodeName());
            assertEquals("ERROR", r.phase());
            assertEquals(SESSION_ID, r.sessionId());
            assertNull(r.outputJson());
            assertEquals("boom", r.errorMsg());
        } finally {
            AiInfra.bind(null, null, null, null, null, null);
        }
    }
}
