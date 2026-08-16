package athena.coder.ai.assistant.agent.result.user;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * UserFaceAssistant 的三路分流模式。
 */
public enum UserFaceMode {
    DIRECT,   // 直处理：简单任务已完成，content 展示给用户，工作流结束
    ROUTE,    // 传递：复杂任务需专家团，content 为原始消息，交给 RouterAgent
    CLARIFY;  // 澄清：意图模糊，content 为追问，等待用户回答

    @JsonCreator
    public static UserFaceMode fromString(String value) {
        try {
            return valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ROUTE;
        }
    }
}