package athena.coder.ai.workflow;

import athena.coder.ai.assistant.agent.result.router.WorkflowMode;
import athena.coder.ai.spi.ErrorLogger;
import athena.coder.ai.workflow.entity.WorkflowState;
import athena.coder.ai.workflow.node.PlanConfirmNode;
import athena.coder.ai.workflow.node.PlanNode;
import athena.coder.ai.workflow.node.RouterNode;
import athena.coder.ai.workflow.node.UserFaceNode;
import athena.coder.ai.workflow.workflow.CoderWorkflow;
import athena.coder.ai.workflow.workflow.DebuggerWorkflow;
import athena.coder.ai.workflow.workflow.GraphDSL;
import athena.coder.ai.workflow.workflow.TesterWorkflow;
import athena.coder.ai.workflow.workflow.WordWorkflow;
import athena.coder.exception.RocAgentException;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;

import java.util.Map;
import java.util.logging.Logger;

import static athena.coder.ai.workflow.entity.NodeEnum.PLANNER;
import static athena.coder.ai.workflow.entity.NodeEnum.PLAN_CONFIRM;
import static athena.coder.ai.workflow.entity.NodeEnum.ROUTER;
import static athena.coder.ai.workflow.entity.NodeEnum.USER_FACE;
import static athena.coder.ai.workflow.workflow.AbstractSubWorkflow.routeBySignal;
import static athena.coder.ai.workflow.workflow.AbstractSubWorkflow.selfTargets;

public class MasterWorkflow {

    private static final Logger LOG = Logger.getLogger(MasterWorkflow.class.getName());

    public void start(Map<String, Object> initialState) throws GraphStateException {
        if (initialState == null || initialState.isEmpty()) {
            throw new RocAgentException("initialState is empty");
        }

        GraphDSL g = new GraphDSL(new StateGraph<>(WorkflowState::new));

        //注册节点
        g.node(USER_FACE, new UserFaceNode());
        g.node(ROUTER, new RouterNode());
        g.node(PLANNER, new PlanNode());
        g.node(PLAN_CONFIRM, new PlanConfirmNode());

        //子工作流节点（实现 NodeAction，内嵌主图；各自内部跑完整质量闭环）；
        //节点名直接用 WorkflowMode.name()，与 PLAN_CONFIRM 的零映射信号天然对齐
        g.node(WorkflowMode.CODE_WORKFLOW, new CoderWorkflow());
        g.node(WorkflowMode.DEBUG_WORKFLOW, new DebuggerWorkflow());
        g.node(WorkflowMode.WORD_WORKFLOW, new WordWorkflow());
        g.node(WorkflowMode.TEST_WORKFLOW, new TesterWorkflow());

        g.fromStart(USER_FACE);
        g.edge(ROUTER, PLANNER);
        g.edge(PLANNER, PLAN_CONFIRM);

        //流程编排
        g.route(USER_FACE, routeBySignal(), selfTargets(ROUTER));

        //人工确认门：拒绝 → PLANNER 重新规划；确认 → 按 WORKFLOW_MODE 分流到对应子工作流节点；熔断/异常 → END
        g.route(PLAN_CONFIRM, routeBySignal(),
                selfTargets(PLANNER, WorkflowMode.CODE_WORKFLOW, WorkflowMode.DEBUG_WORKFLOW,
                        WorkflowMode.WORD_WORKFLOW, WorkflowMode.TEST_WORKFLOW));

        //子工作流执行完毕 → END（最终报告由子工作流基类 collectResults 输出）
        g.toEnd(WorkflowMode.CODE_WORKFLOW);
        g.toEnd(WorkflowMode.DEBUG_WORKFLOW);
        g.toEnd(WorkflowMode.WORD_WORKFLOW);
        g.toEnd(WorkflowMode.TEST_WORKFLOW);

        CompiledGraph<WorkflowState> compiledGraph = g.compile();
        long startMs = System.currentTimeMillis();
        compiledGraph.invoke(initialState)
                .ifPresentOrElse(
                        state -> LOG.info(String.format("■ MasterWorkflow 执行完成，taskId: %d，总耗时 %dms",
                                state.getTaskId(), System.currentTimeMillis() - startMs)),
                        () -> ErrorLogger.warn("MasterWorkflow", String.format("执行结束但未返回最终状态，总耗时 %dms",
                                System.currentTimeMillis() - startMs)));
    }
}
