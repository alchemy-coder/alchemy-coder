package athena.coder.ai.assistant.agent.result.reviewer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * REVIEWER 输出的结构化审查结果
 * <p>
 * 关键路由字段：verdict（APPROVED/APPROVED_WITH_NOTES/REQUEST_CHANGES/BLOCKED）
 * 其余字段用 JsonNode 保留完整数据，序列化回 JSON 存入 state 时不丢失信息
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ReviewerResult(
        String reviewSessionId,
        String verdict,
        String summary,
        JsonNode stageResults,
        JsonNode issues,
        JsonNode requirementDetail,
        JsonNode improvements
) {
}
