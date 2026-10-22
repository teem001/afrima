package com.afrismart.ai.conversation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AiConversationRepository extends JpaRepository<AiConversation, UUID> {

    List<AiConversation> findByUserIdOrderByLastMessageAtDesc(UUID userId);

    Optional<AiConversation> findByIdAndUserId(UUID id, UUID userId);
}
