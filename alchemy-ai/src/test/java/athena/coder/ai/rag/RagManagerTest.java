package athena.coder.ai.rag;

import athena.coder.ai.spi.AiInfra;
import athena.coder.ai.spi.ModelProvider;
import athena.coder.entity.model.ModelEnum;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagManagerTest {

    private static final class FakeModelProvider implements ModelProvider {
        EmbeddingModel embeddingModel;
        EmbeddingStore<TextSegment> store;

        @Override public ChatModel chatModel(ModelEnum m) { throw new UnsupportedOperationException(); }
        @Override public EmbeddingModel embeddingModel() { return embeddingModel; }
        @Override public EmbeddingStore<TextSegment> embeddingStore(String projectKey) { return store; }
    }

    private static final class FakeEmbeddingModel implements EmbeddingModel {
        private final float[] vector;
        FakeEmbeddingModel(float[] vector) { this.vector = vector; }
        @Override public Response<Embedding> embed(String text) {
            return Response.from(Embedding.from(vector));
        }
        @Override public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class FakeEmbeddingStore implements EmbeddingStore<TextSegment> {
        EmbeddingSearchResult<TextSegment> result = new EmbeddingSearchResult<>(List.of());

        @Override public String add(Embedding embedding) { throw new UnsupportedOperationException(); }
        @Override public void add(String id, Embedding embedding) { throw new UnsupportedOperationException(); }
        @Override public String add(Embedding embedding, TextSegment textSegment) { throw new UnsupportedOperationException(); }
        @Override public List<String> addAll(List<Embedding> embeddingList) { throw new UnsupportedOperationException(); }
        @Override public EmbeddingSearchResult<TextSegment> search(EmbeddingSearchRequest request) { return result; }
    }

    @AfterEach
    void resetInfra() {
        AiInfra.bind(null, null, null, null, null);
    }

    private static void bind(String projectPath, ModelProvider provider) {
        AiInfra.bind(null, null, null, () -> projectPath, provider);
    }

    @Test
    void retrieve_nullProjectPath_returnsEmpty() {
        bind(null, providerWithModel());
        assertTrue(RagManager.getInstance().retrieve("query", 5).isEmpty());
    }

    @Test
    void retrieve_blankQuery_returnsEmpty() {
        bind("/proj", providerWithModel());
        assertTrue(RagManager.getInstance().retrieve("   ", 5).isEmpty());
    }

    @Test
    void retrieve_nullEmbeddingModel_returnsEmpty() {
        FakeModelProvider provider = new FakeModelProvider();
        provider.embeddingModel = null;
        bind("/proj", provider);
        assertTrue(RagManager.getInstance().retrieve("query", 5).isEmpty());
    }

    @Test
    void retrieve_happyPath_returnsContents() {
        FakeModelProvider provider = providerWithModel();
        FakeEmbeddingStore store = new FakeEmbeddingStore();
        store.result = new EmbeddingSearchResult<>(List.of(
                new EmbeddingMatch<>(0.9, "1", Embedding.from(new float[]{1, 0, 0}),
                        TextSegment.from("code", Metadata.from("file_path", "A.java")))
        ));
        provider.store = store;
        bind("/proj", provider);

        List<Content> contents = RagManager.getInstance().retrieve("query", 5);
        assertEquals(1, contents.size());
        assertEquals("文件: A.java\ncode", contents.getFirst().textSegment().text());
    }

    private static FakeModelProvider providerWithModel() {
        FakeModelProvider p = new FakeModelProvider();
        p.embeddingModel = new FakeEmbeddingModel(new float[]{1, 0, 0});
        p.store = new FakeEmbeddingStore();
        return p;
    }
}
