package athena.coder.ai.rag;

import athena.coder.ai.spi.AiInfra;
import athena.coder.ai.tool.util.FileTypeConstants;
import athena.coder.ai.spi.ErrorLogger;
import athena.coder.ai.rag.model.FileSnapshot;
import athena.coder.entity.model.EmbeddingModelEnum;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

/**
 * 项目代码索引器：扫描 → 增量对比 → 切块 → 嵌入 → 落库（后台线程调用）
 */
final class EmbeddingIndexer {

    private static final long MAX_FILE_SIZE = 512 * 1024L;
    private static final int MAX_SCAN_DEPTH = 50;
    private static final int CHUNK_SIZE = 1200;
    private static final int CHUNK_OVERLAP = 100;
    private static final int EMBED_BATCH_SIZE = 25;

    private EmbeddingIndexer() {
    }

    /**
     * 执行一轮增量索引：只重建新增/变更文件，清理已删除文件。
     * 失败直接抛出，由 RagManager 统一入库；快照仅在成功后更新，失败文件下轮自动重试。
     */
    static void index(String projectPath, String projectKey, EmbeddingModelEnum modelEnum, SqliteEmbeddingStore store) {
        EmbeddingModel model = EmbeddingModels.get(modelEnum);
        if (model == null) {
            return;   // 已降级，ErrorLogger 已记录
        }

        List<FileSnapshot> scanned = scan(Path.of(projectPath));
        String modelKey = modelEnum.key();
        Map<String, FileSnapshot> snapshots = AiInfra.embeddings().loadSnapshots(projectKey, modelKey);

        // 1. 清理已删除文件
        cleanupDeletedFiles(projectKey, modelKey, store, scanned, snapshots);

        // 2. 筛选新增/变更文件
        List<FileSnapshot> changed = filterChangedFiles(scanned, snapshots);
        if (changed.isEmpty()) {
            return;
        }

        // 3. 重建变更文件
        rebuildChangedFiles(projectPath, projectKey, modelKey, store, model, changed);
    }

    // ==================== 文件扫描 ====================

    private static List<FileSnapshot> scan(Path root) {
        List<FileSnapshot> result = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(root, MAX_SCAN_DEPTH)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> !Files.isSymbolicLink(path))
                    .forEach(path -> {
                        String relative = root.relativize(path).toString().replace('\\', '/');
                        if (!isIndexable(relative)) {
                            return;
                        }
                        try {
                            long size = Files.size(path);
                            if (size > MAX_FILE_SIZE) {
                                return;
                            }
                            long mtime = Files.getLastModifiedTime(path).toMillis();
                            result.add(new FileSnapshot(relative, mtime, size));
                        } catch (IOException e) {
                            ErrorLogger.log("ProjectIndexer.scan", e);
                        }
                    });
        } catch (IOException e) {
            // 扫描中途失败时 result 只是部分结果，若继续会被误判为"文件已删除"而误删 chunk；
            // 直接抛出终止本轮索引（快照未登记，下轮自动重试），由 RagManager 统一入库
            throw new UncheckedIOException("扫描项目目录失败: " + root, e);
        }
        return result;
    }

    /**
     * 代码文件 / 配置文件 / Markdown；忽略目录与路径前缀中的 target、.git 等
     */
    private static boolean isIndexable(String relativePath) {
        for (String element : relativePath.split("/")) {
            if (FileTypeConstants.IGNORED_DIRS.contains(element)) {
                return false;
            }
        }
        Path fileName = Path.of(relativePath).getFileName();
        Path probe = Path.of(relativePath);
        String lower = fileName.toString().toLowerCase();
        return FileTypeConstants.isCodeFile(lower)
                || FileTypeConstants.CONFIG_FILE_FILTER.test(probe)
                || lower.endsWith(".md");
    }

    private static String readQuietly(Path path) {
        try {
            return Files.readString(path);
        } catch (Exception e) {
            return null;   // 非 UTF-8 或不可读，跳过
        }
    }

    /**
     * 清理已删除文件：软删 chunk 向量 + FTS 索引，物理删文件指纹
     */
    private static void cleanupDeletedFiles(String projectKey, String modelKey, SqliteEmbeddingStore store,
                                            List<FileSnapshot> scanned, Map<String, FileSnapshot> snapshots) {
        Set<String> currentPaths = new HashSet<>();
        scanned.forEach(f -> currentPaths.add(f.filePath()));
        for (FileSnapshot snapshot : snapshots.values()) {
            if (!currentPaths.contains(snapshot.filePath())) {
                AiInfra.embeddings().deleteByFile(projectKey, store.getModel(), snapshot.filePath());
                AiInfra.embeddings().deleteSnapshot(projectKey, modelKey, snapshot.filePath());
            }
        }
    }

    /**
     * 筛选变更或新增文件：新文件（快照无记录）或 mtime/size 变化的文件
     */
    private static List<FileSnapshot> filterChangedFiles(List<FileSnapshot> scanned,
                                                         Map<String, FileSnapshot> snapshots) {
        return scanned.stream()
                .filter(f -> {
                    FileSnapshot s = snapshots.get(f.filePath());
                    return s == null || s.mtime() != f.mtime() || s.size() != f.size();
                })
                .toList();
    }

    /**
     * 重建变更文件：读取 → 删旧 chunk → 切块 → 分批嵌入 → 更新指纹
     */
    private static void rebuildChangedFiles(String projectPath, String projectKey, String modelKey,
                                            SqliteEmbeddingStore store, EmbeddingModel model,
                                            List<FileSnapshot> changed) {
        var splitter = DocumentSplitters.recursive(CHUNK_SIZE, CHUNK_OVERLAP);
        List<TextSegment> allSegments = new ArrayList<>();
        List<FileSnapshot> indexed = new ArrayList<>();

        for (FileSnapshot file : changed) {
            String content = readQuietly(Path.of(projectPath, file.filePath()));
            if (content == null || content.isBlank()) {
                continue;
            }
            store.deleteByFile(file.filePath());
            Document document = Document.from(content, Metadata.from("file_path", file.filePath()));
            allSegments.addAll(splitter.split(document));
            indexed.add(file);
        }

        for (int from = 0; from < allSegments.size(); from += EMBED_BATCH_SIZE) {
            List<TextSegment> batch = allSegments.subList(from, Math.min(from + EMBED_BATCH_SIZE, allSegments.size()));
            List<Embedding> embeddings = model.embedAll(batch).content();
            store.addAll(embeddings, batch);
        }

        AiInfra.embeddings().upsertSnapshots(projectKey, modelKey, indexed);
    }
}