package athena.coder.ai.tool.security;

import athena.coder.ai.tool.config.ToolConfigCenter;
import athena.coder.ai.tool.util.PatternRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OutputSanitizerTest {

    private static OutputSanitizer sanitizer() {
        return new OutputSanitizer(ToolConfigCenter.getInstance(), PatternRegistry.getInstance());
    }

    @Test
    void process_null_returnsEmptyMarker() {
        assertEquals("(空)", sanitizer().process(null));
    }

    @Test
    void process_truncatesOversizedOutput() {
        String big = "a".repeat(100_000);
        String out = sanitizer().process(big);
        assertTrue(out.length() < big.length(), "应被截断");
        assertTrue(out.contains("[输出已截断"), out);
    }

    @Test
    void sanitizeSensitiveInfo_masksSecrets() {
        assertEquals("password=***", sanitizer().sanitizeSensitiveInfo("password=secret123"));
        assertEquals("token=***", sanitizer().sanitizeSensitiveInfo("token: abcdef"));
    }

    @Test
    void process_sanitizesWhenEnabled() {
        String out = sanitizer().process("api_key: abcdef");
        assertFalse(out.contains("abcdef"), out);
    }
}
