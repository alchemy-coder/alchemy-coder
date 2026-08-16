package athena.coder.ai.assistant.model.factory;

import athena.coder.ai.assistant.agent.PlannerAgent;
import athena.coder.ai.assistant.agent.UserFaceAssistant;
import athena.coder.ai.assistant.model.ByteCountEstimator;
import athena.coder.ai.rag.EmbeddingModels;
import athena.coder.ai.rag.SqliteEmbeddingStore;
import athena.coder.ai.spi.AiInfra;
import athena.coder.ai.tool.ToolRegistry;
import athena.coder.ai.tool.config.AgentToolPolicy;
import athena.coder.ai.util.ProjectKeyUtil;
import athena.coder.entity.model.EmbeddingModelEnum;
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

public interface IChatAssistant {

    <T> T getChatAssistant(String apiKey, Class<T> agentClass, AgentToolPolicy policy);

    /**
     * 获取智能体实例 - 通过 ToolRegistry 按「角色策略」获取工具集
     *
     * @param agentClass 智能体接口的 Class 对象
     * @param model      ChatModel 实例
     * @param policy     角色工具策略（工具权限由 Node 显式声明，不再按类名查表）
     * @param <T>        智能体接口类型
     * @return 配置好的智能体实例
     */
    default <T> T getAssistant(Class<T> agentClass, ChatModel model, AgentToolPolicy policy) {
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
            EmbeddingModelEnum embeddingModelEnum = EmbeddingModelEnum.QIANWEN_EMBEDDING_V4;
            EmbeddingModel embeddingModel = EmbeddingModels.get(embeddingModelEnum);
            EmbeddingStore<TextSegment> store = new SqliteEmbeddingStore(ProjectKeyUtil.projectKey(projectPath), embeddingModelEnum.key());
            EmbeddingStoreContentRetriever retriever = EmbeddingStoreContentRetriever.builder()
                    .embeddingStore(store)
                    .embeddingModel(embeddingModel)
                    .build();
            builder.contentRetriever(retriever);
        }
        return builder.build();
    }
}
