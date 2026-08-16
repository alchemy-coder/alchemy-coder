package athena.coder.ai.assistant.agent.result.coder;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import athena.coder.ai.assistant.agent.result.MarkdownStrippingDeserializer;

/**
 * CODER 输出的结构化变更结果
 * <p>
 * changedFiles 为落地文件清单，notes 为补充说明；WriterNode 仅消费这两个字段
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonDeserialize(using = MarkdownStrippingDeserializer.class)
public record CoderResult(
        JsonNode changedFiles,
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
