package athena.coder.ai.workflow.workflow;

import athena.coder.ai.spi.ErrorLogger;
import athena.coder.ai.workflow.entity.WorkflowState;
import athena.coder.ai.workflow.report.ReportFormatter;
import athena.coder.entity.chat.ChatEnum;
import athena.coder.exception.RocAgentException;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncEdgeAction;
import org.bsc.langgraph4j.action.NodeAction;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

import static athena.coder.ai.workflow.entity.NodeEnum.CODER;
import static athena.coder.ai.workflow.entity.NodeEnum.DEBUGGER;
import static athena.coder.ai.workflow.entity.NodeEnum.REVIEWER;
import static athena.coder.ai.workflow.entity.NodeEnum.SUMMARIZER;
import static athena.coder.ai.workflow.entity.NodeEnum.TESTER;
import static athena.coder.ai.workflow.entity.WorkflowState.NEXT_NODE;
import static athena.coder.ai.workflow.entity.WorkflowState.SUMMARIZE_RESULT;
import static org.bsc.langgraph4j.GraphDefinition.END;

/**
 * 子工作流模板基类
 * <p>
 * 职责（模板方法 {@link #apply}）：
 * 1. 创建 {@link GraphDSL}，交由子类 {@link #buildGraph} 完成节点与边的编排（构建期校验边目标）
 * 2. compile + invoke，以主图 state.data() 作为子图初始状态（透传 TASK_ID 等必填字段）；
 *    图每次请求构建并 compile，不做缓存
 * 3. 异常兜底：子图执行失败时输出 UI 提示并返回空 Map，保证主图能正常走到 END
 * 4. {@link #collectResults}：把子图产物 merge 回主图，最终报告渲染委托 {@link ReportFormatter}
 * <p>
 * 图编排 DSL 配套工具：{@link #routeBySignal()}/{@link #selfTargets}（边注册与校验见 {@link GraphDSL}），
 * 同构质量闭环拓扑见 {@link #buildQualityLoop}
 */
public abstract class AbstractSubWorkflow implements NodeAction<WorkflowState> {

    protected final Logger log = Logger.getLogger(getClass().getName());

    /**
     * 子工作流名称（用于日志与 UI 提示）
     */
    protected abstract String workflowName();

    /**
     * 子类完成子图的节点注册与流程编排
     */
    protected abstract void buildGraph(GraphDSL g) throws GraphStateException;

    @Override
    public Map<String, Object> apply(WorkflowState state) throws Exception {
        if (state == null) {
            throw new RocAgentException(getClass().getSimpleName() + ": state 不能为 null");
        }
        long startMs = System.currentTimeMillis();
        log.info("▶ [" + workflowName() + "] 子工作流启动, taskId=" + state.getTaskId());

        try {
            GraphDSL g = new GraphDSL(new StateGraph<>(WorkflowState::new));
            buildGraph(g);

            CompiledGraph<WorkflowState> compiledGraph = g.compile();
            Optional<WorkflowState> finalState = compiledGraph.invoke(state.data());

            long costMs = System.currentTimeMillis() - startMs;
            if (finalState.isEmpty()) {
                ErrorLogger.warn(workflowName(), "执行结束但未返回最终状态，耗时 " + costMs + "ms");
                state.outputBotResponse("[警告] " + workflowName() + " 执行结束，但未获取到执行结果", ChatEnum.ROBOT_ERROR);
                return Map.of();
            }
            log.info(String.format("■ [%s] 子工作流执行完成，耗时 %dms", workflowName(), costMs));
            return collectResults(state, finalState.get());
        } catch (Exception e) {
            long costMs = System.currentTimeMillis() - startMs;
            ErrorLogger.log(workflowName(), e, state.getTaskId(), null, null);
            state.outputBotResponse("[失败] " + workflowName() + " 执行失败: " + e.getMessage(), ChatEnum.ROBOT_ERROR);
            return Map.of();
        }
    }

    /**
     * 输出最终 UI 结果；子图产物经 side-effect 落盘，无需 merge 回主图
     */
    private Map<String, Object> collectResults(WorkflowState masterState, WorkflowState subState) {
        Object summarizeResult = subState.data().get(SUMMARIZE_RESULT);
        if (summarizeResult != null) {
            String formattedReport = ReportFormatter.format(subState, String.valueOf(summarizeResult), workflowName());
            masterState.outputBotResponse(formattedReport, ChatEnum.ROBOT_REPORT);
        } else {
            ErrorLogger.warn(workflowName(), "提前终止，未生成最终总结报告");
            masterState.outputBotResponse("[警告] " + workflowName() + " 提前结束，未生成最终总结报告，请查看日志确认原因", ChatEnum.ROBOT_ERROR);
        }
        return Map.of();
    }

    // ===== 同构质量闭环拓扑（编码/测试补全工作流共用）=====

    /**
     * 质量闭环五节点实例
     */
    protected record QualityLoopNodes(NodeAction<WorkflowState> coder,
                                      NodeAction<WorkflowState> tester,
                                      NodeAction<WorkflowState> debugger,
                                      NodeAction<WorkflowState> reviewer,
                                      NodeAction<WorkflowState> summarizer) {
    }

    /**
     * 完整质量闭环拓扑：
     * START → CODER → TESTER ─ PASS/SKIP → REVIEWER ─ 通过 → SUMMARIZER → END
     *           ↑         └─ FAIL/ERROR → DEBUGGER ──┐        ↑
     *           └────────── 修复策略回 CODER ←────────┘（升级/熔断 → SUMMARIZER）
     *           └────────── REVIEWER 打回（REQUEST_CHANGES，超限熔断 → SUMMARIZER）
     */
    protected final void buildQualityLoop(GraphDSL g, QualityLoopNodes n) throws GraphStateException {
        g.node(CODER, n.coder());
        g.node(TESTER, n.tester());
        g.node(DEBUGGER, n.debugger());
        g.node(REVIEWER, n.reviewer());
        g.node(SUMMARIZER, n.summarizer());

        g.fromStart(CODER);
        // CODER：不写路由信号，固定走 TESTER（失败直接抛出，由基类统一收口）
        g.edge(CODER, TESTER);
        // TESTER：PASS/SKIP → REVIEWER，FAIL/ERROR → DEBUGGER
        g.route(TESTER, routeBySignal(), selfTargets(REVIEWER, DEBUGGER));
        // DEBUGGER：修复策略回 CODER，升级/熔断 → SUMMARIZER
        g.route(DEBUGGER, routeBySignal(), selfTargets(CODER, SUMMARIZER));
        // REVIEWER：通过/熔断/BLOCKED → SUMMARIZER，打回 → CODER
        g.route(REVIEWER, routeBySignal(), selfTargets(SUMMARIZER, CODER));
        // SUMMARIZER 收尾
        g.toEnd(SUMMARIZER);
    }

    // ===== 图编排 DSL 配套工具 =====

    /**
     * 严格按 NEXT_NODE 字符串信号分流，信号缺失时走 END
     */
    public static AsyncEdgeAction<WorkflowState> routeBySignal() {
        return AsyncEdgeAction.edge_async(state ->
                state.value(NEXT_NODE).map(String::valueOf).orElse(END));
    }

    /**
     * 生成自映射的条件边目标表（信号值即目标节点名），自动附加 END 兜底映射，
     * 消除成对的 X.name(), X.name() 样板代码
     */
    public static Map<String, String> selfTargets(Enum<?>... nodes) {
        Map<String, String> targets = new HashMap<>(nodes.length + 1, 1.0f);
        for (Enum<?> node : nodes) {
            targets.put(node.name(), node.name());
        }
        targets.put(END, END);
        return targets;
    }
}
