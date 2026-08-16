package athena.coder.ai.tool.executor;

import java.nio.file.Path;
import java.util.List;

public interface CommandExecutor {
    String execute(List<String> command, Path workingDir, int timeoutSeconds);
}