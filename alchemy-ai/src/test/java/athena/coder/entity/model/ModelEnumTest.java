package athena.coder.entity.model;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ModelEnumTest {

    @Test
    void modelType_dbValue() {
        assertEquals("chat", ModelType.CHAT.dbValue());
        assertEquals("embedding", ModelType.EMBEDDING.dbValue());
    }

    @Test
    void chatFactories_buildNonNullModels() {
        for (LLMModelEnum m : LLMModelEnum.values()) {
            ChatModel model = m.getFactory().apply("sk-test");
            assertNotNull(model, m.name());
        }
    }

    @Test
    void modelEnum_metadata() {
        assertEquals("qianwen", LLMModelEnum.QIANWEN37MAX.getModel());
        assertEquals("qwen3.7-max", LLMModelEnum.QIANWEN37MAX.getVersion());
        assertEquals("deepseek", LLMModelEnum.DEEPSEEKV4PRO.getModel());
        assertEquals("deepseek-v4-pro", LLMModelEnum.DEEPSEEKV4PRO.getVersion());
    }

    @Test
    void embeddingFactory_buildsNonNullModel() {
        EmbeddingModelEnum e = EmbeddingModelEnum.QIANWEN_EMBEDDING_V4;
        EmbeddingModel model = e.getFactory().apply("sk-test");
        assertNotNull(model);
    }

    @Test
    void embeddingEnum_metadataAndKey() {
        EmbeddingModelEnum e = EmbeddingModelEnum.QIANWEN_EMBEDDING_V4;
        assertEquals("qianwen", e.getModel());
        assertEquals("text-embedding-v4", e.getVersion());
        assertEquals("qianwen/text-embedding-v4", e.key());
    }
}
