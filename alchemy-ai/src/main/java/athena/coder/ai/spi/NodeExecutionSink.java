package athena.coder.ai.spi;

/**
 * 节点执行持久化端口：每次节点执行时落库入参/出参/当前 state。
 * <p>
 * 实现位于 infra 层（{@code NodeExecutionRepository}），由组合根装配；
 * ai 层未装配时 {@link AiInfra#nodeExecutions()} 返回 null，节点执行静默跳过，不阻断主流程。
 */
public interface NodeExecutionSink {

    /**
     * 记录一次节点执行
     */
    void record(NodeExecutionRecord record);
}
