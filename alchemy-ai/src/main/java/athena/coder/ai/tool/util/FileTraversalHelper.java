package athena.coder.ai.tool.util;

import org.jspecify.annotations.NonNull;

import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class FileTraversalHelper {

    public static final Predicate<Path> CODE_FILE_FILTER = FileTypeConstants.CODE_FILE_FILTER;
    private static final Logger LOG = Logger.getLogger(FileTraversalHelper.class.getName());

    private FileTraversalHelper() {
    }

    public static List<Path> findFiles(Path root, Predicate<Path> fileFilter, int maxDepth, int maxResults) {
        List<Path> results = new ArrayList<>();
        if (root == null || !Files.exists(root)) {
            return results;
        }
        try {
            Files.walkFileTree(root, EnumSet.noneOf(FileVisitOption.class), maxDepth, new SimpleFileVisitor<Path>() {
                @Override
                public @NonNull FileVisitResult preVisitDirectory(@NonNull Path dir, @NonNull BasicFileAttributes attrs) {
                    String dirName = dir.getFileName().toString();
                    if (FileTypeConstants.IGNORED_DIRS.contains(dirName) && !dir.equals(root)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public @NonNull FileVisitResult visitFile(@NonNull Path file, @NonNull BasicFileAttributes attrs) {
                    if (fileFilter.test(file)) {
                        results.add(file);
                    }
                    return results.size() >= maxResults ? FileVisitResult.TERMINATE : FileVisitResult.CONTINUE;
                }
            });
        } catch (Exception e) {
            LOG.log(Level.FINE, "遍历文件失败: " + root, e);
        }
        return results;
    }

    public static List<Path> findCodeFiles(Path root, int maxDepth, int maxResults) {
        return findFiles(root, CODE_FILE_FILTER, maxDepth, maxResults);
    }
}