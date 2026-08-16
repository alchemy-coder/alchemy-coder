package athena.coder.ai.tool.security;

import athena.coder.ai.tool.config.ToolConfigCenter;
import athena.coder.ai.tool.exception.ToolSecurityException;
import athena.coder.ai.tool.exception.ErrorCode;

import java.util.ArrayDeque;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 滑动窗口限流：按工具名 + 全局两类窗口计数。
 * 读/写/破坏性操作曾分目录限流，但写/破坏性路径无调用方，已收敛为单一调用频率。
 */
public class ToolRateLimiter {

    private final ToolConfigCenter config;
    private final ConcurrentHashMap<String, SlidingWindowCounter> toolCounters = new ConcurrentHashMap<>();
    private final SlidingWindowCounter globalCounter;

    public ToolRateLimiter(ToolConfigCenter config) {
        this.config = config;
        this.globalCounter = new SlidingWindowCounter(60, config.getRateLimitPerMinute() * 2);
    }

    public void checkRateLimit(String toolName) throws ToolSecurityException {
        if (!config.isRateLimitEnabled()) return;

        int toolLimit = config.getRateLimitPerMinute();
        if (toolLimit > 0) {
            SlidingWindowCounter toolCounter = toolCounters.computeIfAbsent(
                    toolName,
                    k -> new SlidingWindowCounter(60, toolLimit)
            );
            if (!toolCounter.tryAcquire()) {
                throw new ToolSecurityException(toolName, ErrorCode.RATE_LIMIT_EXCEEDED, toolLimit);
            }
        }

        if (!globalCounter.tryAcquire()) {
            throw new ToolSecurityException(toolName, ErrorCode.RATE_LIMIT_EXCEEDED, config.getRateLimitPerMinute() * 2);
        }
    }

    private static class SlidingWindowCounter {
        final ArrayDeque<Long> timestamps = new ArrayDeque<>();
        private final long windowSizeMillis;
        private final int maxCount;

        SlidingWindowCounter(int windowSizeSeconds, int maxCount) {
            this.windowSizeMillis = windowSizeSeconds * 1000L;
            this.maxCount = maxCount;
        }

        synchronized boolean tryAcquire() {
            long now = System.currentTimeMillis();
            long threshold = now - windowSizeMillis;

            while (!timestamps.isEmpty() && timestamps.peekFirst() < threshold) {
                timestamps.pollFirst();
            }

            if (timestamps.size() >= maxCount) {
                return false;
            }

            timestamps.addLast(now);
            return true;
        }
    }
}
