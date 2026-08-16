package athena.coder.ai.rag;

import athena.coder.ai.rag.model.Hit;
import athena.coder.ai.rag.model.RagChunk;
import athena.coder.ai.spi.AiInfra;
import athena.coder.exception.RocAgentException;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.*;

import java.util.*;

/**
 * SQLite 向量存储，实时查询 SQLite 进行检索（暴力余弦相似度）。
 * <p>
 * 按 (projectKey, model) 作用域构造，无内存缓存，每次检索从 DB 加载全量向量。
 * 单项目 chunk 量级为数千条，DB 加载 + 内存点积毫秒级，无需向量索引。
 */
public class SqliteEmbeddingStore implements EmbeddingStore<TextSegment> {

    private static final double RRF_K = 60.0;
    /**
     * RRF 融合时关键词路的候选深度（maxResults 的倍数）。
     * bm25 名次越靠后贡献越小，多取候选只为给融合留足重叠空间，不影响最终返回条数
     */
    private static final int FTS_DEPTH_FACTOR = 4;
    private final String projectKey;
    private final String model;

    public SqliteEmbeddingStore(String projectKey, String model) {
        this.projectKey = projectKey;
        this.model = model;
    }

    private static RagChunk toChunk(Embedding embedding, TextSegment segment) {
        return new RagChunk(0L, segment.metadata().getString("file_path"), segment.text(), embedding.vector());
    }

    /**
     * 手写点积余弦：query 模长在循环外只算一次，避免 {@link CosineSimilarity} 对每条 chunk 重复计算
     */
    private static double cosine(float[] query, float[] vector, double queryNorm) {
        if (vector.length != query.length) {
            return 0.0;
        }
        double dot = 0.0;
        double norm = 0.0;
        for (int i = 0; i < vector.length; i++) {
            dot += query[i] * vector[i];
            norm += vector[i] * vector[i];
        }
        double denom = queryNorm * Math.sqrt(norm);
        return denom == 0.0 ? 0.0 : dot / denom;
    }

    @Override
    public String add(Embedding embedding) {
        throw new RocAgentException("不支持添加无段落的向量");
    }

    @Override
    public void add(String id, Embedding embedding) {
        throw new RocAgentException("不支持添加无段落的向量");
    }

    @Override
    public String add(Embedding embedding, TextSegment textSegment) {
        return addAll(Collections.singletonList(embedding), Collections.singletonList(textSegment)).getFirst();
    }

    @Override
    public List<String> addAll(List<Embedding> embeddingList) {
//        return addAll(embeddingList, embeddingList.stream().map(e -> TextSegment.from("")).toList());
        throw new RocAgentException("不支持批量添加无段落的向量");
    }

    @Override
    public List<String> addAll(List<Embedding> embeddingList, List<TextSegment> embedded) {
        if (embeddingList.size() != embedded.size()) {
            throw new IllegalArgumentException("embeddings 与 segments 数量不一致");
        }
        List<RagChunk> chunks = new ArrayList<>(embedded.size());
        for (int i = 0; i < embedded.size(); i++) {
            chunks.add(toChunk(embeddingList.get(i), embedded.get(i)));
        }
        List<Long> ids = AiInfra.embeddings().batchInsert(projectKey, model, chunks);

        List<String> idStrings = new ArrayList<>(ids.size());
        for (Long id : ids) {
            idStrings.add(String.valueOf(id));
        }
        return idStrings;
    }

    @Override
    public EmbeddingSearchResult<TextSegment> search(EmbeddingSearchRequest request) {
        List<RagChunk> chunks = AiInfra.embeddings().loadByProject(projectKey, model);
        float[] queryVector = request.queryEmbedding().vector();
        String queryText = request.query();

        double queryNorm = 0.0;
        for (float v : queryVector) {
            queryNorm += v * v;
        }
        queryNorm = Math.sqrt(queryNorm);

        // 向量路：余弦相似度，按 chunk.id 预建索引供 RRF 直接取用
        List<EmbeddingMatch<TextSegment>> vectorMatches = new ArrayList<>();
        Map<Long, EmbeddingMatch<TextSegment>> byId = new HashMap<>();
        for (RagChunk chunk : chunks) {
            double score = (cosine(queryVector, chunk.vector(), queryNorm) + 1) / 2;
            if (score >= request.minScore()) {
                EmbeddingMatch<TextSegment> match = new EmbeddingMatch<>(score, String.valueOf(chunk.id()),
                        Embedding.from(chunk.vector()),
                        TextSegment.from(chunk.content(), Metadata.from("file_path", chunk.filePath())));
                vectorMatches.add(match);
                byId.put(chunk.id(), match);
            }
        }
        vectorMatches.sort((a, b) -> Double.compare(b.score(), a.score()));

        // 关键词路：FTS5 bm25
        List<Hit> ftsHits = queryText != null && !queryText.isBlank()
                ? AiInfra.embeddings().searchFts(projectKey, model, queryText, request.maxResults() * FTS_DEPTH_FACTOR)
                : List.of();

        if (ftsHits.isEmpty()) {
            List<EmbeddingMatch<TextSegment>> topK = new ArrayList<>(
                    vectorMatches.subList(0, Math.min(request.maxResults(), vectorMatches.size())));
            return new EmbeddingSearchResult<>(topK);
        }

        // RRF 融合：按名次计分累加，被两路同时召回的 chunk 自动靠前
        Map<Long, Double> scores = new HashMap<>();
        for (int i = 0; i < vectorMatches.size(); i++) {
            scores.merge(Long.parseLong(vectorMatches.get(i).embeddingId()), 1.0 / (RRF_K + i + 1), Double::sum);
        }
        for (int i = 0; i < ftsHits.size(); i++) {
            Hit hit = ftsHits.get(i);
            scores.merge(hit.chunkId(), 1.0 / (RRF_K + i + 1), Double::sum);
            byId.putIfAbsent(hit.chunkId(), new EmbeddingMatch<>(0.0, String.valueOf(hit.chunkId()), null,
                    TextSegment.from(hit.text(), Metadata.from("file_path", hit.filePath()))));
        }

        return new EmbeddingSearchResult<>(scores.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(request.maxResults())
                .map(e -> byId.get(e.getKey()))
                .toList());
    }

    /**
     * 删除指定来源文件的全部 chunk，增量重建前调用
     */
    public void deleteByFile(String filePath) {
        AiInfra.embeddings().deleteByFile(projectKey, model, filePath);
    }

    @Override
    public void removeAll() {
        AiInfra.embeddings().deleteByProject(projectKey, model);
        AiInfra.embeddings().loadSnapshots(projectKey, model).keySet()
                .forEach(path -> AiInfra.embeddings().deleteSnapshot(projectKey, model, path));
    }

    public String getModel() {
        return model;
    }
}