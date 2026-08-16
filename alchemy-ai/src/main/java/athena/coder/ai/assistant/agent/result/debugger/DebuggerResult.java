package athena.coder.ai.assistant.agent.result.debugger;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * DEBUGGER 输出的结构化诊断结果
 * <p>
 * 关键路由字段：shouldEscalate
 * 其余字段用 JsonNode 保留完整数据，序列化回 JSON 存入 state 时不丢失信息
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DebuggerResult(
        String debugSessionId,
        JsonNode errorClassification,
        JsonNode rootCauseAnalysis,
        JsonNode fixStrategy,
        boolean shouldEscalate,
        JsonNode escalationInfo
) {
}
