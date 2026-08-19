package athena.coder.infra.repository;

import athena.coder.ai.spi.NodeExecutionRecord;
import athena.coder.ai.spi.NodeExecutionSink;

import java.util.logging.Level;
import java.util.logging.Logger;

import static athena.coder.infra.DbManager.getJdbi;

/**
 * 节点执行轨迹存储：每次节点执行落库入参/出参/当前 state（替代原执行日志）。
 * <p>
 * 同步写入：节点本身受 LLM 调用时长主导，单条 INSERT 开销可忽略；同步保证
 * 执行顺序与落库顺序一致（auto-increment id 即执行顺序），崩溃时也能保留到
 * 最后一个已完成节点的完整轨迹。写入失败仅记 JUL 日志，不抛出、不阻断主流程。
 */
public class NodeExecutionRepository implements NodeExecutionSink {

    private static final Logger LOG = Logger.getLogger(NodeExecutionRepository.class.getName());

    @Override
    public void record(NodeExecutionRecord r) {
        try {
            getJdbi().useHandle(handle -> handle.createUpdate("""
                            INSERT INTO node_execution (task_id, node_name, phase,
                                                        input_json, output_json, state_json,
                                                        error_msg, cost_ms)
                            VALUES (:taskId, :nodeName, :phase,
                                    :inputJson, :outputJson, :stateJson,
                                    :errorMsg, :costMs)
                            """)
                    .bind("taskId", r.taskId())
                    .bind("nodeName", r.nodeName())
                    .bind("phase", r.phase())
                    .bind("inputJson", r.inputJson())
                    .bind("outputJson", r.outputJson())
                    .bind("stateJson", r.stateJson())
                    .bind("errorMsg", r.errorMsg())
                    .bind("costMs", r.costMs())
                    .execute());
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "NodeExecution 写入数据库失败: " + r.nodeName(), e);
        }
    }
}
