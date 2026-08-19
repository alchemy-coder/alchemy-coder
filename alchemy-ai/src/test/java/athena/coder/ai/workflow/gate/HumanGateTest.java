package athena.coder.ai.workflow.gate;

import athena.coder.exception.RocAgentException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HumanGateTest {

    @Test
    void hasPending_nullOrAbsent_returnsFalse() {
        assertFalse(HumanGate.hasPending(null));
        assertFalse(HumanGate.hasPending(999_999L));
    }

    @Test
    void remove_isIdempotent() {
        assertDoesNotThrow(() -> HumanGate.remove(null));
        assertDoesNotThrow(() -> HumanGate.remove(1000L));
    }

    @Test
    void await_timesOut_throws() {
        long taskId = 1001L;
        try {
            assertThrows(RocAgentException.class, () -> HumanGate.await(taskId, Duration.ofMillis(100)));
        } finally {
            HumanGate.remove(taskId);
        }
    }

    @Test
    void answer_wakesAwaitingThread() throws Exception {
        long taskId = 1002L;
        CompletableFuture<String> result = new CompletableFuture<>();
        Thread waiter = new Thread(() -> {
            try {
                result.complete(HumanGate.await(taskId, Duration.ofSeconds(5)));
            } catch (Exception e) {
                result.completeExceptionally(e);
            }
        }, "human-gate-waiter");
        waiter.start();

        // 轮询等待等待线程真正挂起（await 内部注册到 PENDING 无外部信号，用轮询兜底）
        long deadline = System.currentTimeMillis() + 2000;
        while (!HumanGate.hasPending(taskId) && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        assertTrue(HumanGate.hasPending(taskId), "等待线程未在超时前挂起");

        HumanGate.answer(taskId, "确认");
        assertEquals("确认", result.get(5, TimeUnit.SECONDS));
    }
}
