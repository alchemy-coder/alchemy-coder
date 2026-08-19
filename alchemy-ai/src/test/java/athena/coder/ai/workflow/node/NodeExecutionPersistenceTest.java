package athena.coder.ai.workflow.node;

import athena.coder.ai.spi.AiInfra;
import athena.coder.ai.spi.NodeExecutionRecord;
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

    private static WorkflowState state() {
        Map<String, Object> m = new HashMap<>();
        m.put(WorkflowState.INIT_TASK_ID, 1L);
        m.put(WorkflowState.INIT_WORK_FULL_PATH, System.getProperty("user.dir"));
        m.put(WorkflowState.INIT_USER_MESSAGE, "实现登录");
        m.put(WorkflowState.INIT_MODEL_TYPE, ModelEnum.QIANWEN37MAX);
        m.put(WorkflowState.INIT_BOT_RESPONSE, (BiConsumer<String, ChatEnum>) (msg, type) -> {});
        return new WorkflowState(m);
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
        List<NodeExecutionRecord> records = new ArrayList<>();
        AiInfra.bind(null, null, null, null, null, records::add);
        try {
            new RecordingProbe().apply(state());

            assertEquals(1, records.size());
            NodeExecutionRecord r = records.getFirst();
            assertEquals("RecordingProbe", r.nodeName());
            assertEquals("END", r.phase());
            assertEquals(1L, r.taskId());
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
        List<NodeExecutionRecord> records = new ArrayList<>();
        AiInfra.bind(null, null, null, null, null, records::add);
        try {
            assertThrows(RuntimeException.class, () -> new ThrowingProbe().apply(state()));

            assertEquals(1, records.size());
            NodeExecutionRecord r = records.getFirst();
            assertEquals("ThrowingProbe", r.nodeName());
            assertEquals("ERROR", r.phase());
            assertNull(r.outputJson());
            assertEquals("boom", r.errorMsg());
        } finally {
            AiInfra.bind(null, null, null, null, null, null);
        }
    }
}
