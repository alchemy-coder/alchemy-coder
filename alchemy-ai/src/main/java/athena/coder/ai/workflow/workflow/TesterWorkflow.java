package athena.coder.ai.workflow.workflow;

import athena.coder.ai.assistant.agent.result.router.WorkflowMode;
import athena.coder.ai.workflow.node.AnalystNode;
import athena.coder.ai.workflow.node.ReportNode;
import athena.coder.ai.workflow.node.ReviewNode;
import athena.coder.ai.workflow.node.TestNode;
import athena.coder.ai.workflow.node.WriterNode;
import org.bsc.langgraph4j.GraphStateException;

/**
 * 测试补全工作流（TEST_WORKFLOW）
 * <p>
 * 拓扑与编码工作流完全同构（补测试同样需要 编写→执行→失败分析→审查→总结 的完整闭环），
 * 差异全部收敛到 {@code XxxNode.test()} 的配置（如禁止改被测业务代码、区分"测试写错"与"代码有 bug"）。
 * 规划职责已上收主图（PLANNER + PLAN_CONFIRM 人工确认）。
 */
public class TesterWorkflow extends AbstractSubWorkflow {

    @Override
    protected String workflowName() {
        return WorkflowMode.TEST_WORKFLOW.label();
    }

    @Override
    protected void buildGraph(GraphDSL g) throws GraphStateException {
        buildQualityLoop(g, new QualityLoopNodes(
                WriterNode.test(), TestNode.test(), AnalystNode.test(),
                ReviewNode.test(), ReportNode.test()));
    }
}
