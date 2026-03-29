package com.chatbot.saas.repository;

import com.chatbot.saas.entity.ChatbotFlow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatbotFlowRepository extends JpaRepository<ChatbotFlow, Long> {
    Optional<ChatbotFlow> findByBusinessIdAndIsActiveTrue(Long businessId);
    List<ChatbotFlow> findAllByBusinessId(Long businessId);
}
