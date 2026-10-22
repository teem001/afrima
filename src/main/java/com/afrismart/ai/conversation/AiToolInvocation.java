package com.afrismart.ai.conversation;

import java.util.UUID;

import com.afrismart.shared.persistence.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "ai_tool_invocations")
public class AiToolInvocation extends BaseEntity {

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    /** Seq of the user message whose turn triggered this call. */
    @Column(nullable = false)
    private int seq;

    @Column(name = "tool_name", nullable = false, length = 64)
    private String toolName;

    @Column(name = "arguments_json", nullable = false, length = 4000)
    private String argumentsJson;

    @Column(name = "result_json", nullable = false, length = 20000)
    private String resultJson;

    @Column(nullable = false)
    private boolean success;

    public UUID getConversationId() {
        return conversationId;
    }

    public void setConversationId(UUID conversationId) {
        this.conversationId = conversationId;
    }

    public int getSeq() {
        return seq;
    }

    public void setSeq(int seq) {
        this.seq = seq;
    }

    public String getToolName() {
        return toolName;
    }

    public void setToolName(String toolName) {
        this.toolName = toolName;
    }

    public String getArgumentsJson() {
        return argumentsJson;
    }

    public void setArgumentsJson(String argumentsJson) {
        this.argumentsJson = argumentsJson;
    }

    public String getResultJson() {
        return resultJson;
    }

    public void setResultJson(String resultJson) {
        this.resultJson = resultJson;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }
}
