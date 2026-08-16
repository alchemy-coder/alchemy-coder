package athena.coder.ai.workflow.workflow;

import athena.coder.ai.workflow.node.code.CodeFixAnalystNode;
import athena.coder.ai.workflow.node.code.CodeReportNode;
import athena.coder.ai.workflow.node.code.CodeReviewNode;
import athena.coder.ai.workflow.node.code.CodeTestNode;
import athena.coder.ai.workflow.node.code.CodeWriterNode;
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
 * 节点全部使用编码工作流专属实现（agent/code + node/code），与其他子流程零复用；
 * 角色枚举名（CODER/TESTER…）仅作子图内部拓扑命名与路由信号
 * <p>
 * 规划职责已上收主图（PLANNER + PLAN_CONFIRM 人工确认），本工作流以 CODER 起步，
 * 经确认的 PLAN/ACCEPTANCE_CRITERIA 随主图 state 透传进入
 * <p>
 * 熔断保护：DEBUGGER→CODER 回环上限 3 次，REVIEWER→CODER 打回上限 2 次，
 * 超限强制走 SUMMARIZER 收尾，保证用户始终能拿到执行报告
 */
public class CoderWorkflow extends AbstractSubWorkflow {

    @Override
    protected String workflowName() {
        return "编码工作流";
    }

    @Override
    protected void buildGraph(GraphDSL g) throws GraphStateException {
        buildQualityLoop(g, new QualityLoopNodes(
                new CodeWriterNode(), new CodeTestNode(), new CodeFixAnalystNode(),
                new CodeReviewNode(), new CodeReportNode()));
    }
}
