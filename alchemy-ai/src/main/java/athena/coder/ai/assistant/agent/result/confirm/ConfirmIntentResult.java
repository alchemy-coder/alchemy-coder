package athena.coder.ai.assistant.agent.result.confirm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import athena.coder.ai.assistant.agent.result.MarkdownStrippingDeserializer;

/**
 * 确认意图分类结果（仅意图；拒绝时的修改意见由节点侧直接取用户原文，避免提炼折损）
 *
 * @param intent 用户意图：CONFIRM 确认 / REJECT 拒绝
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonDeserialize(using = MarkdownStrippingDeserializer.class)
public record ConfirmIntentResult(ConfirmIntent intent) {
}
