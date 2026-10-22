package com.afrismart.ai;

import java.util.List;
import java.util.UUID;

import com.afrismart.ai.tool.ToolExecution;

public record AiChatResponse(UUID conversationId, String reply, List<ToolExecution> toolCalls) {
}
