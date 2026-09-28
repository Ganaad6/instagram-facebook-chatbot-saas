package com.chatbot.saas.repository;

import com.chatbot.saas.entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {
    List<Message> findAllByConversationIdOrderByIdAsc(Long conversationId);
    List<Message> findAllByBusinessId(Long businessId);
    boolean existsByBusinessIdAndMessageId(Long businessId, String messageId);
}
