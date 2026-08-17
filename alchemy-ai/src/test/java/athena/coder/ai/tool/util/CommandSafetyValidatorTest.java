package athena.coder.ai.tool.util;

import athena.coder.ai.tool.exception.ErrorCode;
import athena.coder.ai.tool.exception.ToolSecurityException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandSafetyValidatorTest {

    @Test
    void emptyOrNullCommand_isBlocked() {
        assertBlocked(() -> CommandSafetyValidator.validate("t", List.of()),
                "t", ErrorCode.COMMAND_BLOCKED);
        assertBlocked(() -> CommandSafetyValidator.validate("t", null),
                "t", ErrorCode.COMMAND_BLOCKED);
    }

    @Test
    void dangerousCommand_isDangerous() {
        assertBlocked(() -> CommandSafetyValidator.validate("t", List.of("rm", "-rf", "/")),
                "t", ErrorCode.DANGEROUS_COMMAND);
        assertBlocked(() -> CommandSafetyValidator.validate("t", List.of("sudo", "ls")),
                "t", ErrorCode.DANGEROUS_COMMAND);
        // 大小写不敏感
        assertBlocked(() -> CommandSafetyValidator.validate("t", List.of("RM", "x")),
                "t", ErrorCode.DANGEROUS_COMMAND);
    }

    @Test
    void injectionPatterns_areBlocked() {
        assertBlocked(() -> CommandSafetyValidator.validate("t", List.of("git", "$(whoami)")),
                "t", ErrorCode.COMMAND_BLOCKED);
        assertBlocked(() -> CommandSafetyValidator.validate("t", List.of("git", "`whoami`")),
                "t", ErrorCode.COMMAND_BLOCKED);
        assertBlocked(() -> CommandSafetyValidator.validate("t", List.of("git", "; rm -rf /")),
                "t", ErrorCode.COMMAND_BLOCKED);
    }

    @Test
    void dangerousRedirect_isBlocked() {
        assertBlocked(() -> CommandSafetyValidator.validate("t", List.of("echo", "> /etc/passwd")),
                "t", ErrorCode.COMMAND_BLOCKED);
    }

    @Test
    void safeCommand_passes() {
        assertDoesNotThrow(() -> CommandSafetyValidator.validate("t", List.of("mvn", "compile")));
        assertDoesNotThrow(() -> CommandSafetyValidator.validate("t", List.of("git", "status")));
    }

    private static void assertBlocked(Executable exec, String toolName, ErrorCode code) {
        ToolSecurityException e = assertThrows(ToolSecurityException.class, exec);
        assertEquals(toolName, e.getToolName());
        assertTrue(e.toDisplayString().startsWith("[错误-" + code.getCode() + "]"), e.toDisplayString());
    }
}
