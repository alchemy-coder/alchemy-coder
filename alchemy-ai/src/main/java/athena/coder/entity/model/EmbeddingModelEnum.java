package athena.coder.entity.model;

import dev.langchain4j.community.model.dashscope.QwenEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;

import java.util.function.Function;

/**
 * 向量模型枚举：与 {@link LLMModelEnum} 同构（model/version 对应 model 表的 name/version），
 * 工厂函数内聚在枚举内，新增模型只需加一个条目。
 */
public enum EmbeddingModelEnum {

    QIANWEN_EMBEDDING_V4("qianwen", "text-embedding-v4",
            key -> QwenEmbeddingModel.builder().apiKey(key).modelName("text-embedding-v4").build()),
    ;

    private final String model;
    private final String version;
    private final Function<String, EmbeddingModel> factory;

    EmbeddingModelEnum(String model, String version, Function<String, EmbeddingModel> factory) {
        this.model = model;
        this.version = version;
        this.factory = factory;
    }

    public String getModel() {
        return model;
    }

    public String getVersion() {
        return version;
    }

    public Function<String, EmbeddingModel> getFactory() {
        return factory;
    }

    public static EmbeddingModelEnum fromNameVersion(String name, String version) {
        for (EmbeddingModelEnum e : values()) {
            if (e.model.equals(name) && e.version.equals(version)) {
                return e;
            }
        }
        return null;
    }

    /**
     * 落库的模型标识（隔离不同模型的向量，互不兼容）
     */
    public String key() {
        return model + "/" + version;
    }
}