package athena.coder.ai.spi;

/**
 * 一次执行轨迹记录：节点执行与工具调用合并为同一记录，{@link Kind} 区分（对应 {@code agent_execution} 表一行）。
 * <p>
 * 两种 kind 复用同一组字段，语义随 kind 不同：
 * <ul>
 *   <li>{@link Kind#NODE 节点执行}：{@code phase} 取 "END"/"ERROR"，{@code inputJson} 为执行前 state 快照、
 *       {@code outputJson} 为 doApply 出参增量、{@code stateJson} 为执行后 state；{@code toolName} 恒为 null；</li>
 *   <li>{@link Kind#TOOL 工具调用}：{@code toolName} 为工具全名，{@code inputJson} 为入参、{@code outputJson} 为出参（截断 2000 字）；
 *       {@code phase}/{@code stateJson} 恒为 null。</li>
 * </ul>
 *
 * @param kind      执行类型（节点 / 工具）
 * @param taskId    任务 id
 * @param sessionId 会话 uuid（一次用户消息），可空
 * @param nodeName  发起节点类名
 * @param toolName  工具全名（如 FileOperationTool.readFile），节点执行时 null
 * @param phase     节点执行阶段："END"（成功）或 "ERROR"（失败），工具调用时 null
 * @param inputJson 节点入参（执行前 state 快照）或工具入参（JSON 字符串）
 * @param outputJson 节点出参（doApply 增量）或工具出参（截断 2000 字），失败时 null
 * @param stateJson 节点执行后当前 state，工具调用时 null
 * @param errorMsg  失败异常信息，成功为 null
 * @param costMs    执行耗时（毫秒）
 */
public record AgentExecution(
        Kind kind,
        Long taskId,
        String sessionId,
        String nodeName,
        String toolName,
        String phase,
        String inputJson,
        String outputJson,
        String stateJson,
        String errorMsg,
        long costMs
) {

    /**
     * 执行轨迹类型，落库为 {@code agent_execution.kind} 列。
     */
    public enum Kind {
        NODE, TOOL
    }
}
