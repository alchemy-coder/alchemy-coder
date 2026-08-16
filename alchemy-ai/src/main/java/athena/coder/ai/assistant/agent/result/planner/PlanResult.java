package athena.coder.ai.assistant.agent.result.planner;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * PLANNER 输出的结构化计划结果
 * <p>
 * designBlueprint 为 JSON 对象（与 PlannerAgent Prompt 中的 JSON 模板一致），
 * 使用时通过 {@link JsonNode#toString()} 转为 String 存入 state
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PlanResult(JsonNode designBlueprint, String acceptanceCriteria) {
}
