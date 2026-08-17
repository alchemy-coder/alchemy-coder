package athena.coder.ai.assistant.agent.result;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MarkdownUtilsTest {

    @Test
    void null_returnsEmpty() {
        assertEquals("", MarkdownUtils.stripMarkdown(null));
    }

    @Test
    void jsonFence_isStripped() {
        assertEquals("{\"a\":1}", MarkdownUtils.stripMarkdown("```json\n{\"a\":1}\n```"));
    }

    @Test
    void noLangFence_isStripped() {
        assertEquals("plain", MarkdownUtils.stripMarkdown("```\nplain\n```"));
    }

    @Test
    void unfenced_passesThrough() {
        assertEquals("hello", MarkdownUtils.stripMarkdown("hello"));
        // 无围栏时也做 trim（方法契约：统一返回 trim 后文本）
        assertEquals("keep spaces", MarkdownUtils.stripMarkdown("  keep spaces  "));
    }

    @Test
    void innerCodeBlock_isPreserved() {
        // 内层 ```java 代码块不应被误当作外层围栏剥离
        String raw = "```json\n{\"code\": \"```java\nint x=1;\n```\"}\n```";
        assertEquals("{\"code\": \"```java\nint x=1;\n```\"}", MarkdownUtils.stripMarkdown(raw));
    }
}
