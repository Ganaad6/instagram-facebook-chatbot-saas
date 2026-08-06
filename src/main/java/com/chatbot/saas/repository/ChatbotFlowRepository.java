package com.chatbot.saas.repository;

import com.chatbot.saas.entity.ChatbotFlow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatbotFlowRepository extends JpaRepository<ChatbotFlow, Long> {
    Optional<ChatbotFlow> findByBusinessIdAndIsActiveTrue(Long businessId);
    List<ChatbotFlow> findAllByBusinessId(Long businessId);

    @Modifying
    @Query("UPDATE ChatbotFlow f SET f.isActive = false WHERE f.business.id = :businessId AND f.id <> :excludeFlowId")
    void deactivateAllExcept(@Param("businessId") Long businessId, @Param("excludeFlowId") Long excludeFlowId);
}
