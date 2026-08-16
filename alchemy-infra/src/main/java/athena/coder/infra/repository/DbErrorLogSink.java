package athena.coder.infra.repository;

import athena.coder.ai.spi.ErrorLogSink;
import org.jdbi.v3.core.HandleConsumer;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

import static athena.coder.infra.DbManager.getJdbi;

/**
 * 统一错误日志记录器 - 异步写入 error_log 表
 * <p>
 * 所有异常统一通过本类持久化到数据库，不再打印到 console。
 * 异步写入，不阻塞主业务流程。
 */
public class DbErrorLogSink implements ErrorLogSink {

    private static final Logger LOG = Logger.getLogger(DbErrorLogSink.class.getName());
    private static final int MAX_STACK_LENGTH = 2000;
    private static final int MAX_MESSAGE_LENGTH = 500;
    private static final int MAX_USER_REQUEST_LENGTH = 200;

    /**
     * 记录异常（无任务上下文）
     */
    public void log(String source, Throwable ex) {
        log(source, ex, null, null, null);
    }

    /**
     * 记录异常（携带完整上下文）
     *
     * @param source       来源标识（如 "PlanNode"、"FileOperationTool"）
     * @param ex           异常对象
     * @param taskId       任务ID（可为 null）
     * @param agentType    Agent类型（可为 null）
     * @param userRequest  触发错误的用户请求摘要（可为 null）
     */
    @Override
    public void log(String source, Throwable ex,
                    Long taskId, String agentType, String userRequest) {
        if (ex == null) return;

        String errorType = ex.getClass().getName();
        String errorMsg = truncate(ex.getMessage(), MAX_MESSAGE_LENGTH);
        String stack = truncateStack(ex);
        String req = truncate(userRequest, MAX_USER_REQUEST_LENGTH);

        asyncInsert(handle -> handle.createUpdate("""
                        INSERT INTO error_log (source, error_type, error_message,
                                               task_id, agent_type, user_request,
                                               stack_trace, severity)
                        VALUES (:source, :errorType, :errorMsg,
                                :taskId, :agentType, :userReq,
                                :stack, 'ERROR')
                        """)
                .bind("source", source)
                .bind("errorType", errorType)
                .bind("errorMsg", errorMsg)
                .bind("taskId", taskId)
                .bind("agentType", agentType)
                .bind("userReq", req)
                .bind("stack", stack)
                .execute());
    }

    /**
     * 记录警告（无异常对象的非预期情况，如熔断、数据缺失、回退提取）
     *
     * @param source  来源标识
     * @param message 警告描述
     */
    @Override
    public void warn(String source, String message) {
        String truncated = truncate(message, MAX_MESSAGE_LENGTH);
        asyncInsert(handle -> handle.createUpdate("""
                        INSERT INTO error_log (source, error_type, error_message, severity)
                        VALUES (:source, 'WARN', :message, 'WARN')
                        """)
                .bind("source", source)
                .bind("message", truncated)
                .execute());
    }

    /**
     * 异步写库：DB 写入失败时仅输出到 JUL 日志，不抛出、不阻断主流程
     */
    private static void asyncInsert(HandleConsumer<?> insert) {
        CompletableFuture.runAsync(() -> {
            try {
                getJdbi().useHandle(insert);
            } catch (Exception e) {
                LOG.log(Level.SEVERE, "ErrorLogger 写入数据库失败", e);
            }
        });
    }

    private static String truncateStack(Throwable ex) {
        StringWriter sw = new StringWriter();
        ex.printStackTrace(new PrintWriter(sw));
        return truncate(sw.toString(), MAX_STACK_LENGTH);
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
