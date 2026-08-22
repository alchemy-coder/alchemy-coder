package athena.coder.ai.workflow.workflow;

import athena.coder.ai.spi.ErrorLogger;
import athena.coder.ai.workflow.entity.WorkflowState;
import athena.coder.ai.workflow.report.ReportFormatter;
import athena.coder.entity.chat.ChatEnum;
import athena.coder.exception.RocAgentException;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.NodeAction;

import java.util.Map;
import java.util.Optional;

import static athena.coder.ai.workflow.entity.NodeEnum.CODER;
import static athena.coder.ai.workflow.entity.NodeEnum.DEBUGGER;
import static athena.coder.ai.workflow.entity.NodeEnum.REVIEWER;
import static athena.coder.ai.workflow.entity.NodeEnum.SUMMARIZER;
import static athena.coder.ai.workflow.entity.NodeEnum.TESTER;
import static athena.coder.ai.workflow.entity.WorkflowState.SUMMARIZE_RESULT;

/**
 * 子工作流模板基类
 * <p>
 * 职责（模板方法 {@link #apply}）：
 * 1. 创建 {@link GraphDSL}，交由子类 {@link #buildGraph} 完成节点与边的编排（构建期校验边目标）
 * 2. compile + invoke，以主图 state.data() 作为子图初始状态（透传 TASK_ID 等必填字段）；
 *    图每次请求构建并 compile，不做缓存
 * 3. 异常兜底：子图执行失败时输出 UI 提示并返回空 Map，保证主图能正常走到 END
 * 4. {@link #renderFinalReport}：子图产物经 side-effect 落盘（ROBOT_REPORT）输出，不 merge 回主图；
 *    最终报告渲染委托 {@link ReportFormatter}
 * <p>
 * 图编排配套工具（{@link GraphDSL#routeBySignal()}/{@link GraphDSL#selfTargets}），
 * 同构质量闭环拓扑见 {@link #buildQualityLoop}
 */
public abstract class AbstractSubWorkflow implements NodeAction<WorkflowState> {

    /**
     * 子工作流名称（用于 UI 提示）
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

        GraphDSL g = new GraphDSL(new StateGraph<>(WorkflowState::new));
        CompiledGraph<WorkflowState> compiledGraph;
        try {
            buildGraph(g);
            compiledGraph = g.compile();
        } catch (Exception e) {
            ErrorLogger.log(workflowName(), e, state.getTaskId(), null, null);
            state.outputBotResponse("[失败] " + workflowName() + " 图构建失败: " + e.getMessage(), ChatEnum.ROBOT_ERROR);
            return Map.of();
        }

        Optional<WorkflowState> finalState;
        try {
            finalState = compiledGraph.invoke(state.data());
        } catch (Exception e) {
            // 节点执行异常已由 AbstractAgentNode.apply 统一记录（含 nodeName 上下文），
            // 此处仅输出用户提示，避免同一 taskId 产生双份 ERROR 日志。
            state.outputBotResponse("[失败] " + workflowName() + " 执行失败: " + e.getMessage(), ChatEnum.ROBOT_ERROR);
            return Map.of();
        }

        long costMs = System.currentTimeMillis() - startMs;
        if (finalState.isEmpty()) {
            ErrorLogger.warn(workflowName(), "执行结束但未返回最终状态，耗时 " + costMs + "ms");
            state.outputBotResponse("[警告] " + workflowName() + " 执行结束，但未获取到执行结果", ChatEnum.ROBOT_ERROR);
            return Map.of();
        }
        return renderFinalReport(state, finalState.get());
    }

    /**
     * 输出最终 UI 结果：子图产物经 side-effect（ROBOT_REPORT）落盘，无需 merge 回主图
     */
    private Map<String, Object> renderFinalReport(WorkflowState masterState, WorkflowState subState) {
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

    // ===== 同构质量闭环拓扑（编码/测试补全/缺陷修复工作流共用）=====

    /**
     * 质量闭环节点实例；reviewer 仅在 {@code withReviewer=true} 时注册，否则可传 null
     */
    protected record QualityLoopNodes(NodeAction<WorkflowState> coder,
                                      NodeAction<WorkflowState> tester,
                                      NodeAction<WorkflowState> debugger,
                                      NodeAction<WorkflowState> reviewer,
                                      NodeAction<WorkflowState> summarizer) {
    }

    /**
     * 质量闭环拓扑（含审查与否两种变体）：
     * START → CODER → TESTER ─ PASS/SKIP → (REVIEWER | SUMMARIZER) ─ 通过 → SUMMARIZER → END
     *           ↑         └─ FAIL/ERROR → DEBUGGER ──┐
     *           └────────── 修复策略回 CODER ←────────┘（升级/熔断 → SUMMARIZER）
     *           └────────── REVIEWER 打回（REQUEST_CHANGES，超限熔断 → SUMMARIZER）
     *
     * @param withReviewer true=完整闭环（含 REVIEWER），false=跳过审查（缺陷修复以测试通过为准）
     */
    protected final void buildQualityLoop(GraphDSL g, QualityLoopNodes n, boolean withReviewer) throws GraphStateException {
        g.node(CODER, n.coder());
        g.node(TESTER, n.tester());
        g.node(DEBUGGER, n.debugger());
        if (withReviewer) {
            g.node(REVIEWER, n.reviewer());
        }
        g.node(SUMMARIZER, n.summarizer());

        g.fromStart(CODER);
        // CODER：不写路由信号，固定走 TESTER（失败直接抛出，由基类统一收口）
        g.edge(CODER, TESTER);
        // TESTER：PASS/SKIP → REVIEWER（含审查）或 SUMMARIZER（跳过审查），FAIL/ERROR → DEBUGGER
        g.route(TESTER, GraphDSL.routeBySignal(), GraphDSL.selfTargets(withReviewer ? REVIEWER : SUMMARIZER, DEBUGGER));
        // DEBUGGER：修复策略回 CODER，升级/熔断 → SUMMARIZER
        g.route(DEBUGGER, GraphDSL.routeBySignal(), GraphDSL.selfTargets(CODER, SUMMARIZER));
        if (withReviewer) {
            // REVIEWER：通过/熔断/BLOCKED → SUMMARIZER，打回 → CODER
            g.route(REVIEWER, GraphDSL.routeBySignal(), GraphDSL.selfTargets(SUMMARIZER, CODER));
        }
        // SUMMARIZER 收尾
        g.toEnd(SUMMARIZER);
    }
}
