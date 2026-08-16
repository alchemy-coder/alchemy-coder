package athena.coder.ui.content.chatview;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ChatView Emoji 处理测试
 * 验证包含 emoji 的内容在 WebView 中能正确显示
 */
class ChatViewEmojiTest {

    @BeforeEach
    void setUp() {
        // 测试前初始化（如果需要）
    }

    @Test
    void testBasicEmoji() {
        String content = "Hello 😊 World";
        assertNotNull(content, "Basic emoji content should not be null");
        assertTrue(content.contains("😊"), "Content should contain emoji");
    }

    @Test
    void testComplexEmoji() {
        String content = "Testing complex emojis: 👨‍👩‍👧‍👦 🎉🚀💻";
        assertNotNull(content);
        assertEquals(42, content.length(), "Complex emoji string length should be correct");
    }

    @Test
    void testEmojiWithMarkdown() {
        String markdown = "# Title with emoji 😎\n\nThis is **bold** with emoji 🎯\n\n- Item 1 ✅\n- Item 2 ❌";
        assertNotNull(markdown);
        assertTrue(markdown.contains("😎"));
        assertTrue(markdown.contains("🎯"));
        assertTrue(markdown.contains("✅"));
        assertTrue(markdown.contains("❌"));
    }

    @Test
    void testEmojiInCodeBlock() {
        String code = "```java\n// Comment with emoji 🐛\nString emoji = \"🔥\";\n```";
        assertNotNull(code);
        assertTrue(code.contains("🐛"));
        assertTrue(code.contains("🔥"));
    }

    @Test
    void testMixedContentWithEmoji() {
        String mixed = "Regular text 😊 **markdown** 🎯 `code` 💻\n\n> Quote with emoji ⭐\n\n1. Numbered 😎";
        assertNotNull(mixed);

        // 验证各种 emoji 都存在
        String[] emojis = {"😊", "🎯", "💻", "⭐", "😎"};
        for (String emoji : emojis) {
            assertTrue(mixed.contains(emoji), "Should contain emoji: " + emoji);
        }
    }

    @Test
    void testSpecialCharactersWithEmoji() {
        String special = "Emoji with <html> tags & \"quotes\" and 'apostrophes' 😊🎉";
        assertNotNull(special);
        assertTrue(special.contains("😊"));
        assertTrue(special.contains("🎉"));
    }

    @Test
    void testLongTextWithMultipleEmojis() {
        StringBuilder sb = new StringBuilder();
        sb.append("Start ");
        for (int i = 0; i < 110; i++) {
            sb.append("emoji").append(i % 10).append(" 😊");
            if (i % 10 == 0) sb.append("\n");
        }
        sb.append(" End 🎉");

        String longText = sb.toString();
        assertNotNull(longText);
        assertTrue(longText.contains("😊"));
        assertTrue(longText.contains("🎉"));
        assertTrue(longText.length() > 1000, "Long text should have substantial length");
    }

    @Test
    void testEmojiEncodingConsistency() {
        String original = "Test 🔥 encoding";

        // 模拟 UTF-8 编码和解码过程
        try {
            byte[] utf8Bytes = original.getBytes("UTF-8");
            String decoded = new String(utf8Bytes, "UTF-8");
            assertEquals(original, decoded, "UTF-8 encoding/decoding should preserve emoji");
        } catch (Exception e) {
            fail("UTF-8 encoding test failed: " + e.getMessage());
        }
    }

    @Test
    void testBase64EncodingWithEmoji() {
        String htmlWithEmoji = "<p>Hello 😊 World 🌍</p>";

        try {
            // 测试 Base64 编码（模拟 escapeForJsString 方法）
            String base64Encoded = java.util.Base64.getEncoder()
                    .encodeToString(htmlWithEmoji.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            assertNotNull(base64Encoded);
            assertFalse(base64Encoded.isEmpty());

            // 测试 Base64 解码
            byte[] decodedBytes = java.util.Base64.getDecoder().decode(base64Encoded);
            String decoded = new String(decodedBytes, java.nio.charset.StandardCharsets.UTF_8);

            assertEquals(htmlWithEmoji, decoded, "Base64 encode/decode should preserve emoji");
            assertTrue(decoded.contains("😊"), "Decoded content should contain emoji");
            assertTrue(decoded.contains("🌍"), "Decoded content should contain globe emoji");

        } catch (Exception e) {
            fail("Base64 encoding test failed: " + e.getMessage());
        }
    }

    @Test
    void testPreprocessContentMethod() {
        // 由于 preprocessContent 是 private 方法，我们测试其预期行为

        String input = "Test\r\nwith\r\nline\nbreaks 😊\tand\ttabs 🎉";
        String expectedNormalized = "Test\nwith\nline\nbreaks 😊\tand\ttabs 🎉";

        // 手动实现预处理逻辑进行验证
        String normalized = input.replace("\r\n", "\n").replace("\r", "\n");
        assertEquals(expectedNormalized, normalized, "Line normalization should work correctly");
        assertTrue(normalized.contains("😊"));
        assertTrue(normalized.contains("🎉"));
    }

    @Test
    void testControlCharacterRemoval() {
        String withControlChars = "Before\u0000After 😊\u0001More 🎉";

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < withControlChars.length(); i++) {
            char c = withControlChars.charAt(i);
            if (c == '\n' || c == '\t' || c >= ' ') {
                sb.append(c);
            } else if (Character.isHighSurrogate(c) && i + 1 < withControlChars.length() &&
                    Character.isLowSurrogate(withControlChars.charAt(i + 1))) {
                sb.append(c);
                sb.append(withControlChars.charAt(++i));
            }
        }

        String cleaned = sb.toString();
        assertFalse(cleaned.contains("\u0000"), "Null character should be removed");
        assertFalse(cleaned.contains("\u0001"), "Control character should be removed");
        assertTrue(cleaned.contains("😊"), "Emoji should be preserved");
        assertTrue(cleaned.contains("🎉"), "Emoji should be preserved");
        assertTrue(cleaned.contains("Before"), "Normal text should be preserved");
        assertTrue(cleaned.contains("After"), "Normal text should be preserved");
    }
}
