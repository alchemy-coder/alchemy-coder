package athena.coder.infra.repository;

import athena.coder.ai.spi.AgentExecution;
import athena.coder.ai.spi.AgentExecutionSink;

import java.util.logging.Level;
import java.util.logging.Logger;

import static athena.coder.infra.DbManager.getJdbi;

/**
 * 执行轨迹存储：节点与工具执行合并落库到 agent_execution（kind 区分）。
 * <p>
 * 同步写入：节点/工具本身受 LLM 调用时长主导，单条 INSERT 开销可忽略；同步保证
 * 执行顺序与落库顺序一致（auto-increment id 即执行顺序），崩溃时也能保留到
 * 最后一个已完成步骤的完整轨迹。写入失败仅记 JUL 日志，不抛出、不阻断主流程。
 */
public class AgentExecutionRepository implements AgentExecutionSink {

    private static final Logger LOG = Logger.getLogger(AgentExecutionRepository.class.getName());

    @Override
    public void record(AgentExecution e) {
        try {
            getJdbi().useHandle(handle -> handle.createUpdate("""
                            INSERT INTO agent_execution (kind, session_id, task_id, node_name, tool_name, phase,
                                                         input_json, output_json, state_json,
                                                         error_msg, cost_ms)
                            VALUES (:kind, :sessionId, :taskId, :nodeName, :toolName, :phase,
                                    :inputJson, :outputJson, :stateJson,
                                    :errorMsg, :costMs)
                            """)
                    .bind("kind", e.kind().name())
                    .bind("sessionId", e.sessionId())
                    .bind("taskId", e.taskId())
                    .bind("nodeName", e.nodeName())
                    .bind("toolName", e.toolName())
                    .bind("phase", e.phase())
                    .bind("inputJson", e.inputJson())
                    .bind("outputJson", e.outputJson())
                    .bind("stateJson", e.stateJson())
                    .bind("errorMsg", e.errorMsg())
                    .bind("costMs", e.costMs())
                    .execute());
        } catch (Exception ex) {
            LOG.log(Level.SEVERE, "AgentExecution 写入数据库失败: " + (e.nodeName() != null ? e.nodeName() : e.toolName()), ex);
        }
    }
}
