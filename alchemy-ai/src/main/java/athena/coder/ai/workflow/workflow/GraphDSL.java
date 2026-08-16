package athena.coder.ai.workflow.workflow;

import athena.coder.ai.workflow.entity.WorkflowState;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncEdgeAction;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.action.NodeAction;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.bsc.langgraph4j.GraphDefinition.END;
import static org.bsc.langgraph4j.GraphDefinition.START;

/**
 * 图编排 DSL：包装 {@link StateGraph}，登记已注册节点并在构建期校验边目标，
 * 路由目标写错（未注册节点）在构图时即抛 {@link GraphStateException}，不再等到运行时。
 */
public final class GraphDSL {

    private final StateGraph<WorkflowState> graph;
    private final Set<String> registered = new HashSet<>();

    public GraphDSL(StateGraph<WorkflowState> graph) {
        this.graph = graph;
    }

    /**
     * 注册节点
     */
    public void node(Enum<?> name, NodeAction<WorkflowState> action) throws GraphStateException {
        graph.addNode(name.name(), AsyncNodeAction.node_async(action));
        registered.add(name.name());
    }

    /**
     * START → to
     */
    public void fromStart(Enum<?> to) throws GraphStateException {
        requireRegistered(to);
        graph.addEdge(START, to.name());
    }

    /**
     * 静态边 from → to
     */
    public void edge(Enum<?> from, Enum<?> to) throws GraphStateException {
        requireRegistered(from);
        requireRegistered(to);
        graph.addEdge(from.name(), to.name());
    }

    /**
     * from → END
     */
    public void toEnd(Enum<?> from) throws GraphStateException {
        requireRegistered(from);
        graph.addEdge(from.name(), END);
    }

    /**
     * 条件边：校验 targets 全部 value ∈ 已注册节点 ∪ {END}，否则构建期抛异常
     */
    public void route(Enum<?> from, AsyncEdgeAction<WorkflowState> action, Map<String, String> targets)
            throws GraphStateException {
        requireRegistered(from);
        for (Map.Entry<String, String> entry : targets.entrySet()) {
            String target = entry.getValue();
            if (!registered.contains(target) && !END.equals(target)) {
                throw new GraphStateException(
                        "条件边 " + from.name() + " 的目标节点未注册: " + target + "（信号: " + entry.getKey() + "）");
            }
        }
        graph.addConditionalEdges(from.name(), action, targets);
    }

    public CompiledGraph<WorkflowState> compile() throws GraphStateException {
        return graph.compile();
    }

    private void requireRegistered(Enum<?> name) throws GraphStateException {
        if (!registered.contains(name.name())) {
            throw new GraphStateException("引用了未注册的节点: " + name.name());
        }
    }
}
