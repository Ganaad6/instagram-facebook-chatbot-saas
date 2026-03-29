package com.chatbot.saas.repository;

import com.chatbot.saas.entity.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, Long> {
    Optional<Conversation> findByCustomerIdAndStatus(Long customerId, Conversation.Status status);
    List<Conversation> findAllByBusinessId(Long businessId);
}
