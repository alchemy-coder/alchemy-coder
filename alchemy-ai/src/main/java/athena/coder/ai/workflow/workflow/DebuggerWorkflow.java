package athena.coder.ai.workflow.workflow;

import athena.coder.ai.assistant.agent.result.router.WorkflowMode;
import athena.coder.ai.workflow.node.AnalystNode;
import athena.coder.ai.workflow.node.ReportNode;
import athena.coder.ai.workflow.node.TestNode;
import athena.coder.ai.workflow.node.WriterNode;
import org.bsc.langgraph4j.GraphStateException;

/**
 * 缺陷修复工作流（DEBUG_WORKFLOW）
 * <p>
 * 与编码工作流的差异：跳过 REVIEWER 审查环节（Bug 修复以测试通过为准），
 * 复用 {@link #buildQualityLoop} 的 withReviewer=false 变体；节点由 {@code XxxNode.fix()} 配置驱动。
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
        return WorkflowMode.DEBUG_WORKFLOW.label();
    }

    @Override
    protected void buildGraph(GraphDSL g) throws GraphStateException {
        buildQualityLoop(g, new QualityLoopNodes(
                WriterNode.fix(), TestNode.fix(), AnalystNode.fix(), null, ReportNode.fix()), false);
    }
}
