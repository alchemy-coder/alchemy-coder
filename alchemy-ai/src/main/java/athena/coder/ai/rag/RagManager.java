package athena.coder.ai.rag;

import athena.coder.ai.spi.AiInfra;
import athena.coder.ai.spi.ErrorLogger;
import athena.coder.ai.util.ProjectKeyUtil;
import athena.coder.entity.model.EmbeddingModelEnum;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * RAG 门面：项目索引调度、混合检索（向量 + FTS5 关键词，RRF 融合）、向量模型切换。
 * <p>
 * 当前项目路径取自 {@link AiInfra#projectPath()}，天然跟随项目切换；
 * 任何异常静默降级（返回空结果），RAG 永远不阻断主链路。
 */
public final class RagManager {

    private static final double MIN_SCORE = 0.5;
    private final Set<String> indexing = ConcurrentHashMap.newKeySet();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "rag-indexer");
        t.setDaemon(true);
        return t;
    });
    private static final EmbeddingModelEnum MODEL_ENUM = EmbeddingModelEnum.QIANWEN_EMBEDDING_V4;

    private RagManager() {
    }

    public static RagManager getInstance() {
        return Holder.INSTANCE;
    }

    private static String preprocessQuery(String query) {
        String cleaned = query.strip()
                .replaceAll("[，。！？、；：“”‘’【】《》（）…—\\\\\\-]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return cleaned.isEmpty() ? "" : cleaned;
    }

    /**
     * 异步增量索引（项目选中/每次工作流入口均可触发）。
     * <p>
     * 幂等守卫仅拦截"索引进行中"的任务，完成后即解除：代码变更由
     * {@link EmbeddingIndexer} 的文件指纹对比发现并重建，不会被幂等挡住。
     */
    public void indexAsync(String projectPath) {
        if (projectPath == null || projectPath.isBlank()) {
            return;
        }
        String projectKey = ProjectKeyUtil.projectKey(projectPath);
        EmbeddingModelEnum currentEmbeddingModel = MODEL_ENUM;
        String guardKey = projectKey + "|" + currentEmbeddingModel.key();
        if (!indexing.add(guardKey)) {
            return;
        }
        try {
            executor.submit(() -> {
                try {
                    EmbeddingModel embeddingModel = AiInfra.modelProvider().embeddingModel();
                    if (embeddingModel == null) {
                        return;   // 已降级，ErrorLogger 已记录
                    }
                    EmbeddingStore<TextSegment> store = AiInfra.modelProvider().embeddingStore(projectKey);
                    EmbeddingIndexer.index(projectPath, projectKey, currentEmbeddingModel, store, embeddingModel);
                } catch (Exception e) {
                    ErrorLogger.log("RagManager.index", e);
                } finally {
                    indexing.remove(guardKey);
                }
            });
        } catch (RuntimeException e) {
            // submit 失败（如已 shutdown）必须解除守卫，否则该项目永久无法索引
            indexing.remove(guardKey);
        }
    }

    /**
     * 混合检索：向量（语义）+ FTS5（关键词）双路召回、RRF 融合；
     * 任一路异常降级为另一路，全部失败返回空结果
     */
    public List<Content> retrieve(String queryText, int maxResults) {
        try {
            String projectPath = AiInfra.projectPath();
            if (projectPath == null || queryText == null || queryText.isBlank()) {
                return List.of();
            }
            String cleaned = preprocessQuery(queryText);
            if (cleaned.isEmpty()) {
                return List.of();
            }
            EmbeddingModel embeddingModel = AiInfra.modelProvider().embeddingModel();
            if (embeddingModel == null) {
                return List.of();
            }
            Embedding query = embeddingModel.embed(cleaned).content();
            EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                    .query(cleaned)
                    .queryEmbedding(query)
                    .maxResults(maxResults)
                    .minScore(MIN_SCORE)
                    .build();
            return AiInfra.modelProvider().embeddingStore(ProjectKeyUtil.projectKey(projectPath))
                    .search(request).matches().stream()
                    .map(m -> Content.from("文件: " + m.embedded().metadata().getString("file_path") + "\n" + m.embedded().text()))
                    .toList();
        } catch (Exception e) {
            ErrorLogger.log("RagManager.retrieve", e);
            return List.of();
        }
    }

    public void shutdown() {
        executor.shutdownNow();
        // stores.clear();
    }

    private static class Holder {
        static final RagManager INSTANCE = new RagManager();
    }
}