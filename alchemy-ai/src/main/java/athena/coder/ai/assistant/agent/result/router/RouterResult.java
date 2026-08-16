package athena.coder.ai.assistant.agent.result.router;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import athena.coder.ai.assistant.agent.result.MarkdownStrippingDeserializer;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonDeserialize(using = MarkdownStrippingDeserializer.class)
public record RouterResult(WorkflowMode workflowMode) {
}
