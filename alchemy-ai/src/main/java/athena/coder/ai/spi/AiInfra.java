package athena.coder.ai.spi;

import dev.langchain4j.store.memory.chat.ChatMemoryStore;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * ai 层基础设施绑定器（依赖反转的唯一注入点）。
 * <p>
 * ai 模块只依赖本包定义的端口，不依赖 infra 层；实现由组合根
 * （app 层 ApplicationLauncher）启动时经 {@link #bind} 注入。
 */
public final class AiInfra {

    private static volatile ErrorLogSink errorLog;
    private static volatile EmbeddingRepositoryPort embeddings;
    private static volatile Function<String, ChatMemoryStore> chatMemoryFactory;
    private static volatile Supplier<String> projectPath;
    private static volatile ModelProvider modelProvider;
    private static volatile AgentExecutionSink agentExecutions;

    private AiInfra() {
    }

    /**
     * 组合根装配入口：一次性注入全部端口实现
     *
     * @param chatMemory agentType → ChatMemoryStore 工厂
     * @param workPath   当前项目路径提供者
     */
    public static void bind(ErrorLogSink errorLogSink,
                            EmbeddingRepositoryPort embeddingRepository,
                            Function<String, ChatMemoryStore> chatMemory,
                            Supplier<String> workPath,
                            ModelProvider modelProvider,
                            AgentExecutionSink agentExecutionSink) {
        errorLog = errorLogSink;
        embeddings = embeddingRepository;
        chatMemoryFactory = chatMemory;
        projectPath = workPath;
        AiInfra.modelProvider = modelProvider;
        AiInfra.agentExecutions = agentExecutionSink;
    }

    /**
     * 错误日志 sink，未装配时返回 null（ErrorLogger 门面自行降级 JUL）
     */
    public static ErrorLogSink errorLog() {
        return errorLog;
    }

    public static EmbeddingRepositoryPort embeddings() {
        requireBound(embeddings, "EmbeddingRepositoryPort");
        return embeddings;
    }

    public static ChatMemoryStore chatMemory(String agentType) {
        requireBound(chatMemoryFactory, "ChatMemoryStore 工厂");
        return chatMemoryFactory.apply(agentType);
    }

    /**
     * 当前项目绝对路径，未装配/未选项目时返回 null
     */
    public static String projectPath() {
        requireBound(projectPath, "项目路径提供者");
        return projectPath.get();
    }

    /**
     * 语言/向量模型与 RAG 存储提供者（由调用方组装后注入）
     */
    public static ModelProvider modelProvider() {
        requireBound(modelProvider, "ModelProvider");
        return modelProvider;
    }

    /**
     * 执行轨迹持久化 sink（节点 + 工具），未装配时返回 null（执行轨迹静默跳过，不阻断主流程）
     */
    public static AgentExecutionSink agentExecutions() {
        return agentExecutions;
    }

    private static void requireBound(Object value, String name) {
        if (value == null) {
            throw new IllegalStateException("AiInfra 未装配: " + name + "，请在 ApplicationLauncher 启动时调用 AiInfra.bind");
        }
    }
}
