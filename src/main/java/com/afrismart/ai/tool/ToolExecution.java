package com.afrismart.ai.tool;

import java.util.Map;

public record ToolExecution(String tool, Map<String, Object> arguments, ToolResult result) {
}
