package com.afrismart.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.afrismart.ai.conversation.AiConversationService;
import com.afrismart.ai.engine.ChatMessage;
import com.afrismart.ai.engine.ModelEngine;
import com.afrismart.ai.engine.ModelEngineRegistry;
import com.afrismart.ai.engine.ToolCall;
import com.afrismart.ai.tool.AiToolContext;
import com.afrismart.ai.tool.AiToolRegistry;
import com.afrismart.ai.tool.ToolDefinition;
import com.afrismart.ai.tool.ToolExecution;
import com.afrismart.shared.security.AuthUser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Runs the model/tool loop. Deliberately not transactional: model calls can take seconds, and each tool
 * commits its own work so a later failure doesn't roll back an order the model already confirmed.
 */
@Service
public class AiAgentService {

    private static final Logger log = LoggerFactory.getLogger(AiAgentService.class);
    private static final int TOOL_MEMORY_TURNS = 3;
    private static final int TOOL_RESULT_LENGTH = 12_000;
    private static final String FALLBACK_REPLY =
            "Sorry, I couldn't finish that request. Could you rephrase it or break it into smaller steps?";

    private final ModelEngineRegistry engines;
    private final AiToolRegistry tools;
    private final AiConversationService conversations;
    private final AiPrompts prompts;
    private final AiProperties properties;
    private final ObjectMapper objectMapper;

    public AiAgentService(
            ModelEngineRegistry engines,
            AiToolRegistry tools,
            AiConversationService conversations,
            AiPrompts prompts,
            AiProperties properties,
            ObjectMapper objectMapper
    ) {
        this.engines = engines;
        this.tools = tools;
        this.conversations = conversations;
        this.prompts = prompts;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public AiChatResponse chat(AuthUser user, AiChatRequest request) {
        return chat(user, request, AiProgressListener.NONE);
    }

    public AiChatResponse chat(AuthUser user, AiChatRequest request, AiProgressListener listener) {
        ModelEngine engine = engines.active();
        AiToolContext context = new AiToolContext(user, request.latitude(), request.longitude());
        String userMessage = request.message().strip();
        UUID conversationId = conversations.open(user, request.conversationId(), userMessage);
        listener.conversationOpened(conversationId);

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system(prompts.systemPrompt(context, request.context())));
        conversations.recentToolContext(conversationId, TOOL_MEMORY_TURNS)
                .ifPresent(memory -> messages.add(ChatMessage.system(memory)));
        messages.addAll(conversations.history(conversationId, properties.getHistoryWindow()));
        messages.add(ChatMessage.user(userMessage));

        List<ToolDefinition> definitions = tools.definitionsFor(user.role());
        List<ToolExecution> executions = new ArrayList<>();
        String reply = null;

        for (int round = 0; round < properties.getMaxToolRounds(); round++) {
            listener.modelTurn(round);
            ChatMessage response = engine.complete(messages, definitions);
            if (!response.requestsTools()) {
                reply = response.content();
                break;
            }
            messages.add(ChatMessage.assistantToolCalls(response.content(), response.toolCalls()));
            for (ToolCall call : response.toolCalls()) {
                listener.toolStarted(call.function().name());
                ToolExecution execution = tools.invokeJson(call.function().name(), call.function().arguments(), context);
                listener.toolFinished(execution.tool(), execution.result().success(), execution.result().message());
                executions.add(execution);
                messages.add(ChatMessage.tool(call.id(), toToolMessage(execution)));
            }
        }

        if (reply == null || reply.isBlank()) {
            log.warn("Conversation {} ended without a reply after {} tool call(s)", conversationId, executions.size());
            reply = FALLBACK_REPLY;
        }
        conversations.recordExchange(conversationId, userMessage, reply, executions);
        return new AiChatResponse(conversationId, reply, executions);
    }

    private String toToolMessage(ToolExecution execution) {
        String json;
        try {
            json = objectMapper.writeValueAsString(execution.result());
        } catch (JsonProcessingException ex) {
            json = "{\"success\":false,\"message\":\"Result could not be serialised\"}";
        }
        return json.length() <= TOOL_RESULT_LENGTH ? json : json.substring(0, TOOL_RESULT_LENGTH) + "...(truncated)";
    }
}
