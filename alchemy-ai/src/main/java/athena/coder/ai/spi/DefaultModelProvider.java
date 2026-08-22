package athena.coder.ai.spi;

import athena.coder.ai.rag.SqliteEmbeddingStore;
import athena.coder.entity.model.EmbeddingModelEnum;
import athena.coder.entity.model.LLMModelEnum;
import athena.coder.entity.model.ModelType;
import dev.langchain4j.community.model.dashscope.QwenChatModel;
import dev.langchain4j.community.model.dashscope.QwenEmbeddingModel;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.store.embedding.EmbeddingStore;

import java.util.Objects;

/**
 * {@link ModelProvider} 默认实现。
 * <p>
 * apiKey 取自 model 表（{@link ModelConfigPort}），模型构建逻辑内聚于此。
 */
public final class DefaultModelProvider implements ModelProvider {

    private final ModelConfigPort models;

    public DefaultModelProvider(ModelConfigPort models) {
        this.models = models;
    }

    @Override
    public ChatModel chatModel(LLMModelEnum modelEnum) {
        String apiKey = Objects.requireNonNull(
                models.findApiKey(ModelType.CHAT, modelEnum.getModel(), modelEnum.getVersion()),
                "模型配置缺失: " + modelEnum);
        return buildChatModel(modelEnum, apiKey);
    }

    private ChatModel buildChatModel(LLMModelEnum modelEnum, String apiKey) {
        return switch (modelEnum) {
            case QIANWEN37MAX -> QwenChatModel.builder().apiKey(apiKey).modelName("qwen3.7-max").build();
            case QIANWEN35FLASH -> QwenChatModel.builder().apiKey(apiKey).modelName("qwen3.5-flash").build();
            case DEEPSEEKV4PRO -> OpenAiChatModel.builder()
                    .apiKey(apiKey)
                    .baseUrl("https://api.deepseek.com")
                    .modelName("deepseek-v4-pro")
                    .responseFormat("json_object")
                    .build();
            default -> throw new IllegalArgumentException("不支持的模型: " + modelEnum);
        };
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
            return buildEmbeddingModel(modelEnum, apiKey);
        } catch (Exception e) {
            ErrorLogger.log("DefaultModelProvider.embeddingModel", e);
            return null;
        }
    }

    private EmbeddingModel buildEmbeddingModel(EmbeddingModelEnum modelEnum, String apiKey) {
        return switch (modelEnum) {
            case QIANWEN_EMBEDDING_V4 -> QwenEmbeddingModel.builder().apiKey(apiKey).modelName("text-embedding-v4").build();
            default -> throw new IllegalArgumentException("不支持的模型: " + modelEnum);
        };
    }

    @Override
    public EmbeddingStore<TextSegment> embeddingStore(String projectKey) {
        return new SqliteEmbeddingStore(projectKey, EmbeddingModelEnum.QIANWEN_EMBEDDING_V4.key());
    }
}