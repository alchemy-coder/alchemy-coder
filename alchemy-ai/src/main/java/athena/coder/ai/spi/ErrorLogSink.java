package athena.coder.ai.spi;

/**
 * 错误日志持久化端口：实现位于 infra 层（DbErrorLogSink），由组合根装配。
 * ai 层未装配时由 {@link ErrorLogger} 门面降级为 JUL 输出。
 */
public interface ErrorLogSink {

    /**
     * 记录异常
     *
     * @param source      来源标识（如 "PlanNode"、"FileOperationTool"）
     * @param ex          异常对象
     * @param taskId      任务ID（可为 null）
     * @param agentType   Agent类型（可为 null）
     * @param userRequest 触发错误的用户请求摘要（可为 null）
     */
    void log(String source, Throwable ex, Long taskId, String agentType, String userRequest);

    /**
     * 记录警告（无异常对象的非预期情况，如熔断、数据缺失、回退提取）
     */
    void warn(String source, String message);
}
