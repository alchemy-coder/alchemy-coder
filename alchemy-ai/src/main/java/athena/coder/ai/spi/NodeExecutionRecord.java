package athena.coder.ai.spi;

/**
 * 一次节点执行的完整快照。
 *
 * @param taskId     任务 id
 * @param nodeName   节点类名
 * @param phase      "END"（成功）或 "ERROR"（失败）
 * @param inputJson  入参：执行前 state 快照
 * @param outputJson 出参：doApply 返回的 state 增量（ERROR 为 null）
 * @param stateJson  执行后当前 state（入参 merge 出参；ERROR 时等于入参）
 * @param errorMsg   失败时的异常信息（e.getMessage()），END 为 null
 * @param costMs     节点执行耗时（毫秒）
 */
public record NodeExecutionRecord(
        Long taskId,
        String nodeName,
        String phase,
        String inputJson,
        String outputJson,
        String stateJson,
        String errorMsg,
        long costMs
) {
}
