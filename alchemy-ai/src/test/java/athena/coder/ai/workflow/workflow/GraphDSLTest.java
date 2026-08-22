package athena.coder.ai.workflow.workflow;

import athena.coder.ai.workflow.entity.WorkflowState;
import athena.coder.entity.chat.ChatEnum;
import athena.coder.entity.model.LLMModelEnum;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

import static org.bsc.langgraph4j.GraphDefinition.END;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GraphDSLTest {

    private enum N { A, B, C }

    private static WorkflowState state(Map<String, Object> extra) {
        Map<String, Object> m = new HashMap<>();
        m.put(WorkflowState.INIT_TASK_ID, 1L);
        m.put(WorkflowState.INIT_WORK_FULL_PATH, "/tmp/proj");
        m.put(WorkflowState.INIT_USER_MESSAGE, "x");
        m.put(WorkflowState.INIT_MODEL_TYPE, LLMModelEnum.QIANWEN37MAX);
        m.put(WorkflowState.INIT_BOT_RESPONSE, (BiConsumer<String, ChatEnum>) (msg, type) -> {});
        if (extra != null) {
            m.putAll(extra);
        }
        return new WorkflowState(m);
    }

    private static GraphDSL newGraph() {
        return new GraphDSL(new StateGraph<>(WorkflowState::new));
    }

    @Test
    void node_edge_toEnd_compiles() throws GraphStateException {
        GraphDSL g = newGraph();
        g.node(N.A, s -> Map.of());
        g.node(N.B, s -> Map.of());
        g.fromStart(N.A);
        g.edge(N.A, N.B);
        g.toEnd(N.B);
        assertDoesNotThrow(g::compile);
    }

    @Test
    void edge_unregisteredTarget_throws() throws GraphStateException {
        GraphDSL g = newGraph();
        g.node(N.A, s -> Map.of());
        assertThrows(GraphStateException.class, () -> g.edge(N.A, N.B));
    }

    @Test
    void fromStart_unregistered_throws() {
        GraphDSL g = newGraph();
        assertThrows(GraphStateException.class, () -> g.fromStart(N.A));
    }

    @Test
    void route_unregisteredTarget_throws() throws GraphStateException {
        GraphDSL g = newGraph();
        g.node(N.A, s -> Map.of());
        g.node(N.B, s -> Map.of());
        assertThrows(GraphStateException.class,
                () -> g.route(N.A, GraphDSL.routeBySignal(), GraphDSL.selfTargets(N.C)));
    }

    @Test
    void selfTargets_selfMapPlusEndFallback() {
        Map<String, String> targets = GraphDSL.selfTargets(N.A, N.B);
        assertEquals("A", targets.get("A"));
        assertEquals("B", targets.get("B"));
        assertEquals(END, targets.get(END));
    }

    @Test
    void routeBySignal_present_returnsSignal() throws Exception {
        String target = GraphDSL.routeBySignal()
                .apply(state(Map.of(WorkflowState.NEXT_NODE, "B")))
                .get();
        assertEquals("B", target);
    }

    @Test
    void routeBySignal_missing_returnsEnd() throws Exception {
        String target = GraphDSL.routeBySignal().apply(state(null)).get();
        assertEquals(END, target);
    }
}
