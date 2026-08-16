package athena.coder.ai.workflow.workflow;

import athena.coder.ai.workflow.node.AnalystNode;
import athena.coder.ai.workflow.node.ReportNode;
import athena.coder.ai.workflow.node.ReviewNode;
import athena.coder.ai.workflow.node.TestNode;
import athena.coder.ai.workflow.node.WriterNode;
import org.bsc.langgraph4j.GraphStateException;

/**
 * 编码工作流（CODE_WORKFLOW）- 完整质量闭环
 * <p>
 * 拓扑（{@link #buildQualityLoop}）：
 * START → CODER → TESTER ─ PASS/SKIP → REVIEWER ─ 通过 → SUMMARIZER → END
 *           ↑         └─ FAIL/ERROR → DEBUGGER ──┐        ↑
 *           └────────── 修复策略回 CODER ←────────┘（升级/熔断 → SUMMARIZER）
 *           └────────── REVIEWER 打回（REQUEST_CHANGES，超限熔断 → SUMMARIZER）
 * <p>
 * 五节点全部配置驱动（{@code XxxNode.code()}），角色枚举名（CODER/TESTER…）仅作子图内部拓扑命名；
 * 规划职责已上收主图（PLANNER + PLAN_CONFIRM 人工确认），经确认的 PLAN/ACCEPTANCE_CRITERIA 随主图 state 透传进入。
 */
public class CoderWorkflow extends AbstractSubWorkflow {

    @Override
    protected String workflowName() {
        return "编码工作流";
    }

    @Override
    protected void buildGraph(GraphDSL g) throws GraphStateException {
        buildQualityLoop(g, new QualityLoopNodes(
                WriterNode.code(), TestNode.code(), AnalystNode.code(),
                ReviewNode.code(), ReportNode.code()));
    }
}
