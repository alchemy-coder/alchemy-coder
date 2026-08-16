package athena.coder.ai.workflow.entity;

import athena.coder.exception.RocAgentException;

/**
 * 测试执行状态枚举：收敛节点内 "PASS/FAIL/SKIP/ERROR" 魔法值。
 */
public enum TesterStatus {

    PASS, FAIL, SKIP, ERROR;

    /**
     * 宽容解析：null/空 → ERROR 兜底；未知值 → 业务异常（保持原节点行为）
     */
    public static TesterStatus from(String raw) {
        if (raw == null || raw.isBlank()) {
            return ERROR;
        }
        try {
            return valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new RocAgentException("未知的测试状态: " + raw);
        }
    }
}
