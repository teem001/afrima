package com.afrismart.ai.engine;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record ToolCall(String id, String type, FunctionCall function) {

    public static ToolCall function(String id, String name, String argumentsJson) {
        return new ToolCall(id, "function", new FunctionCall(name, argumentsJson));
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FunctionCall(String name, String arguments) {
    }
}
