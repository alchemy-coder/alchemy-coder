package athena.coder.ai.support;

import athena.coder.ai.rag.model.FileSnapshot;
import athena.coder.ai.rag.model.Hit;
import athena.coder.ai.rag.model.RagChunk;
import athena.coder.ai.spi.EmbeddingRepositoryPort;

import java.util.List;
import java.util.Map;

/**
 * {@link EmbeddingRepositoryPort} 的可配置测试替身：仅 {@code loadByProject}/{@code searchFts}
 * 参与断言，其余方法抛异常以暴露误用。
 */
public class StubEmbeddingRepository implements EmbeddingRepositoryPort {

    public List<RagChunk> loadByProject = List.of();
    public List<Hit> searchFts = List.of();
    public List<Long> batchInsertResult = List.of(10L);
    public boolean batchInsertCalled = false;
    public boolean deleteByProjectCalled = false;

    @Override
    public List<Long> batchInsert(String projectKey, String model, List<RagChunk> chunks) {
        batchInsertCalled = true;
        return batchInsertResult;
    }

    @Override
    public List<RagChunk> loadByProject(String projectKey, String model) {
        return loadByProject;
    }

    @Override
    public void deleteByFile(String projectKey, String model, String filePath) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void deleteByProject(String projectKey, String model) {
        deleteByProjectCalled = true;
    }

    @Override
    public List<Hit> searchFts(String projectKey, String model, String queryText, int limit) {
        return searchFts;
    }

    @Override
    public Map<String, FileSnapshot> loadSnapshots(String projectKey, String model) {
        return Map.of();
    }

    @Override
    public void upsertSnapshots(String projectKey, String model, List<FileSnapshot> snapshots) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void deleteSnapshot(String projectKey, String model, String filePath) {
        throw new UnsupportedOperationException();
    }
}
