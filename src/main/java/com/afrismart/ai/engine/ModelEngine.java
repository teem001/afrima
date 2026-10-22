package com.afrismart.ai.engine;

import java.util.List;

import com.afrismart.ai.tool.ToolDefinition;

public interface ModelEngine {

    boolean supports(AiEngineType type);

    boolean isConfigured();

    String model();

    /** One model turn; the agent loop owns tool execution and repeats until no tool calls remain. */
    ChatMessage complete(List<ChatMessage> messages, List<ToolDefinition> tools);
}
