package athena.coder.ai.spi;

import athena.coder.ai.rag.SqliteEmbeddingStore;
import athena.coder.entity.model.EmbeddingModelEnum;
import athena.coder.entity.model.ModelEnum;
import athena.coder.entity.model.ModelType;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;

import java.util.Objects;

/**
 * {@link ModelProvider} 默认实现。
 * <p>
 * apiKey 取自 model 表（{@link ModelConfigPort}），具体 builder 内聚在
 * {@link ModelEnum}/{@link EmbeddingModelEnum} 枚举工厂内。
 */
public final class DefaultModelProvider implements ModelProvider {

    private final ModelConfigPort models;

    public DefaultModelProvider(ModelConfigPort models) {
        this.models = models;
    }

    @Override
    public ChatModel chatModel(ModelEnum modelEnum) {
        String apiKey = Objects.requireNonNull(
                models.findApiKey(ModelType.CHAT, modelEnum.getModel(), modelEnum.getVersion()),
                "模型配置缺失: " + modelEnum);
        return modelEnum.getFactory().apply(apiKey);
    }

    @Override
    public EmbeddingModel embeddingModel() {
        EmbeddingModelEnum modelEnum = EmbeddingModelEnum.QIANWEN_EMBEDDING_V4;
        String apiKey = models.findApiKey(ModelType.EMBEDDING, modelEnum.getModel(), modelEnum.getVersion());
        if (apiKey == null || apiKey.isBlank()) {
            ErrorLogger.warn("DefaultModelProvider", "model 表未配置 embedding 模型: " + modelEnum.key() + "，RAG 已降级");
            return null;
        }
        try {
            return modelEnum.getFactory().apply(apiKey);
        } catch (Exception e) {
            ErrorLogger.log("DefaultModelProvider.embeddingModel", e);
            return null;
        }
    }

    @Override
    public EmbeddingStore<TextSegment> embeddingStore(String projectKey) {
        return new SqliteEmbeddingStore(projectKey, EmbeddingModelEnum.QIANWEN_EMBEDDING_V4.key());
    }
}
