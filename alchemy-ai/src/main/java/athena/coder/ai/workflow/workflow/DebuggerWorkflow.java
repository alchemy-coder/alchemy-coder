package athena.coder.ai.workflow.workflow;

import athena.coder.ai.workflow.node.AnalystNode;
import athena.coder.ai.workflow.node.ReportNode;
import athena.coder.ai.workflow.node.TestNode;
import athena.coder.ai.workflow.node.WriterNode;
import org.bsc.langgraph4j.GraphStateException;

import static athena.coder.ai.workflow.entity.NodeEnum.CODER;
import static athena.coder.ai.workflow.entity.NodeEnum.DEBUGGER;
import static athena.coder.ai.workflow.entity.NodeEnum.SUMMARIZER;
import static athena.coder.ai.workflow.entity.NodeEnum.TESTER;

/**
 * 缺陷修复工作流（DEBUG_WORKFLOW）
 * <p>
 * 与编码工作流的差异：跳过 REVIEWER 审查环节（Bug 修复以测试通过为准），
 * 验证节点通过信号直接指向 SUMMARIZER；节点由 {@code XxxNode.fix()} 配置驱动。
 * <p>
 * 拓扑：
 * START → CODER → TESTER ─ PASS/SKIP → SUMMARIZER → END
 *           ↑         └─ FAIL/ERROR → DEBUGGER ──┐
 *           └────────── 修复策略回 CODER ←────────┘（升级/熔断 → SUMMARIZER）
 * <p>
 * 规划职责已上收主图（PLANNER + PLAN_CONFIRM 人工确认）。
 */
public class DebuggerWorkflow extends AbstractSubWorkflow {

    @Override
    protected String workflowName() {
        return "缺陷修复工作流";
    }

    @Override
    protected void buildGraph(GraphDSL g) throws GraphStateException {
        g.node(CODER, WriterNode.fix());
        g.node(TESTER, TestNode.fix());
        g.node(DEBUGGER, AnalystNode.fix());
        g.node(SUMMARIZER, ReportNode.fix());

        g.fromStart(CODER);
        // CODER：不写路由信号，固定走 TESTER（失败直接抛出，由子工作流基类统一收口）
        g.edge(CODER, TESTER);
        // TESTER：PASS/SKIP → SUMMARIZER，FAIL/ERROR → DEBUGGER
        g.route(TESTER, routeBySignal(), selfTargets(SUMMARIZER, DEBUGGER));
        // DEBUGGER：修复策略回 CODER，升级/熔断 → SUMMARIZER
        g.route(DEBUGGER, routeBySignal(), selfTargets(CODER, SUMMARIZER));
        // SUMMARIZER 收尾
        g.toEnd(SUMMARIZER);
    }
}
