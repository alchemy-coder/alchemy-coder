package athena.coder.ai.assistant.model;

import athena.coder.ai.assistant.model.factory.IChatAssistant;
import athena.coder.ai.tool.config.AgentToolPolicy;
import dev.langchain4j.model.chat.ChatModel;

import java.util.function.Function;

/**
 * 通用模型路由实现（参数化），替代原来每个模型一个类的冗余结构。
 * <p>
 * 通过构造器传入 ChatModel 工厂函数（apiKey → ChatModel），
 * 新增模型只需在 AiAssistantFactory 中加一行配置。
 */
public class DefaultChatAssistant implements IChatAssistant {

    private final Function<String, ChatModel> modelFactory;

    public DefaultChatAssistant(Function<String, ChatModel> modelFactory) {
        this.modelFactory = modelFactory;
    }

    @Override
    public <T> T getChatAssistant(String apiKey, Class<T> agentClass, AgentToolPolicy policy) {
        ChatModel model = modelFactory.apply(apiKey);
        return getAssistant(agentClass, model, policy);
    }
}
