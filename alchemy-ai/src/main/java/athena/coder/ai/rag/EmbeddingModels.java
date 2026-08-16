package athena.coder.ai.rag;

import athena.coder.ai.spi.AiInfra;
import athena.coder.ai.spi.ErrorLogger;
import athena.coder.entity.model.EmbeddingModelEnum;
import dev.langchain4j.model.embedding.EmbeddingModel;

/**
 * 向量模型工厂：按 {@link EmbeddingModelEnum} 路由构建 EmbeddingModel。
 * <p>
 * API key 取自 model 表（name+version），每次构建实时查询，key 变更即时生效；
 * 未配置或构建失败返回 null（降级信号）。
 */
public final class EmbeddingModels {

    private EmbeddingModels() {
    }

    /**
     * 构建向量模型实例（无状态对象，调用方自行复用）
     *
     * @return 模型实例；未配置 key 或构建失败返回 null
     */
    public static EmbeddingModel get(EmbeddingModelEnum modelEnum) {
        String apiKey = AiInfra.models().findApiKey(modelEnum.getModel(), modelEnum.getVersion());
        if (apiKey == null || apiKey.isBlank()) {
            ErrorLogger.warn("EmbeddingModels", "model 表未配置 embedding 模型: " + modelEnum.key() + "，RAG 已降级");
            return null;
        }
        try {
            return modelEnum.getFactory().apply(apiKey);
        } catch (Exception e) {
            ErrorLogger.log("EmbeddingModels.get", e);
            return null;
        }
    }
}
