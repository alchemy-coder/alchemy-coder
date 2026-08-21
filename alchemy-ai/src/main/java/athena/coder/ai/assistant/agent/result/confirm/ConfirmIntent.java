package athena.coder.ai.assistant.agent.result.confirm;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * 规划确认意图分类结果
 */
public enum ConfirmIntent {

    // 用户确认执行计划
    CONFIRM,
    // 用户对计划提出修改意见/部分认可但要求调整 → 按意见重新规划
    REVISE,
    // 用户仅提问/有疑问、未表态、未提修改指令 → 先答疑再让其决定
    CLARIFY,
    // 用户明确拒绝/取消（不打算继续）
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
            case "REVISE", "REVISED", "MODIFY", "EDIT", "CHANGE", "ADJUST", "修改", "修订", "调整" -> REVISE;
            case "CLARIFY", "ASK", "QUESTION", "QUERY", "追问", "询问", "为什么" -> CLARIFY;
            case "REJECT", "REJECTED", "NO", "DENY", "CANCEL", "取消", "拒绝" -> REJECT;
            default -> null;
        };
    }
}
