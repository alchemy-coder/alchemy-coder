package athena.coder.ai.assistant.agent.result.summarizer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import athena.coder.ai.assistant.agent.result.MarkdownStrippingDeserializer;

/**
 * SUMMARIZER 输出的结构化总结结果
 * <p>
 * 关键验证字段：report / commitMessage
 * 其余字段用 JsonNode 保留完整数据，序列化回 JSON 存入 state 时不丢失信息
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonDeserialize(using = MarkdownStrippingDeserializer.class)
public record SummarizerResult(
        String sessionId,
        JsonNode report,
        JsonNode commitMessage,
        JsonNode branchSuggestion
) {
}
