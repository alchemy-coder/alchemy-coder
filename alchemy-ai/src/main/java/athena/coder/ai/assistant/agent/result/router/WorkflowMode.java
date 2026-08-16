package athena.coder.ai.assistant.agent.result.router;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum WorkflowMode {

    //编码
    CODE_WORKFLOW,
    //代码修复
    DEBUG_WORKFLOW,
    //写文档
    WORD_WORKFLOW,
    //帮助用户做测试
    TEST_WORKFLOW;

    /**
     * 工作流中文名（UI 提示用，与子工作流 workflowName 对齐）
     */
    public String label() {
        return switch (this) {
            case CODE_WORKFLOW -> "编码工作流";
            case DEBUG_WORKFLOW -> "缺陷修复工作流";
            case WORD_WORKFLOW -> "文档工作流";
            case TEST_WORKFLOW -> "测试补全工作流";
        };
    }

    /**
     * 兼容 LLM 输出的短名称（CODE/DEBUG/WORD/TEST）和枚举全名（CODE_WORKFLOW 等）
     */
    @JsonCreator
    public static WorkflowMode fromString(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase();
        // 直接匹配枚举全名
        for (WorkflowMode mode : values()) {
            if (mode.name().equals(normalized)) {
                return mode;
            }
        }
        // 兼容短名称: CODE → CODE_WORKFLOW
        return switch (normalized) {
            case "CODE" -> CODE_WORKFLOW;
            case "DEBUG" -> DEBUG_WORKFLOW;
            case "WORD" -> WORD_WORKFLOW;
            case "TEST" -> TEST_WORKFLOW;
            default -> null;
        };
    }
}
