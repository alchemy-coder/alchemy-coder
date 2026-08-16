package athena.coder.ai.tool.security;

import athena.coder.ai.tool.config.ToolConfigCenter;
import athena.coder.ai.tool.exception.ToolSecurityException;
import athena.coder.ai.tool.exception.ErrorCode;

import java.util.ArrayDeque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class ToolRateLimiter {

    private static final Logger LOG = Logger.getLogger(ToolRateLimiter.class.getName());

    private final ToolConfigCenter config;
    private final ConcurrentHashMap<String, SlidingWindowCounter> toolCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, SlidingWindowCounter> categoryCounters = new ConcurrentHashMap<>();

    public ToolRateLimiter(ToolConfigCenter config) {
        this.config = config;
    }

    public void checkRateLimit(String toolName) throws ToolSecurityException {
        checkRateLimit(toolName, OperationCategory.READ);
    }

    public void checkRateLimit(String toolName, OperationCategory category) throws ToolSecurityException {
        if (!config.isRateLimitEnabled()) return;

        int toolLimit = getToolLimit(toolName, category);
        if (toolLimit > 0) {
            SlidingWindowCounter toolCounter = toolCounters.computeIfAbsent(
                    toolName,
                    k -> new SlidingWindowCounter(60, toolLimit)
            );
            if (!toolCounter.tryAcquire()) {
                throw new ToolSecurityException(toolName, ErrorCode.RATE_LIMIT_EXCEEDED, toolLimit);
            }
        }

        int categoryLimit = getCategoryLimit(category);
        if (categoryLimit > 0) {
            String categoryKey = category.name();
            SlidingWindowCounter catCounter = categoryCounters.computeIfAbsent(
                    categoryKey,
                    k -> new SlidingWindowCounter(60, categoryLimit)
            );
            if (!catCounter.tryAcquire()) {
                throw new ToolSecurityException(toolName, ErrorCode.RATE_LIMIT_EXCEEDED, categoryLimit);
            }
        }
    }

    private int getToolLimit(String toolName, OperationCategory category) {
        return switch (category) {
            case WRITE, DESTRUCTIVE -> config.getRateLimitPerMinute() / 2;
            case READ -> config.getRateLimitPerMinute();
        };
    }

    private int getCategoryLimit(OperationCategory category) {
        return switch (category) {
            case WRITE -> config.getRateLimitPerMinute() / 2;
            case DESTRUCTIVE -> config.getRateLimitPerMinute() / 4;
            case READ -> config.getRateLimitPerMinute() * 2;
        };
    }

    public enum OperationCategory {
        READ,
        WRITE,
        DESTRUCTIVE
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