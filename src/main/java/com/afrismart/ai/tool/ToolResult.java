package com.afrismart.ai.tool;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ToolResult(boolean success, String message, String errorCode, Object data) {

    public static ToolResult ok(Object data) {
        return new ToolResult(true, null, null, data);
    }

    public static ToolResult ok(String message, Object data) {
        return new ToolResult(true, message, null, data);
    }

    public static ToolResult error(String errorCode, String message) {
        return new ToolResult(false, message, errorCode, null);
    }
}
