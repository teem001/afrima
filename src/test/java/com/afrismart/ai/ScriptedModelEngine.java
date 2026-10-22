package com.afrismart.ai;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import com.afrismart.ai.engine.AiEngineType;
import com.afrismart.ai.engine.ChatMessage;
import com.afrismart.ai.engine.ModelEngine;
import com.afrismart.ai.tool.ToolDefinition;

/** Plays back queued model turns so the agent loop can be tested without a real LLM. */
public class ScriptedModelEngine implements ModelEngine {

    private final Deque<ChatMessage> replies = new ArrayDeque<>();
    private final List<List<ChatMessage>> requests = new ArrayList<>();
    private final List<List<ToolDefinition>> toolRequests = new ArrayList<>();

    public synchronized void enqueue(ChatMessage... messages) {
        replies.addAll(List.of(messages));
    }

    public synchronized void reset() {
        replies.clear();
        requests.clear();
        toolRequests.clear();
    }

    public synchronized List<List<ChatMessage>> requests() {
        return List.copyOf(requests);
    }

    public synchronized List<List<ToolDefinition>> toolRequests() {
        return List.copyOf(toolRequests);
    }

    @Override
    public boolean supports(AiEngineType type) {
        return type == AiEngineType.OPENAI;
    }

    @Override
    public boolean isConfigured() {
        return true;
    }

    @Override
    public String model() {
        return "scripted";
    }

    @Override
    public synchronized ChatMessage complete(List<ChatMessage> messages, List<ToolDefinition> tools) {
        requests.add(List.copyOf(messages));
        toolRequests.add(List.copyOf(tools));
        ChatMessage next = replies.poll();
        return next == null ? ChatMessage.assistant("(script exhausted)") : next;
    }
}
