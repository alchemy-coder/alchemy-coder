package athena.coder.ai.workflow.workflow;

import athena.coder.ai.workflow.node.word.DocReportNode;
import athena.coder.ai.workflow.node.word.DocReviewNode;
import athena.coder.ai.workflow.node.word.DocWriteNode;
import org.bsc.langgraph4j.GraphStateException;

import static athena.coder.ai.workflow.entity.NodeEnum.CODER;
import static athena.coder.ai.workflow.entity.NodeEnum.REVIEWER;
import static athena.coder.ai.workflow.entity.NodeEnum.SUMMARIZER;

/**
 * 文档工作流（WORD_WORKFLOW）
 * <p>
 * 面向注释/文档/README 等非可执行产物的变更：无 TESTER/DEBUGGER 环节，
 * 编写完成后直接进入审查；节点全部使用文档工作流专属实现（agent/word + node/word）
 * <p>
 * 拓扑：
 * START → CODER → REVIEWER ─ 通过/熔断 → SUMMARIZER → END
 *           ↑          │
 *           └── 打回（REQUEST_CHANGES）
 * <p>
 * 规划职责已上收主图（PLANNER + PLAN_CONFIRM 人工确认），本工作流以 CODER 起步，
 * 经确认的 PLAN 随主图 state 透传进入
 */
public class WordWorkflow extends AbstractSubWorkflow {

    @Override
    protected String workflowName() {
        return "文档工作流";
    }

    @Override
    protected void buildGraph(GraphDSL g) throws GraphStateException {
        //注册节点（无 PLANNER/TESTER/DEBUGGER；规划由主图完成；全部为文档工作流专属节点）
        g.node(CODER, new DocWriteNode());
        g.node(REVIEWER, new DocReviewNode());
        g.node(SUMMARIZER, new DocReportNode());

        //流程编排
        g.fromStart(CODER);
        //CODER：不写路由信号，固定走 REVIEWER（失败直接抛出，由子工作流基类统一收口）
        g.edge(CODER, REVIEWER);
        //REVIEWER：通过/熔断/BLOCKED → SUMMARIZER，打回 → CODER
        g.route(REVIEWER, routeBySignal(), selfTargets(SUMMARIZER, CODER));
        //SUMMARIZER 收尾
        g.toEnd(SUMMARIZER);
    }
}
