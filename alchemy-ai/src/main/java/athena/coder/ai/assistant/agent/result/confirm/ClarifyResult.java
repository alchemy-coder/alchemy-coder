package athena.coder.ai.assistant.agent.result.confirm;

import athena.coder.ai.assistant.agent.result.MarkdownStrippingDeserializer;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

/**
 * 计划答疑结果（针对用户对执行计划提出的疑问）
 *
 * @param answer     针对疑问的直接回答（Markdown，可直接展示给用户）
 * @param suggestion 基于回答给出的下一步建议（一句话，可选；无建议时省略）
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonDeserialize(using = MarkdownStrippingDeserializer.class)
public record ClarifyResult(String answer, String suggestion) {
}
