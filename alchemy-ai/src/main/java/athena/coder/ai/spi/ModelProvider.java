package athena.coder.ai.spi;

import athena.coder.entity.model.LLMModelEnum;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;

/**
 * 模型与 RAG 存储提供者（依赖反转端口）。
 * <p>
 * 组合根（app 层 ApplicationLauncher）组装具体实现并经 {@link AiInfra#bind} 注入；
 * ai 层消费方只依赖本端口，不再自行构建 ChatModel / EmbeddingModel / EmbeddingStore。
 */
public interface ModelProvider {

    /**
     * 按模型枚举构建语言大模型；未配置 apiKey 时抛出异常。
     */
    ChatModel chatModel(LLMModelEnum modelEnum);

    /**
     * 构建向量大模型；未配置 apiKey 或构建失败返回 null（RAG 降级）。
     */
    EmbeddingModel embeddingModel();

    /**
     * 按项目 key 构建 RAG 向量存储（无缓存，每次新建）。
     */
    EmbeddingStore<TextSegment> embeddingStore(String projectKey);
}
