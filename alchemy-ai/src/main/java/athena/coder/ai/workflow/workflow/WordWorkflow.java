package athena.coder.ai.workflow.workflow;

import athena.coder.ai.workflow.node.ReportNode;
import athena.coder.ai.workflow.node.ReviewNode;
import athena.coder.ai.workflow.node.WriterNode;
import org.bsc.langgraph4j.GraphStateException;

import static athena.coder.ai.workflow.entity.NodeEnum.CODER;
import static athena.coder.ai.workflow.entity.NodeEnum.REVIEWER;
import static athena.coder.ai.workflow.entity.NodeEnum.SUMMARIZER;

/**
 * 文档工作流（WORD_WORKFLOW）
 * <p>
 * 面向注释/文档/README 等非可执行产物的变更：无 TESTER/DEBUGGER 环节，
 * 编写完成后直接进入审查；节点由 {@code XxxNode.doc()} 配置驱动。
 * <p>
 * 拓扑：
 * START → CODER → REVIEWER ─ 通过/熔断 → SUMMARIZER → END
 *           ↑          │
 *           └── 打回（REQUEST_CHANGES）
 * <p>
 * 规划职责已上收主图（PLANNER + PLAN_CONFIRM 人工确认）。
 */
public class WordWorkflow extends AbstractSubWorkflow {

    @Override
    protected String workflowName() {
        return "文档工作流";
    }

    @Override
    protected void buildGraph(GraphDSL g) throws GraphStateException {
        g.node(CODER, WriterNode.doc());
        g.node(REVIEWER, ReviewNode.doc());
        g.node(SUMMARIZER, ReportNode.doc());

        g.fromStart(CODER);
        // CODER：不写路由信号，固定走 REVIEWER（失败直接抛出，由子工作流基类统一收口）
        g.edge(CODER, REVIEWER);
        // REVIEWER：通过/熔断/BLOCKED → SUMMARIZER，打回 → CODER
        g.route(REVIEWER, routeBySignal(), selfTargets(SUMMARIZER, CODER));
        // SUMMARIZER 收尾
        g.toEnd(SUMMARIZER);
    }
}
