package athena.coder.ai.assistant.model.factory;

import athena.coder.ai.assistant.agent.PlannerAgent;
import athena.coder.ai.assistant.agent.UserFaceAssistant;
import athena.coder.ai.assistant.model.ByteCountEstimator;
import athena.coder.ai.spi.AiInfra;
import athena.coder.ai.tool.ToolRegistry;
import athena.coder.ai.tool.config.AgentToolPolicy;
import athena.coder.ai.util.ProjectKeyUtil;
import athena.coder.entity.model.ModelEnum;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.chat.TokenWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.store.embedding.EmbeddingStore;

import java.util.Map;

/**
 * 智能体组装工厂：ChatModel 由注入的 {@link athena.coder.ai.spi.ModelProvider} 提供，
 * 本类只负责按「角色策略」组装 AiServices（工具集 + 记忆 + RAG 增强）。
 */
public final class AiAssistantFactory {

    private AiAssistantFactory() {
    }

    public static <T> T newChatAssistant(ModelEnum modelEnum, Class<T> agentClass, AgentToolPolicy policy) {
        ChatModel model = AiInfra.modelProvider().chatModel(modelEnum);
        return buildAssistant(agentClass, model, policy);
    }

    private static <T> T buildAssistant(Class<T> agentClass, ChatModel model, AgentToolPolicy policy) {
        Map<ToolSpecification, ToolExecutor> tools = ToolRegistry.getToolsForAgent(policy);

        AiServices<T> builder = AiServices.builder(agentClass)
                .chatModel(model)
                .tools(tools)
                .chatMemoryProvider(memoryId -> {
                    var memoryBuilder = TokenWindowChatMemory.builder()
                            .id(memoryId)
                            .maxTokens(26000, new ByteCountEstimator());
                    if (memoryId != null && !"default".equals(memoryId) && !memoryId.equals(0L)) {
                        memoryBuilder.chatMemoryStore(AiInfra.chatMemory(agentClass.getSimpleName()));
                    }
                    // 否则 TokenWindowChatMemory 默认使用 InMemoryChatMemoryStore
                    return memoryBuilder.build();
                });
        // 入口分流与规划需要项目知识增强；RAG 不可用时 retriever 静默返回空
        if (agentClass == UserFaceAssistant.class || agentClass == PlannerAgent.class) {
            String projectPath = AiInfra.projectPath();
            EmbeddingModel embeddingModel = AiInfra.modelProvider().embeddingModel();
            EmbeddingStore<TextSegment> store = AiInfra.modelProvider().embeddingStore(ProjectKeyUtil.projectKey(projectPath));
            EmbeddingStoreContentRetriever retriever = EmbeddingStoreContentRetriever.builder()
                    .embeddingStore(store)
                    .embeddingModel(embeddingModel)
                    .build();
            builder.contentRetriever(retriever);
        }
        return builder.build();
    }
}
