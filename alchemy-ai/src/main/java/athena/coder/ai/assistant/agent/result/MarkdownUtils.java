package athena.coder.ai.assistant.agent.result;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Markdown 代码块剥离工具（共享实现）
 * <p>
 * LLM 输出常被 ```json ... ``` 包裹，本工具统一剥离逻辑，
 * 供 {@link MarkdownStrippingDeserializer} 等共用。
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
}
