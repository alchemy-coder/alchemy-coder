package athena.coder.ai.assistant.agent.result.confirm;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * 规划确认意图分类结果
 */
public enum ConfirmIntent {

    // 用户确认执行计划
    CONFIRM,
    // 用户拒绝计划（含提出修改意见、追问、部分认可并要求调整等情形）
    REJECT;

    /**
     * 兼容 LLM 输出的大小写与常见近义表达
     */
    @JsonCreator
    public static ConfirmIntent fromString(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase();
        return switch (normalized) {
            case "CONFIRM", "CONFIRMED", "YES", "APPROVE", "同意", "确认" -> CONFIRM;
            case "REJECT", "REJECTED", "NO", "DENY", "拒绝" -> REJECT;
            default -> null;
        };
    }
}
