package athena.coder.ai.spi;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 统一错误日志门面：保留静态调用习惯，实际持久化委托 {@link ErrorLogSink}（infra 层实现）。
 * <p>
 * 未装配（如单测/启动前）降级为 JUL 输出，不抛异常、不阻断主流程。
 */
public final class ErrorLogger {

    private static final Logger LOG = Logger.getLogger(ErrorLogger.class.getName());

    private ErrorLogger() {
    }

    /**
     * 记录异常（无任务上下文）
     */
    public static void log(String source, Throwable ex) {
        log(source, ex, null, null, null);
    }

    /**
     * 记录异常（携带完整上下文）
     */
    public static void log(String source, Throwable ex,
                           Long taskId, String agentType, String userRequest) {
        if (ex == null) {
            return;
        }
        ErrorLogSink sink = AiInfra.errorLog();
        if (sink == null) {
            LOG.log(Level.WARNING, source + " 发生异常（ErrorLogSink 未装配，仅输出 JUL）", ex);
            return;
        }
        sink.log(source, ex, taskId, agentType, userRequest);
    }

    /**
     * 记录警告（无异常对象的非预期情况，如熔断、数据缺失、回退提取）
     */
    public static void warn(String source, String message) {
        ErrorLogSink sink = AiInfra.errorLog();
        if (sink == null) {
            LOG.log(Level.WARNING, source + ": " + message);
            return;
        }
        sink.warn(source, message);
    }
}
