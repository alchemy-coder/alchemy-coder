package athena.coder.ai.workflow.workflow;

import athena.coder.ai.workflow.node.debug.FixAnalyzeNode;
import athena.coder.ai.workflow.node.debug.FixApplyNode;
import athena.coder.ai.workflow.node.debug.FixReportNode;
import athena.coder.ai.workflow.node.debug.FixVerifyNode;
import org.bsc.langgraph4j.GraphStateException;

import static athena.coder.ai.workflow.entity.NodeEnum.CODER;
import static athena.coder.ai.workflow.entity.NodeEnum.DEBUGGER;
import static athena.coder.ai.workflow.entity.NodeEnum.SUMMARIZER;
import static athena.coder.ai.workflow.entity.NodeEnum.TESTER;

/**
 * 缺陷修复工作流（DEBUG_WORKFLOW）
 * <p>
 * 与编码工作流的差异：跳过 REVIEWER 审查环节（Bug 修复以测试通过为准），
 * 验证节点（FixVerifyNode）通过信号直接指向 SUMMARIZER；
 * 节点全部使用缺陷修复工作流专属实现（agent/debug + node/debug）
 * <p>
 * 拓扑：
 * START → CODER → TESTER ─ PASS/SKIP → SUMMARIZER → END
 *           ↑         └─ FAIL/ERROR → DEBUGGER ──┐
 *           └────────── 修复策略回 CODER ←────────┘（升级/熔断 → SUMMARIZER）
 * <p>
 * 规划职责已上收主图（PLANNER + PLAN_CONFIRM 人工确认），本工作流以 CODER 起步，
 * 经确认的 PLAN 随主图 state 透传进入
 */
public class DebuggerWorkflow extends AbstractSubWorkflow {

    @Override
    protected String workflowName() {
        return "缺陷修复工作流";
    }

    @Override
    protected void buildGraph(GraphDSL g) throws GraphStateException {
        //注册节点（无 PLANNER/REVIEWER；规划由主图完成；全部为缺陷修复工作流专属节点）
        g.node(CODER, new FixApplyNode());
        g.node(TESTER, new FixVerifyNode());
        g.node(DEBUGGER, new FixAnalyzeNode());
        g.node(SUMMARIZER, new FixReportNode());

        //流程编排
        g.fromStart(CODER);
        //CODER：不写路由信号，固定走 TESTER（失败直接抛出，由子工作流基类统一收口）
        g.edge(CODER, TESTER);
        //TESTER：PASS/SKIP → SUMMARIZER，FAIL/ERROR → DEBUGGER
        g.route(TESTER, routeBySignal(), selfTargets(SUMMARIZER, DEBUGGER));
        //DEBUGGER：修复策略回 CODER，升级/熔断 → SUMMARIZER
        g.route(DEBUGGER, routeBySignal(), selfTargets(CODER, SUMMARIZER));
        //SUMMARIZER 收尾
        g.toEnd(SUMMARIZER);
    }
}
