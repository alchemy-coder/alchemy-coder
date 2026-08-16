package athena.coder.ai.assistant.agent.result.tester;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import athena.coder.ai.assistant.agent.result.MarkdownStrippingDeserializer;

/**
 * TESTER 输出的结构化测试结果
 * <p>
 * 关键路由字段：status（PASS/FAIL/SKIP/ERROR）
 * 其余字段用 JsonNode 保留完整数据，序列化回 JSON 存入 state 时不丢失信息
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonDeserialize(using = MarkdownStrippingDeserializer.class)
public record TesterResult(
        String status,
        JsonNode summary,
        JsonNode coverage,
        JsonNode failures,
        JsonNode executionLog
) {
}
