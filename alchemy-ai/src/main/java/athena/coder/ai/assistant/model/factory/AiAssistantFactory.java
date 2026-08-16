package athena.coder.ai.assistant.model.factory;

import athena.coder.ai.assistant.model.DefaultChatAssistant;
import athena.coder.ai.spi.AiInfra;
import athena.coder.ai.tool.config.AgentToolPolicy;
import athena.coder.entity.model.ModelEntity;
import athena.coder.entity.model.ModelEnum;
import dev.langchain4j.community.model.dashscope.QwenChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;

import java.util.EnumMap;
import java.util.Map;

public class AiAssistantFactory {

    private static Map<ModelEnum, IChatAssistant> buildRouterMap() {
        Map<ModelEnum, IChatAssistant> map = new EnumMap<>(ModelEnum.class);
        map.put(ModelEnum.QIANWEN37MAX, new DefaultChatAssistant(
                key -> QwenChatModel.builder().apiKey(key).modelName("qwen3.7-max").build()));
        map.put(ModelEnum.QIANWEN35FLASH, new DefaultChatAssistant(
                key -> QwenChatModel.builder().apiKey(key).modelName("qwen3.5-flash").build()));
        map.put(ModelEnum.DEEPSEEKV4PRO, new DefaultChatAssistant(
                key -> OpenAiChatModel.builder()
                        .apiKey(key)
                        .baseUrl("https://api.deepseek.com")
                        .modelName("deepseek-v4-pro")
                        .responseFormat("json_object")
                        .build()));
        return map;
    }

    private static Map<ModelEnum, IChatAssistant> getRouterMap() {
        return Holder.MAP;
    }

    public static <T> T newChatAssistant(ModelEnum modelEnum, Class<T> agentClass, AgentToolPolicy policy) {
        ModelEntity modelEntity = AiInfra.models().findByModel(modelEnum);
        IChatAssistant iChatAssistant = getRouterMap().get(modelEnum);
        if (iChatAssistant == null) {
            throw new IllegalArgumentException("未找到模型路由: " + modelEnum);
        }
        return iChatAssistant.getChatAssistant(modelEntity.getApiKey(), agentClass, policy);
    }

    private static class Holder {
        static final Map<ModelEnum, IChatAssistant> MAP = buildRouterMap();
    }
}
