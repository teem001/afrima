package com.afrismart.ai.conversation;

import java.util.UUID;

import com.afrismart.shared.persistence.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "ai_messages")
public class AiMessage extends BaseEntity {

    public static final String USER = "user";
    public static final String ASSISTANT = "assistant";

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    @Column(nullable = false)
    private int seq;

    @Column(nullable = false, length = 16)
    private String role;

    @Column(nullable = false, length = 20000)
    private String content;

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

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
