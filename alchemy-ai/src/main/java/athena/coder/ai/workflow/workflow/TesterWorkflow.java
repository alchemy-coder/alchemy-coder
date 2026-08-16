package athena.coder.ai.workflow.workflow;

import athena.coder.ai.workflow.node.test.TestFixAnalystNode;
import athena.coder.ai.workflow.node.test.TestReportNode;
import athena.coder.ai.workflow.node.test.TestReviewNode;
import athena.coder.ai.workflow.node.test.TestRunNode;
import athena.coder.ai.workflow.node.test.TestWriteNode;
import org.bsc.langgraph4j.GraphStateException;

/**
 * 测试补全工作流（TEST_WORKFLOW）
 * <p>
 * 拓扑与编码工作流完全同构（补测试同样需要 编写→执行→失败分析→审查→总结 的完整闭环），
 * 但节点全部使用测试补全工作流专属实现（agent/test + node/test）——拓扑同构不等于实现复用，
 * 各环节提示词按补测使命独立编写（如禁止改被测业务代码、区分"测试写错"与"代码有 bug"）
 * <p>
 * 规划职责已上收主图（PLANNER + PLAN_CONFIRM 人工确认），本工作流以 CODER 起步，
 * 经确认的 PLAN/ACCEPTANCE_CRITERIA 随主图 state 透传进入
 */
public class TesterWorkflow extends AbstractSubWorkflow {

    @Override
    protected String workflowName() {
        return "测试补全工作流";
    }

    @Override
    protected void buildGraph(GraphDSL g) throws GraphStateException {
        buildQualityLoop(g, new QualityLoopNodes(
                new TestWriteNode(), new TestRunNode(), new TestFixAnalystNode(),
                new TestReviewNode(), new TestReportNode()));
    }
}
