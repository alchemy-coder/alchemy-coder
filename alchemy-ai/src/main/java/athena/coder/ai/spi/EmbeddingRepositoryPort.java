package athena.coder.ai.spi;

import athena.coder.ai.rag.model.FileSnapshot;
import athena.coder.ai.rag.model.Hit;
import athena.coder.ai.rag.model.RagChunk;

import java.util.List;
import java.util.Map;

/**
 * RAG 向量/指纹持久化端口：实现位于 infra 层（SqliteEmbeddingRepository），由组合根装配。
 */
public interface EmbeddingRepositoryPort {

    /**
     * 批量插入 chunk 并同步写关键词索引（单事务），返回各 chunk 的自增 id
     */
    List<Long> batchInsert(String projectKey, String model, List<RagChunk> chunks);

    /**
     * 加载项目指定模型的全部 chunk
     */
    List<RagChunk> loadByProject(String projectKey, String model);

    /**
     * 删除指定文件的全部 chunk 与关键词索引
     */
    void deleteByFile(String projectKey, String model, String filePath);

    /**
     * 删除整个项目指定模型的全部 chunk 与关键词索引
     */
    void deleteByProject(String projectKey, String model);

    /**
     * 关键词检索（bm25 排序），JOIN 主表带回原文
     */
    List<Hit> searchFts(String projectKey, String model, String queryText, int limit);

    /**
     * 加载项目指定模型的文件指纹表，key 为文件相对路径
     */
    Map<String, FileSnapshot> loadSnapshots(String projectKey, String model);

    /**
     * 更新文件指纹（INSERT OR REPLACE）
     */
    void upsertSnapshots(String projectKey, String model, List<FileSnapshot> snapshots);

    /**
     * 删除已不存在文件的指纹
     */
    void deleteSnapshot(String projectKey, String model, String filePath);
}
