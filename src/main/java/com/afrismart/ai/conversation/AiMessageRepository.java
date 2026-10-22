package com.afrismart.ai.conversation;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AiMessageRepository extends JpaRepository<AiMessage, UUID> {

    List<AiMessage> findByConversationIdOrderBySeqDesc(UUID conversationId, Pageable pageable);

    List<AiMessage> findByConversationIdOrderBySeqAsc(UUID conversationId);

    @Query("select coalesce(max(m.seq), 0) from AiMessage m where m.conversationId = :conversationId")
    int maxSeq(@Param("conversationId") UUID conversationId);
}
