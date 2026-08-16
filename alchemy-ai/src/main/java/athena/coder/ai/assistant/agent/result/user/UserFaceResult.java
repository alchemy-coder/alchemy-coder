package athena.coder.ai.assistant.agent.result.user;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import athena.coder.ai.assistant.agent.result.MarkdownStrippingDeserializer;

/**
 * UserFaceAssistant 的标准返回结构。
 *
 * <pre>
 * 三路分流：
 *   DIRECT  → 简单任务已完成，content 展示给用户，工作流结束
 *   ROUTE   → 复杂任务需专家团，content 为原始消息，routeContext 传给下游 Agent
 *   CLARIFY → 意图模糊，content 为追问，等待用户回答
 *
 * routeContext：用户意图的精简提炼（必填），ROUTE 模式下注入 RouterAgent/PlannerAgent 的上下文，
 * DIRECT/CLARIFY 模式下为所理解的任务摘要。
 * </pre>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonDeserialize(using = MarkdownStrippingDeserializer.class)
public record UserFaceResult(
        @JsonProperty("mode") UserFaceMode mode,
        @JsonProperty("content") String content,
        @JsonProperty("routeContext") String routeContext) {
}