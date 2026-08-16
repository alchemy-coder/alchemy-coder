package athena.coder.infra.repository;

import athena.coder.ai.rag.model.FileSnapshot;
import athena.coder.ai.rag.model.Hit;
import athena.coder.ai.rag.model.RagChunk;
import athena.coder.ai.spi.EmbeddingRepositoryPort;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;
import java.util.stream.Collectors;

import static athena.coder.infra.DbManager.getJdbi;

/**
 * rag_embedding / rag_embedding_fts / rag_file_snapshot 表数据访问层（RAG 向量存储 + FTS5 关键词索引）
 */
public final class SqliteEmbeddingRepository implements EmbeddingRepositoryPort {

    private static final int MAX_MATCH_TERMS = 8;


    public SqliteEmbeddingRepository() {
    }

    // ==================== rag_embedding ====================

    /**
     * 批量插入 chunk 并同步写 FTS 索引（单事务，保证主表与 FTS 原子一致），返回各 chunk 的自增 id
     */
    public List<Long> batchInsert(String projectKey, String model, List<RagChunk> chunks) {
        if (chunks.isEmpty()) {
            return List.of();
        }
        return getJdbi().inTransaction(handle -> {
            List<Long> ids = new ArrayList<>(chunks.size());
            for (RagChunk chunk : chunks) {
                long id = handle.createUpdate("""
                                INSERT INTO rag_embedding (project_key, model, file_path, content, vector)
                                VALUES (:projectKey, :model, :filePath, :content, :vector)
                                """)
                        .bind("projectKey", projectKey)
                        .bind("model", model)
                        .bind("filePath", chunk.filePath())
                        .bind("content", chunk.content())
                        .bind("vector", toBytes(chunk.vector()))
                        .executeAndReturnGeneratedKeys("id")
                        .mapTo(Long.class)
                        .one();
                handle.createUpdate("""
                                INSERT INTO rag_embedding_fts (project_key, model, chunk_id, file_path, content)
                                VALUES (:projectKey, :model, :chunkId, :filePath, :content)
                                """)
                        .bind("projectKey", projectKey)
                        .bind("model", model)
                        .bind("chunkId", id)
                        .bind("filePath", chunk.filePath())
                        .bind("content", indexText(chunk.filePath(), chunk.content()))
                        .execute();
                ids.add(id);
            }
            return ids;
        });
    }

    public List<RagChunk> loadByProject(String projectKey, String model) {
        return getJdbi().withHandle(handle ->
                handle.createQuery("""
                                SELECT id, file_path, content, vector FROM rag_embedding
                                WHERE project_key = :projectKey AND model = :model
                                """)
                        .bind("projectKey", projectKey)
                        .bind("model", model)
                        .map((rs, ctx) -> new RagChunk(rs.getLong("id"),
                                rs.getString("file_path"),
                                rs.getString("content"),
                                fromBytes(rs.getBytes("vector"))))
                        .list());
    }

    /**
     * 删除指定文件的全部 chunk（增量重建前调用），FTS 索引同步删除，单事务保证原子
     */
    public void deleteByFile(String projectKey, String model, String filePath) {
        getJdbi().useTransaction(handle -> {
            handle.createUpdate("""
                            DELETE FROM rag_embedding_fts WHERE chunk_id IN
                                (SELECT id FROM rag_embedding
                                 WHERE project_key = :projectKey AND model = :model AND file_path = :filePath)
                            """)
                    .bind("projectKey", projectKey)
                    .bind("model", model)
                    .bind("filePath", filePath)
                    .execute();
            handle.createUpdate("""
                            DELETE FROM rag_embedding
                            WHERE project_key = :projectKey AND model = :model AND file_path = :filePath
                            """)
                    .bind("projectKey", projectKey)
                    .bind("model", model)
                    .bind("filePath", filePath)
                    .execute();
        });
    }

    /**
     * 删除整个项目指定模型的全部 chunk，FTS 索引同步删除
     */
    public void deleteByProject(String projectKey, String model) {
        getJdbi().useTransaction(handle -> {
            handle.createUpdate("""
                            DELETE FROM rag_embedding_fts WHERE chunk_id IN
                                (SELECT id FROM rag_embedding
                                 WHERE project_key = :projectKey AND model = :model)
                            """)
                    .bind("projectKey", projectKey)
                    .bind("model", model)
                    .execute();
            handle.createUpdate("""
                            DELETE FROM rag_embedding
                            WHERE project_key = :projectKey AND model = :model
                            """)
                    .bind("projectKey", projectKey)
                    .bind("model", model)
                    .execute();
        });
    }

    // ==================== rag_embedding_fts ====================

    /**
     * FTS5 关键词检索：bm25 排序，JOIN 主表带回原文。
     * bm25 权重按全部列（含 UNINDEXED）的位置对应，file_path 给 2.0 加权（文件名命中是更强的信号）
     */
    public List<Hit> searchFts(String projectKey, String model, String queryText, int limit) {
        String match = buildMatchExpr(queryText);
        if (match.isEmpty()) {
            return List.of();
        }
        return getJdbi().withHandle(handle ->
                handle.createQuery("""
                                SELECT chunk_id, file_path, content, bm25(rag_embedding_fts, 1.0, 1.0, 1.0, 2.0, 1.0) AS rank
                                FROM rag_embedding_fts
                                WHERE project_key = :projectKey AND model = :model
                                  AND rag_embedding_fts MATCH :match
                                ORDER BY rank
                                LIMIT :limit
                                """)
                        .bind("projectKey", projectKey)
                        .bind("model", model)
                        .bind("match", match)
                        .bind("limit", limit)
                        .map((rs, ctx) -> new Hit(
                                rs.getLong("chunk_id"),
                                rs.getString("file_path"),
                                rs.getString("content")))
                        .list());
    }

    /**
     * FTS 索引文本：路径 + 原文拼接后分词。
     * 查询多为自然语言句子，其词元主要来自路径（类名/文件名），只索引正文会导致关键词路几乎不命中
     */
    private static String indexText(String filePath, String content) {
        return tokenizeForSearch(filePath + " " + content);
    }

    /**
     * 检索分词：驼峰/下划线拆词 + 小写化（写入与查询两侧共用）。
     * handlePaymentTimeout → "handle payment timeout"
     */
    static String tokenizeForSearch(String text) {
        String spaced = text
                .replaceAll("(?<=[a-z0-9])(?=[A-Z])", " ")
                .replaceAll("(?<=[A-Z])(?=[A-Z][a-z])", " ")
                .replaceAll("[^a-zA-Z0-9]+", " ");
        return spaced.toLowerCase().trim().replaceAll("\\s+", " ");
    }

    /**
     * 构造 FTS5 MATCH 表达式：每个词包双引号（防 FTS 语法注入），OR 连接保证召回，最多 MAX_MATCH_TERMS 个词。
     * 无有效词返回空串（调用方跳过关键词路）
     */
    static String buildMatchExpr(String queryText) {
        return Arrays.stream(tokenizeForSearch(queryText).split(" "))
                .filter(t -> !t.isEmpty())
                .distinct()
                .limit(MAX_MATCH_TERMS)
                .map(t -> "\"" + t + "\"")
                .collect(Collectors.joining(" OR "));
    }

    /**
     * 加载项目指定模型的文件指纹表
     */
    public Map<String, FileSnapshot> loadSnapshots(String projectKey, String model) {
        List<FileSnapshot> list = getJdbi().withHandle(handle ->
                handle.createQuery("""
                                SELECT file_path, mtime, size FROM rag_file_snapshot
                                WHERE project_key = :projectKey AND model = :model
                                """)
                        .bind("projectKey", projectKey)
                        .bind("model", model)
                        .map((rs, ctx) -> new FileSnapshot(
                                rs.getString("file_path"),
                                rs.getLong("mtime"),
                                rs.getLong("size")))
                        .list());
        Map<String, FileSnapshot> map = new HashMap<>();
        list.forEach(s -> map.put(s.filePath(), s));
        return map;
    }

    /**
     * 主表存在 FTS 缺失的有效 chunk 时补写（升级迁移/中断续补），按 id 游标分页。
     * NOT EXISTS 保证幂等：中断重跑或并发写入都不会产生重复行
     */
//    public void backfillFtsIfNeeded() {
//        if (!ftsReady.compareAndSet(false, true)) {
//            return;
//        }
//        try {
//            getJdbi().useHandle(handle -> {
//                // 快速路径：FTS 行数不少于主表有效行数时无需补写
//                long mainCount = handle.createQuery("SELECT COUNT(*) FROM rag_embedding")
//                        .mapTo(Long.class).one();
//                long ftsCount = handle.createQuery("SELECT COUNT(*) FROM rag_embedding_fts")
//                        .mapTo(Long.class).one();
//                if (ftsCount >= mainCount) {
//                    return;
//                }
//                long lastId = 0;
//                while (true) {
//                    List<BackfillRow> rows = handle.createQuery("""
//                                    SELECT id, project_key, model, file_path, content FROM rag_embedding m
//                                    WHERE m.id > :lastId
//                                      AND NOT EXISTS (SELECT 1 FROM rag_embedding_fts f WHERE f.chunk_id = m.id)
//                                    ORDER BY m.id LIMIT :pageSize
//                                    """)
//                            .bind("lastId", lastId)
//                            .bind("pageSize", BACKFILL_PAGE_SIZE)
//                            .map((rs, ctx) -> new BackfillRow(
//                                    rs.getLong("id"),
//                                    rs.getString("project_key"),
//                                    rs.getString("model"),
//                                    rs.getString("file_path"),
//                                    rs.getString("content")))
//                            .list();
//                    if (rows.isEmpty()) {
//                        return;
//                    }
//                    for (BackfillRow row : rows) {
//                        handle.createUpdate("""
//                                        INSERT INTO rag_embedding_fts (project_key, model, chunk_id, file_path, content)
//                                        VALUES (:projectKey, :model, :chunkId, :filePath, :content)
//                                        """)
//                                .bind("projectKey", row.projectKey())
//                                .bind("model", row.model())
//                                .bind("chunkId", row.id())
//                                .bind("filePath", row.filePath())
//                                .bind("content", indexText(row.filePath(), row.content()))
//                                .execute();
//                    }
//                    lastId = rows.get(rows.size() - 1).id();
//                }
//            });
//        } catch (Exception e) {
//            ftsReady.set(false);
//            ErrorLogger.log("EmbeddingRepository.backfillFts", e);
//        }
//    }

    // ==================== rag_file_snapshot ====================

    /**
     * 更新文件指纹（INSERT OR REPLACE）
     */
    public void upsertSnapshots(String projectKey, String model, List<FileSnapshot> snapshots) {
        if (snapshots.isEmpty()) {
            return;
        }
        getJdbi().useHandle(handle -> {
            var batch = handle.prepareBatch("""
                    INSERT OR REPLACE INTO rag_file_snapshot (project_key, model, file_path, mtime, size)
                    VALUES (:projectKey, :model, :filePath, :mtime, :size)
                    """);
            for (FileSnapshot snapshot : snapshots) {
                batch.bind("projectKey", projectKey)
                        .bind("model", model)
                        .bind("filePath", snapshot.filePath())
                        .bind("mtime", snapshot.mtime())
                        .bind("size", snapshot.size())
                        .add();
            }
            batch.execute();
        });
    }

    /**
     * 删除已不存在文件的指纹
     */
    public void deleteSnapshot(String projectKey, String model, String filePath) {
        getJdbi().useHandle(handle -> handle.createUpdate("""
                        DELETE FROM rag_file_snapshot
                        WHERE project_key = :projectKey AND model = :model AND file_path = :filePath
                        """)
                .bind("projectKey", projectKey)
                .bind("model", model)
                .bind("filePath", filePath)
                .execute());
    }

    private static byte[] toBytes(float[] vector) {
        ByteBuffer buffer = ByteBuffer.allocate(vector.length * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (float v : vector) {
            buffer.putFloat(v);
        }
        return buffer.array();
    }

    // ==================== 向量编解码 ====================

    private static float[] fromBytes(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        float[] vector = new float[bytes.length / 4];
        for (int i = 0; i < vector.length; i++) {
            vector[i] = buffer.getFloat();
        }
        return vector;
    }

    /**
     * 回填行
     */
    private record BackfillRow(long id, String projectKey, String model, String filePath, String content) {
    }
}