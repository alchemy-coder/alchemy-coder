package athena.coder.ai.spi;

/**
 * 执行轨迹持久化端口：一次会话下，节点执行与工具调用统一落库（{@code agent_execution} 表）。
 * <p>
 * 实现位于 infra 层（{@code AgentExecutionRepository}），由组合根装配；
 * ai 层未装配时 {@link AiInfra#agentExecutions()} 返回 null，执行轨迹静默跳过，不阻断主流程。
 */
public interface AgentExecutionSink {

    /**
     * 记录一次执行（节点或工具，由 {@link AgentExecution#kind()} 区分）
     */
    void record(AgentExecution execution);
}
