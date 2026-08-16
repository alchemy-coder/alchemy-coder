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
    private static volatile ModelConfigPort models;
    private static volatile Function<String, ChatMemoryStore> chatMemoryFactory;
    private static volatile Supplier<String> projectPath;

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
                            ModelConfigPort modelConfig,
                            Function<String, ChatMemoryStore> chatMemory,
                            Supplier<String> workPath) {
        errorLog = errorLogSink;
        embeddings = embeddingRepository;
        models = modelConfig;
        chatMemoryFactory = chatMemory;
        projectPath = workPath;
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

    public static ModelConfigPort models() {
        requireBound(models, "ModelConfigPort");
        return models;
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

    private static void requireBound(Object value, String name) {
        if (value == null) {
            throw new IllegalStateException("AiInfra 未装配: " + name + "，请在 ApplicationLauncher 启动时调用 AiInfra.bind");
        }
    }
}
