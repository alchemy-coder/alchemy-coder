package athena.coder.ai.workflow.gate;

import athena.coder.exception.RocAgentException;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 人工确认阻塞门：工作流节点挂起等待用户回复，ChatManager 将用户下一条消息投递给门。
 * <p>
 * 按 taskId 挂起/应答，同一时刻每个任务至多一个待确认门。
 * 调用方（节点）必须在 finally 中调用 {@link #remove} 清理条目，防止泄漏。
 */
public final class HumanGate {

    private static final Map<Long, CompletableFuture<String>> PENDING = new ConcurrentHashMap<>();

    /**
     * 人工确认默认超时：防止用户挂起永久阻塞工作流线程
     */
    public static final Duration AWAIT_TIMEOUT = Duration.ofMinutes(30);

    private HumanGate() {
    }

    /**
     * 指定任务是否存在待确认门
     */
    public static boolean hasPending(Long taskId) {
        return taskId != null && PENDING.containsKey(taskId);
    }

    /**
     * 投递用户回复，唤醒等待中的节点；原子消费待确认条目，防止连发双投递竞态
     */
    public static void answer(Long taskId, String reply) {
        CompletableFuture<String> future = PENDING.remove(taskId);
        if (future != null) {
            future.complete(reply);
        }
    }

    /**
     * 挂起当前线程等待用户回复（默认 {@link #AWAIT_TIMEOUT} 超时）
     *
     * @param taskId 任务 id
     * @return 用户回复文本
     * @throws RocAgentException 等待超时或被中断
     */
    public static String await(Long taskId) {
        return await(taskId, AWAIT_TIMEOUT);
    }

    /**
     * 挂起当前线程等待用户回复（指定超时）
     *
     * @param taskId  任务 id
     * @param timeout 等待超时时长
     * @return 用户回复文本
     * @throws RocAgentException 等待超时或被中断
     */
    public static String await(Long taskId, Duration timeout) {
        CompletableFuture<String> future = new CompletableFuture<>();
        PENDING.put(taskId, future);
        try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            throw new RocAgentException("人工确认等待超时");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RocAgentException("人工确认等待被中断");
        } catch (Exception e) {
            throw new RocAgentException("人工确认等待失败: " + e.getMessage());
        }
    }

    /**
     * 清理待确认条目（节点 finally 中必须调用；answer 已原子摘除时此处为幂等兜底，
     * 用于覆盖 await 被中断等未经过 answer 的退出路径）
     */
    public static void remove(Long taskId) {
        if (taskId != null) {
            PENDING.remove(taskId);
        }
    }

}
