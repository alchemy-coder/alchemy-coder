package athena.coder.ai.assistant.agent.result.summarizer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * SUMMARIZER 输出的结构化总结结果
 * <p>
 * 关键验证字段：report / commitMessage
 * 其余字段用 JsonNode 保留完整数据，序列化回 JSON 存入 state 时不丢失信息
 * <p>
 * error：节点侧失败兜底契约字段（提示词不产出，仅解析失败场景由节点填充）
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SummarizerResult(
        String sessionId,
        JsonNode report,
        JsonNode commitMessage,
        JsonNode branchSuggestion,
        String error
) {
}
