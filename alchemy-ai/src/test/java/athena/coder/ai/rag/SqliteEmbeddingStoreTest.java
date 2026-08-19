package athena.coder.ai.rag;

import athena.coder.ai.rag.model.Hit;
import athena.coder.ai.rag.model.RagChunk;
import athena.coder.ai.spi.AiInfra;
import athena.coder.ai.support.StubEmbeddingRepository;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqliteEmbeddingStoreTest {

    private final StubEmbeddingRepository repo = new StubEmbeddingRepository();

    @AfterEach
    void resetInfra() {
        AiInfra.bind(null, null, null, null, null, null);
    }

    private static SqliteEmbeddingStore store() {
        return new SqliteEmbeddingStore("pk", "qianwen/text-embedding-v4");
    }

    private static EmbeddingSearchRequest request(float[] query, int maxResults, double minScore) {
        return EmbeddingSearchRequest.builder()
                .query("foo bar")
                .queryEmbedding(Embedding.from(query))
                .maxResults(maxResults)
                .minScore(minScore)
                .build();
    }

    private static List<String> ids(EmbeddingSearchResult<TextSegment> result) {
        return result.matches().stream().map(EmbeddingMatch::embeddingId).toList();
    }

    @Test
    void search_cosineRanking_similarFirst() {
        repo.loadByProject = List.of(
                new RagChunk(1L, "B.java", "bbb", new float[]{0, 1, 0}),
                new RagChunk(2L, "A.java", "aaa", new float[]{1, 0, 0})
        );
        repo.searchFts = List.of();
        AiInfra.bind(null, repo, null, null, null, null);

        EmbeddingSearchResult<TextSegment> result = store().search(request(new float[]{1, 0, 0}, 10, 0.5));
        assertEquals(List.of("2", "1"), ids(result));
    }

    @Test
    void search_minScoreFiltersOrthogonal() {
        repo.loadByProject = List.of(
                new RagChunk(2L, "A.java", "aaa", new float[]{1, 0, 0}),  // cos=1 -> score 1.0
                new RagChunk(1L, "B.java", "bbb", new float[]{0, 1, 0})   // cos=0 -> score 0.5, 被 minScore=0.51 过滤
        );
        repo.searchFts = List.of();
        AiInfra.bind(null, repo, null, null, null, null);

        EmbeddingSearchResult<TextSegment> result = store().search(request(new float[]{1, 0, 0}, 10, 0.51));
        assertEquals(List.of("2"), ids(result));
    }

    @Test
    void search_ftsEmpty_returnsVectorTopK() {
        repo.loadByProject = List.of(
                new RagChunk(2L, "A.java", "aaa", new float[]{1, 0, 0}),
                new RagChunk(1L, "B.java", "bbb", new float[]{0, 1, 0})
        );
        repo.searchFts = List.of();
        AiInfra.bind(null, repo, null, null, null, null);

        EmbeddingSearchResult<TextSegment> result = store().search(request(new float[]{1, 0, 0}, 1, 0.0));
        assertEquals(1, result.matches().size());
        assertEquals("2", ids(result).getFirst());
    }

    @Test
    void search_rrfFusion_promotesBothRecalled() {
        repo.loadByProject = List.of(new RagChunk(1L, "A.java", "aaa", new float[]{1, 0, 0}));
        repo.searchFts = List.of(new Hit(1L, "A.java", "aaa"), new Hit(2L, "B.java", "bbb"));
        AiInfra.bind(null, repo, null, null, null, null);

        EmbeddingSearchResult<TextSegment> result = store().search(request(new float[]{1, 0, 0}, 10, 0.5));
        assertEquals(2, result.matches().size());
        assertEquals("1", ids(result).getFirst());  // chunk1 被向量+FTS 双路召回，靠前
    }

    @Test
    void addAll_delegatesToBatchInsert() {
        AiInfra.bind(null, repo, null, null, null, null);

        List<String> ids = store().addAll(
                List.of(Embedding.from(new float[]{1, 0, 0})),
                List.of(TextSegment.from("code", Metadata.from("file_path", "A.java")))
        );
        assertTrue(repo.batchInsertCalled);
        assertEquals(List.of("10"), ids);
    }
}
