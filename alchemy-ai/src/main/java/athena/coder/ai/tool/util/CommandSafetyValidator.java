package athena.coder.ai.tool.util;

import athena.coder.ai.tool.exception.ErrorCode;
import athena.coder.ai.tool.exception.ToolSecurityException;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public final class CommandSafetyValidator {

    private static final Set<String> BLOCKED_COMMANDS = Set.of(
            "rm", "mkfs", "dd", "chmod", "chown", "sudo", "su", "reboot",
            "shutdown", "halt", "poweroff", "kill", "pkill", "killall"
    );

    private static final Pattern COMMAND_INJECTION_PATTERN = Pattern.compile(
            "\\$\\(|`[^`]+`|\\|\\s*(?:sh|bash|/bin/sh|/bin/bash|zsh)|" +
                    ";\\s*(?:rm|mkfs|dd|chmod|chown|wget|curl|nc|telnet|ssh|scp|ftp)|" +
                    "\\$\\{[^}]+\\}|&&\\s*(?:rm|mkfs|dd|reboot|shutdown)"
    );

    private static final Pattern PIPE_REDIRECT_PATTERN = Pattern.compile(
            ">(?:>|&)?\\s*(?:/dev/|/etc/|/proc/|/sys/)|<(?:<|&)?\\s*(?:/dev/|/etc/|/proc/|/sys/)"
    );

    private CommandSafetyValidator() {
    }

    public static void validate(String toolName, List<String> command) throws ToolSecurityException {
        if (command == null || command.isEmpty()) {
            throw new ToolSecurityException(toolName, ErrorCode.COMMAND_BLOCKED, "空命令");
        }

        String baseCommand = command.getFirst().toLowerCase();

        if (isDangerousCommand(baseCommand)) {
            throw new ToolSecurityException(toolName, ErrorCode.DANGEROUS_COMMAND,
                    "检测到危险命令: " + String.join(" ", command));
        }

        for (String arg : command) {
            if (containsInjectionPatterns(arg)) {
                throw new ToolSecurityException(toolName, ErrorCode.COMMAND_BLOCKED,
                        "命令参数包含注入模式: " + arg);
            }
            if (containsRedirectPatterns(arg)) {
                throw new ToolSecurityException(toolName, ErrorCode.COMMAND_BLOCKED,
                        "命令参数包含危险重定向: " + arg);
            }
        }
    }

    private static boolean isDangerousCommand(String baseCommand) {
        return BLOCKED_COMMANDS.contains(baseCommand);
    }

    private static boolean containsInjectionPatterns(String arg) {
        return COMMAND_INJECTION_PATTERN.matcher(arg).find();
    }

    private static boolean containsRedirectPatterns(String arg) {
        return PIPE_REDIRECT_PATTERN.matcher(arg).find();
    }
}