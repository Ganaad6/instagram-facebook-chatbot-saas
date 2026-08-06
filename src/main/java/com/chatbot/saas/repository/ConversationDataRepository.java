package com.chatbot.saas.repository;

import com.chatbot.saas.entity.ConversationData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ConversationDataRepository extends JpaRepository<ConversationData, Long> {
    List<ConversationData> findAllByConversationId(Long conversationId);
}
