package athena.coder.ai.assistant.model;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.TokenCountEstimator;

/**
 * 基于消息文本长度的 Token 估算器。
 * <p>
 * 每 4 字符 ≈ 1 token（中英文混合粗略估算），双字节字符占比高时实际 token 更多，
 * 偏差在安全范围内，用于 {@code TokenWindowChatMemory} 的字节级内存控制。
 * 52KB ≈ 13,000 tokens，远低于 DeepSeek 128K 窗口。
 */
public class ByteCountEstimator implements TokenCountEstimator {

    /** 字符到 token 的估算系数：4 字符 ≈ 1 token */
    private static final int CHARS_PER_TOKEN = 4;

    @Override
    public int estimateTokenCountInText(String text) {
        if (text == null || text.isEmpty()) return 0;
        return Math.max(1, text.length() / CHARS_PER_TOKEN);
    }

    @Override
    public int estimateTokenCountInMessage(ChatMessage message) {
        if (message == null) return 0;
        // 直接取消息文本，避免 toString() 的 JSON 序列化开销
        return estimateTokenCountInText(getMessageText(message));
    }

    @Override
    public int estimateTokenCountInMessages(Iterable<ChatMessage> messages) {
        if (messages == null) return 0;
        int total = 0;
        for (ChatMessage msg : messages) {
            total += estimateTokenCountInMessage(msg);
        }
        return total;
    }

    /** 从 ChatMessage 提取纯文本，比 toString() 快且不产生 JSON 垃圾。 */
    private static String getMessageText(ChatMessage message) {
        return switch (message) {
            case dev.langchain4j.data.message.AiMessage ai    -> ai.text();
            case dev.langchain4j.data.message.UserMessage u   -> u.singleText();
            case dev.langchain4j.data.message.SystemMessage s -> s.text();
            case dev.langchain4j.data.message.ToolExecutionResultMessage t -> t.text();
            default -> message.toString();
        };
    }
}
