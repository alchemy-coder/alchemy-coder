package athena.coder.ai.assistant.agent.result.confirm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import athena.coder.ai.assistant.agent.result.MarkdownStrippingDeserializer;

/**
 * 确认意图分类结果（意图 + 可选的结构化修订指令）
 *
 * @param intent 用户意图：CONFIRM 确认 / REVISE 提修改意见 / REJECT 拒绝取消
 * @param revise 结构化修订指令，仅 REVISE 时有值；其余意图为 null
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonDeserialize(using = MarkdownStrippingDeserializer.class)
public record ConfirmIntentResult(ConfirmIntent intent, Revise revise) {

    /** 便捷构造：无修订指令（CONFIRM/REJECT 用） */
    public ConfirmIntentResult(ConfirmIntent intent) {
        this(intent, null);
    }
}
