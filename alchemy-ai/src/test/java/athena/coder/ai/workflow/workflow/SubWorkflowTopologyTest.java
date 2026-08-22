package athena.coder.ai.workflow.workflow;

import athena.coder.ai.workflow.entity.WorkflowState;
import org.bsc.langgraph4j.StateGraph;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * 子工作流构图冒烟测试：验证四种子工作流的节点/边编排在构建期通过 GraphDSL 校验并可 compile。
 * 不真正 invoke（避免触发真实 LLM 调用），仅守护「路由目标写错在构图时即抛异常」这一核心价值。
 */
class SubWorkflowTopologyTest {

    @Test
    void allSubWorkflows_buildAndCompile() {
        List<AbstractSubWorkflow> workflows = List.of(
                new CoderWorkflow(),
                new DebuggerWorkflow(),
                new WordWorkflow(),
                new TesterWorkflow());
        for (AbstractSubWorkflow wf : workflows) {
            GraphDSL g = new GraphDSL(new StateGraph<>(WorkflowState::new));
            String name = wf.getClass().getSimpleName();
            assertDoesNotThrow(() -> wf.buildGraph(g), name + " buildGraph 失败");
            assertDoesNotThrow(g::compile, name + " compile 失败");
        }
    }
}
