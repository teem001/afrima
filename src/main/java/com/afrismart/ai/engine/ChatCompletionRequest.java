package com.afrismart.ai.engine;

import java.util.List;

import com.afrismart.ai.tool.ToolDefinition;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatCompletionRequest(
        String model,
        List<ChatMessage> messages,
        List<ToolSpec> tools,
        @JsonProperty("tool_choice") String toolChoice,
        Double temperature
) {

    public record ToolSpec(String type, ToolDefinition function) {

        public static ToolSpec of(ToolDefinition definition) {
            return new ToolSpec("function", definition);
        }
    }
}
