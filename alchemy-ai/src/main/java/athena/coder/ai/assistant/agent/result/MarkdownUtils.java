package athena.coder.ai.assistant.agent.result;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Markdown 代码块剥离工具（共享实现）
 * <p>
 * LLM 输出常被 ```json ... ``` 包裹，本工具统一剥离逻辑，
 * 供 {@link MarkdownStrippingDeserializer} 和 AbstractAgentNode 等共用。
 */
public final class MarkdownUtils {

    private static final Pattern MARKDOWN_PATTERN = Pattern.compile(
            "```(?:json)?\\s*\\n?([\\s\\S]*?)\\n?```", Pattern.MULTILINE);

    private MarkdownUtils() {
    }

    /**
     * 仅当整段文本被围栏完整包裹时才剥离（外层围栏）；
     * 避免误剥离 content 字段内部的 ```java 等代码块（旧版 find() 非贪婪匹配会提取错位置）
     *
     * @param raw 原始 LLM 输出
     * @return 剥离后的纯文本；raw 为 null 时返回空字符串
     */
    public static String stripMarkdown(String raw) {
        if (raw == null) return "";
        String trimmed = raw.trim();
        Matcher m = MARKDOWN_PATTERN.matcher(trimmed);
        if (m.matches()) {
            return m.group(1).trim();
        }
        return trimmed;
    }

    /**
     * 当 stripMarkdown 无法剥离（如前面有自然语言前缀）时的降级提取：
     * 用 find() 查找代码块，提取以 { 开头的内容
     *
     * @return 提取到的 JSON 字符串；无法提取时返回 null
     */
    public static String extractJson(String raw) {
        if (raw == null) return null;
        Matcher m = MARKDOWN_PATTERN.matcher(raw);
        while (m.find()) {
            String content = m.group(1).trim();
            if (content.startsWith("{")) {
                return content;
            }
        }
        // 没有 markdown 围栏：尝试从文本中找第一个 { 到最后一个 }
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return raw.substring(start, end + 1).trim();
        }
        return null;
    }
}
