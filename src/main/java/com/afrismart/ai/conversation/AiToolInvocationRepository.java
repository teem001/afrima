package com.afrismart.ai.conversation;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AiToolInvocationRepository extends JpaRepository<AiToolInvocation, UUID> {

    List<AiToolInvocation> findByConversationIdAndSeqGreaterThanOrderBySeqAscCreatedAtAsc(UUID conversationId, int seq);

    List<AiToolInvocation> findByConversationIdOrderBySeqAscCreatedAtAsc(UUID conversationId);
}
