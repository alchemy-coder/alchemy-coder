package athena.coder.entity.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ModelEnumTest {

    @Test
    void modelType_dbValue() {
        assertEquals("chat", ModelType.CHAT.dbValue());
        assertEquals("embedding", ModelType.EMBEDDING.dbValue());
    }

    @Test
    void modelEnum_metadata() {
        assertEquals("qianwen", LLMModelEnum.QIANWEN37MAX.getModel());
        assertEquals("qwen3.7-max", LLMModelEnum.QIANWEN37MAX.getVersion());
        assertEquals("deepseek", LLMModelEnum.DEEPSEEKV4PRO.getModel());
        assertEquals("deepseek-v4-pro", LLMModelEnum.DEEPSEEKV4PRO.getVersion());
    }

    @Test
    void modelEnum_fromNameVersion() {
        assertEquals(LLMModelEnum.QIANWEN37MAX,
                LLMModelEnum.fromNameVersion("qianwen", "qwen3.7-max"));
        assertEquals(LLMModelEnum.DEEPSEEKV4PRO,
                LLMModelEnum.fromNameVersion("deepseek", "deepseek-v4-pro"));
    }

    @Test
    void embeddingEnum_metadataAndKey() {
        EmbeddingModelEnum e = EmbeddingModelEnum.QIANWEN_EMBEDDING_V4;
        assertEquals("qianwen", e.getModel());
        assertEquals("text-embedding-v4", e.getVersion());
        assertEquals("qianwen/text-embedding-v4", e.key());
    }
}