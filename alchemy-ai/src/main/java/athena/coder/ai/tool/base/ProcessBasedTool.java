package athena.coder.ai.tool.base;

import athena.coder.ai.tool.AbstractBaseTool;
import athena.coder.ai.tool.util.CommandSafetyValidator;
import athena.coder.ai.tool.util.ProjectContextHelper;
import athena.coder.ai.tool.strategy.CommandBuilderStrategy;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public abstract class ProcessBasedTool extends AbstractBaseTool {

    protected ProcessBasedTool() {
        super(true);
    }

    protected String executeCommand(List<String> command, Path workDir, int timeoutSeconds) {
        validateCommandSafety(command);
        String rawResult = executor.execute(command, workDir, timeoutSeconds > 0 ? timeoutSeconds : getMyTimeout());
        return processResult(rawResult);
    }

    protected CommandBuilderStrategy getProjectStrategy(Path workDir) {
        return ProjectContextHelper.getCommandStrategy(workDir);
    }

    protected List<String> buildCommandList(String command) {
        List<String> cmdList = new ArrayList<>();
        boolean inQuotes = false;
        char quoteChar = '\0';
        StringBuilder current = new StringBuilder();

        for (int i = 0; i < command.length(); i++) {
            char c = command.charAt(i);

            if (!inQuotes && (c == '"' || c == '\'')) {
                inQuotes = true;
                quoteChar = c;
            } else if (inQuotes && c == quoteChar) {
                inQuotes = false;
                quoteChar = '\0';
            } else if (!inQuotes && Character.isWhitespace(c)) {
                if (!current.isEmpty()) {
                    cmdList.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }

        if (!current.isEmpty()) {
            cmdList.add(current.toString());
        }

        if (cmdList.isEmpty()) {
            cmdList.add(command);
        }

        return cmdList;
    }

    protected void validateCommandSafety(List<String> command) {
        CommandSafetyValidator.validate(getToolName(), command);
    }

    protected String processResult(String rawResult) {
        ToolConstants.CommandResult result = ToolConstants.parseResult(rawResult);
        return switch (result.status()) {
            case SUCCESS -> {
                String output = result.body();
                yield output.isBlank() ? OK_PREFIX + "命令执行成功（无输出）" : OK_PREFIX + output;
            }
            case FAILED -> ERR_PREFIX + "命令执行失败（退出码非0）:\n" + result.body();
            case ERROR -> ERR_PREFIX + "命令执行失败:\n" + result.body();
            case TIMEOUT -> ERR_PREFIX + "命令执行超时（" + getMyTimeout() + "秒限制）";
            case UNKNOWN -> rawResult;
        };
    }

    protected String maskSensitiveInfo(String text) {
        return sanitizer.sanitizeSensitiveInfo(text);
    }
}