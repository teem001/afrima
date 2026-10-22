package com.afrismart.ai.conversation;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.afrismart.ai.engine.ChatMessage;
import com.afrismart.ai.tool.ToolExecution;
import com.afrismart.shared.api.ApiException;
import com.afrismart.shared.api.ErrorCodes;
import com.afrismart.shared.security.AuthUser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class AiConversationService {

    private static final int TITLE_LENGTH = 80;
    private static final int MESSAGE_LENGTH = 20_000;
    private static final int ARGUMENTS_LENGTH = 4_000;
    private static final int TOOL_MEMORY_RESULT_LENGTH = 1_500;
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final AiConversationRepository conversations;
    private final AiMessageRepository messages;
    private final AiToolInvocationRepository invocations;
    private final ObjectMapper objectMapper;

    public AiConversationService(
            AiConversationRepository conversations,
            AiMessageRepository messages,
            AiToolInvocationRepository invocations,
            ObjectMapper objectMapper
    ) {
        this.conversations = conversations;
        this.messages = messages;
        this.invocations = invocations;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public UUID open(AuthUser user, UUID conversationId, String firstMessage) {
        if (conversationId != null) {
            return require(user, conversationId).getId();
        }
        AiConversation conversation = new AiConversation();
        conversation.setUserId(user.id());
        conversation.setTitle(truncate(firstMessage.strip().replaceAll("\\s+", " "), TITLE_LENGTH));
        conversation.setLastMessageAt(Instant.now());
        return conversations.save(conversation).getId();
    }

    /** User and assistant turns only; tool traffic is replayed separately via {@link #recentToolContext}. */
    @Transactional(readOnly = true)
    public List<ChatMessage> history(UUID conversationId, int window) {
        List<AiMessage> recent = new ArrayList<>(
                messages.findByConversationIdOrderBySeqDesc(conversationId, PageRequest.of(0, Math.max(1, window)))
        );
        Collections.reverse(recent);
        return recent.stream()
                .map(message -> AiMessage.USER.equals(message.getRole())
                        ? ChatMessage.user(message.getContent())
                        : ChatMessage.assistant(message.getContent()))
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<String> recentToolContext(UUID conversationId, int turns) {
        int since = messages.maxSeq(conversationId) - turns * 2;
        List<AiToolInvocation> recent = invocations
                .findByConversationIdAndSeqGreaterThanOrderBySeqAscCreatedAtAsc(conversationId, since)
                .stream()
                .filter(AiToolInvocation::isSuccess)
                .toList();
        if (recent.isEmpty()) {
            return Optional.empty();
        }
        StringBuilder memory = new StringBuilder(
                "Tool results from earlier in this conversation. Reuse ids from here instead of guessing:\n");
        for (AiToolInvocation invocation : recent) {
            memory.append("- ").append(invocation.getToolName())
                    .append(' ').append(invocation.getArgumentsJson())
                    .append(" -> ").append(truncate(invocation.getResultJson(), TOOL_MEMORY_RESULT_LENGTH))
                    .append('\n');
        }
        return Optional.of(memory.toString());
    }

    @Transactional
    public void recordExchange(UUID conversationId, String userMessage, String reply, List<ToolExecution> executions) {
        AiConversation conversation = conversations.findById(conversationId)
                .orElseThrow(AiConversationService::conversationNotFound);
        int userSeq = messages.maxSeq(conversationId) + 1;
        messages.save(message(conversationId, userSeq, AiMessage.USER, userMessage));
        for (ToolExecution execution : executions) {
            AiToolInvocation invocation = new AiToolInvocation();
            invocation.setConversationId(conversationId);
            invocation.setSeq(userSeq);
            invocation.setToolName(truncate(execution.tool(), 64));
            invocation.setArgumentsJson(truncate(toJson(execution.arguments()), ARGUMENTS_LENGTH));
            invocation.setResultJson(truncate(toJson(execution.result()), MESSAGE_LENGTH));
            invocation.setSuccess(execution.result().success());
            invocations.save(invocation);
        }
        messages.save(message(conversationId, userSeq + 1, AiMessage.ASSISTANT, reply));
        conversation.setLastMessageAt(Instant.now());
    }

    @Transactional(readOnly = true)
    public List<ConversationSummary> list(AuthUser user) {
        return conversations.findByUserIdOrderByLastMessageAtDesc(user.id()).stream()
                .map(c -> new ConversationSummary(c.getId(), c.getTitle(), c.getCreatedAt(), c.getLastMessageAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public ConversationDetail detail(AuthUser user, UUID conversationId) {
        AiConversation conversation = require(user, conversationId);
        List<MessageView> transcript = messages.findByConversationIdOrderBySeqAsc(conversationId).stream()
                .map(m -> new MessageView(m.getSeq(), m.getRole(), m.getContent(), m.getCreatedAt()))
                .toList();
        List<ToolCallView> toolCalls = invocations.findByConversationIdOrderBySeqAscCreatedAtAsc(conversationId).stream()
                .map(i -> new ToolCallView(i.getSeq(), i.getToolName(), parse(i.getArgumentsJson()), i.isSuccess(), i.getCreatedAt()))
                .toList();
        return new ConversationDetail(conversation.getId(), conversation.getTitle(), transcript, toolCalls);
    }

    private AiConversation require(AuthUser user, UUID conversationId) {
        return conversations.findByIdAndUserId(conversationId, user.id())
                .orElseThrow(AiConversationService::conversationNotFound);
    }

    private static ApiException conversationNotFound() {
        return ApiException.notFound(ErrorCodes.CONVERSATION_NOT_FOUND, "Conversation not found");
    }

    private static AiMessage message(UUID conversationId, int seq, String role, String content) {
        AiMessage message = new AiMessage();
        message.setConversationId(conversationId);
        message.setSeq(seq);
        message.setRole(role);
        message.setContent(truncate(content, MESSAGE_LENGTH));
        return message;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }

    private Object parse(String json) {
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException ex) {
            return json;
        }
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }

    public record ConversationSummary(UUID id, String title, Instant createdAt, Instant lastMessageAt) {
    }

    public record ConversationDetail(UUID id, String title, List<MessageView> messages, List<ToolCallView> toolCalls) {
    }

    public record MessageView(int seq, String role, String content, Instant createdAt) {
    }

    public record ToolCallView(int seq, String tool, Object arguments, boolean success, Instant createdAt) {
    }
}
