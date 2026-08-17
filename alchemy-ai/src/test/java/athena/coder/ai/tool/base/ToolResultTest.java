package athena.coder.ai.tool.base;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolResultTest {

    @Test
    void success() {
        assertEquals("成功：ok", ToolResult.success("ok").toDisplayString());
    }

    @Test
    void success_withDetails() {
        assertEquals("成功：ok\nd", ToolResult.success("ok", "d").toDisplayString());
    }

    @Test
    void warn() {
        assertEquals("警告：w", ToolResult.warn("w").toDisplayString());
    }

    @Test
    void error() {
        assertEquals("错误：e", ToolResult.error("e").toDisplayString());
    }

    @Test
    void error_withException_appendsStack() {
        ToolResult r = ToolResult.error("boom", new RuntimeException("x"));
        assertTrue(r.toDisplayString().startsWith("错误：boom\n    at "), r.toDisplayString());
    }
}
