package athena.coder.entity.model;

import dev.langchain4j.community.model.dashscope.QwenChatModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;

import java.util.function.Function;

/**
 * 语言大模型枚举：与 {@link EmbeddingModelEnum} 同构（model/version 对应 model 表的 name/version），
 * 工厂函数内聚在枚举内，新增模型只需加一个条目。
 */
public enum LLMModelEnum {

    QIANWEN37MAX("qianwen", "qwen3.7-max",
            key -> QwenChatModel.builder().apiKey(key).modelName("qwen3.7-max").build()),
    QIANWEN35FLASH("qianwen", "qwen3.5-flash",
            key -> QwenChatModel.builder().apiKey(key).modelName("qwen3.5-flash").build()),
    DEEPSEEKV4PRO("deepseek", "deepseek-v4-pro",
            key -> OpenAiChatModel.builder()
                    .apiKey(key)
                    .baseUrl("https://api.deepseek.com")
                    .modelName("deepseek-v4-pro")
                    .responseFormat("json_object")
                    .build()),
    ;

    private final String model;
    private final String version;
    private final Function<String, ChatModel> factory;

    LLMModelEnum(String model, String version, Function<String, ChatModel> factory) {
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

    public Function<String, ChatModel> getFactory() {
        return factory;
    }
}
