package athena.coder.ai.tool.executor;

import athena.coder.ai.tool.base.ToolConstants;
import athena.coder.ai.tool.config.ToolConfigCenter;
import athena.coder.ai.tool.exception.ErrorCode;
import athena.coder.ai.tool.exception.ToolExecutionException;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ProcessCommandExecutor implements CommandExecutor {

    private static final Logger LOG = Logger.getLogger(ProcessCommandExecutor.class.getName());

    private static final int MAX_OUTPUT_LINES = 100;
    private static final int MAX_LINE_LENGTH = 2048;
    private final ToolConfigCenter config = ToolConfigCenter.getInstance();

    @Override
    public String execute(List<String> command, Path workingDir, int timeoutSeconds) {
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(workingDir.toFile());
            pb.redirectErrorStream(true);

            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            int maxChars = config.getMaxOutputChars();

            CompletableFuture<String> readFuture = CompletableFuture.supplyAsync(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    int lineCount = 0;
                    while ((line = reader.readLine()) != null) {
                        if (lineCount >= MAX_OUTPUT_LINES) {
                            output.append("[输出已截断，共 ").append(lineCount).append(" 行]");
                            break;
                        }
                        if (output.length() >= maxChars) {
                            output.append("[输出已截断，超出").append(maxChars).append("字符限制]");
                            break;
                        }
                        if (line.length() > MAX_LINE_LENGTH) {
                            line = line.substring(0, MAX_LINE_LENGTH) + "...[截断]";
                        }
                        output.append(line).append("\n");
                        lineCount++;
                    }
                } catch (Exception e) {
                    LOG.log(Level.FINE, "读取进程输出时出错: " + e.getMessage());
                }
                return output.toString();
            });

            try {
                readFuture.get(timeoutSeconds, TimeUnit.SECONDS);
            } catch (java.util.concurrent.TimeoutException e) {
                process.destroyForcibly();
                readFuture.cancel(true);
                return ToolConstants.PREFIX_TIMEOUT + "子进程超时未响应";
            }

            boolean finished = process.waitFor(5, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
                return ToolConstants.PREFIX_TIMEOUT + "子进程未能在超时内完成";
            }

            int exitCode = process.exitValue();
            String result = output.toString().trim();

            if (exitCode == 0) {
                return ToolConstants.PREFIX_SUCCESS + (result.isEmpty() ? "(无输出)" : result);
            } else {
                return ToolConstants.PREFIX_FAILED + "退出码: " + exitCode + "\n" + result;
            }
        } catch (Exception e) {
            throw new ToolExecutionException(
                    "ProcessCommandExecutor", ErrorCode.INTERNAL_ERROR, e);
        }
    }

    @Override
    public void shutdown() {
    }
}