package athena.coder.ai.assistant.agent.result.coder;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import athena.coder.ai.assistant.agent.result.MarkdownStrippingDeserializer;

/**
 * CODER 输出的结构化变更结果
 * <p>
 * 关键路由字段：status / changedFiles / compilationStatus
 * 其余字段用 JsonNode 保留完整数据，序列化回 JSON 时不丢失信息
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonDeserialize(using = MarkdownStrippingDeserializer.class)
public record CoderResult(
        String status,
        JsonNode completedTasks,
        JsonNode failedTasks,
        JsonNode changedFiles,
        String compilationStatus,
        String notes
) {
    /**
     * 将 changedFiles JSON 数组转为逗号分隔字符串（供 state 存储）
     */
    public String changedFilesAsCsv() {
        if (changedFiles == null || !changedFiles.isArray() || changedFiles.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (JsonNode f : changedFiles) {
            String text = f.asText();
            if (text != null && !text.isBlank()) {
                if (!sb.isEmpty()) sb.append(',');
                sb.append(text);
            }
        }
        return sb.toString();
    }
}
