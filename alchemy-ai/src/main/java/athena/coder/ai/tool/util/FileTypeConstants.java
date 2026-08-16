package athena.coder.ai.tool.util;

import java.nio.file.Path;
import java.util.Set;
import java.util.function.Predicate;

public final class FileTypeConstants {

    public static final Set<String> IGNORED_DIRS = Set.of(
            ".git", "target", "node_modules", ".idea", ".gradle", "build",
            ".backup", "__pycache__", ".mvn", "out"
    );
    public static final Set<String> BINARY_EXTENSIONS = Set.of(
            ".class", ".jar", ".war", ".ear", ".exe", ".dll", ".so", ".dylib",
            ".png", ".jpg", ".jpeg", ".gif", ".bmp", ".ico", ".webp",
            ".pdf", ".zip", ".tar", ".gz", ".7z", ".rar",
            ".db", ".sqlite", ".dat"
    );
    private static final Set<String> CODE_EXTENSIONS = Set.of(
            ".java", ".go", ".rs", ".py", ".js", ".ts", ".tsx", ".jsx",
            ".kt", ".scala", ".rb", ".php", ".cs", ".cpp", ".c", ".h"
    );

    public static final Predicate<Path> CODE_FILE_FILTER = file -> isCodeFile(file.getFileName().toString());

    public static final Predicate<Path> CONFIG_FILE_FILTER = file -> {
        String fileName = file.getFileName().toString().toLowerCase();
        return fileName.endsWith(".xml") || fileName.endsWith(".yml") || fileName.endsWith(".yaml")
                || fileName.endsWith(".properties") || fileName.endsWith(".json") || fileName.endsWith(".conf")
                || fileName.endsWith(".ini") || fileName.endsWith(".env") || fileName.endsWith(".toml");
    };

    private FileTypeConstants() {
    }

    public static boolean isBinaryFile(Path path) {
        String fileName = path.getFileName().toString().toLowerCase();
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex >= 0 && BINARY_EXTENSIONS.contains(fileName.substring(dotIndex));
    }

    public static boolean isCodeFile(String fileName) {
        String lower = fileName.toLowerCase();
        int dotIndex = lower.lastIndexOf('.');
        return dotIndex >= 0 && CODE_EXTENSIONS.contains(lower.substring(dotIndex));
    }
}