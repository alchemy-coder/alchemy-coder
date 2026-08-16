package athena.coder.ai.assistant.agent.result.router;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RouterResult(WorkflowMode workflowMode) {
}
