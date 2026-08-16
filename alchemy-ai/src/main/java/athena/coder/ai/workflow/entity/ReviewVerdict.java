package athena.coder.ai.workflow.entity;

import athena.coder.exception.RocAgentException;

/**
 * 审查结论枚举：收敛节点内 "APPROVED/.../BLOCKED" 魔法值。
 */
public enum ReviewVerdict {

    APPROVED, APPROVED_WITH_NOTES, REQUEST_CHANGES, BLOCKED;

    /**
     * 宽容解析：null/空 → BLOCKED 兜底（确保安全）；未知值 → 业务异常（保持原节点行为）
     */
    public static ReviewVerdict from(String raw) {
        if (raw == null || raw.isBlank()) {
            return BLOCKED;
        }
        try {
            return valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new RocAgentException("未知的审查结论: " + raw);
        }
    }
}
