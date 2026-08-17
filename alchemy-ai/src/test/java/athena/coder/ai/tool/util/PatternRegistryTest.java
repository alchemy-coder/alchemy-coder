package athena.coder.ai.tool.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PatternRegistryTest {

    private final PatternRegistry registry = PatternRegistry.getInstance();

    @Test
    void sensitiveValuePattern_matchesSecrets() {
        assertTrue(registry.sensitiveValuePattern().matcher("password=secret").find());
        assertTrue(registry.sensitiveValuePattern().matcher("api_key: abc").find());
        assertFalse(registry.sensitiveValuePattern().matcher("hello world").find());
    }

    @Test
    void isValidRegex() {
        assertTrue(registry.isValidRegex("a+"));
        assertFalse(registry.isValidRegex("("));
    }

    @Test
    void compile_cachesSameInstance() {
        assertSame(registry.compile("x\\d", 0), registry.compile("x\\d", 0));
    }

    @Test
    void securityPatternArrays_nonEmpty() {
        assertTrue(registry.sqlInjectionPatterns().length > 0);
        assertTrue(registry.xssPatterns().length > 0);
        assertTrue(registry.commandInjectionPatterns().length > 0);
        assertTrue(registry.pathTraversalPatterns().length > 0);
        assertTrue(registry.hardcodedSecretPatterns().length > 0);
        assertTrue(registry.unsafeDeserializationPatterns().length > 0);
        assertTrue(registry.weakCryptoPatterns().length > 0);
    }
}
