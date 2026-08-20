package athena.coder.ai.tool.base;

import athena.coder.ai.spi.AgentExecution;
import athena.coder.ai.spi.AgentExecutionSink;
import athena.coder.ai.spi.AiInfra;
import athena.coder.ai.tool.exception.ToolExecutionException;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.service.tool.ToolExecutor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolExecutionPersistenceTest {

    private static final long TASK_ID = 7L;
    private static final String SESSION_ID = "session-uuid-456";
    private static final String NODE_NAME = "CoderNode";
    private static final String TOOL_NAME = "FileOperationTool.readFile";

    private static final class RecordingSink implements AgentExecutionSink {
        final List<AgentExecution> records = new ArrayList<>();

        @Override
        public void record(AgentExecution execution) {
            records.add(execution);
        }
    }

    @AfterEach
    void cleanup() {
        ToolInvocationLogger.clearExecContext();
        AiInfra.bind(null, null, null, null, null, null);
    }

    private static ToolExecutionRequest request() {
        return ToolExecutionRequest.builder()
                .name(TOOL_NAME)
                .arguments("{\"filePath\":\"a.txt\"}")
                .build();
    }

    @Test
    void execute_success_recordsToolExecution() {
        RecordingSink sink = new RecordingSink();
        AiInfra.bind(null, null, null, null, null, sink);
        ToolInvocationLogger.setExecContext(TASK_ID, SESSION_ID, NODE_NAME);

        ToolExecutor delegate = (req, memoryId) -> "ok";
        String result = new ToolInvocationLogger(delegate, TOOL_NAME).execute(request(), null);

        assertEquals("ok", result);
        assertEquals(1, sink.records.size());
        AgentExecution r = sink.records.getFirst();
        assertEquals(AgentExecution.Kind.TOOL, r.kind());
        assertEquals(TASK_ID, r.taskId());
        assertEquals(SESSION_ID, r.sessionId());
        assertEquals(NODE_NAME, r.nodeName());
        assertEquals(TOOL_NAME, r.toolName());
        assertEquals("{\"filePath\":\"a.txt\"}", r.inputJson());
        assertEquals("ok", r.outputJson());
        assertNull(r.phase());
        assertNull(r.stateJson());
        assertNull(r.errorMsg());
        assertTrue(r.costMs() >= 0);
    }

    @Test
    void execute_longResult_truncatedTo2000() {
        RecordingSink sink = new RecordingSink();
        AiInfra.bind(null, null, null, null, null, sink);
        ToolInvocationLogger.setExecContext(TASK_ID, SESSION_ID, NODE_NAME);

        String longResult = "x".repeat(3000);
        ToolExecutor delegate = (req, memoryId) -> longResult;
        new ToolInvocationLogger(delegate, TOOL_NAME).execute(request(), null);

        assertEquals(1, sink.records.size());
        String recorded = sink.records.getFirst().outputJson();
        assertEquals(2000 + "...[截断]".length(), recorded.length());
        assertTrue(recorded.startsWith("x".repeat(2000)));
        assertTrue(recorded.endsWith("...[截断]"));
    }

    @Test
    void execute_exception_recordsErrorAndThrows() {
        RecordingSink sink = new RecordingSink();
        AiInfra.bind(null, null, null, null, null, sink);
        ToolInvocationLogger.setExecContext(TASK_ID, SESSION_ID, NODE_NAME);

        ToolExecutor delegate = (req, memoryId) -> {
            throw new RuntimeException("tool failed");
        };

        assertThrows(ToolExecutionException.class,
                () -> new ToolInvocationLogger(delegate, TOOL_NAME).execute(request(), null));

        assertEquals(1, sink.records.size());
        AgentExecution r = sink.records.getFirst();
        assertNull(r.outputJson());
        assertEquals("tool failed", r.errorMsg());
    }

    @Test
    void execute_noSink_doesNotThrow() {
        // 未装配 sink（agentExecutions 为 null）时静默跳过，不阻断工具执行
        AiInfra.bind(null, null, null, null, null, null);
        ToolInvocationLogger.setExecContext(TASK_ID, SESSION_ID, NODE_NAME);

        ToolExecutor delegate = (req, memoryId) -> "ok";
        String result = new ToolInvocationLogger(delegate, TOOL_NAME).execute(request(), null);
        assertEquals("ok", result);
    }
}
