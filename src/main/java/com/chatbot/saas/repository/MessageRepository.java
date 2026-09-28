package com.chatbot.saas.repository;

import com.chatbot.saas.entity.Message;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {
    List<Message> findAllByConversationIdOrderByIdAsc(Long conversationId);
    List<Message> findAllByBusinessId(Long businessId);
    boolean existsByBusinessIdAndMessageId(Long businessId, String messageId);
    List<Message> findAllByCustomerIdOrderByIdDesc(Long customerId, Pageable pageable);
    Optional<Message> findFirstByCustomerIdAndDirectionOrderByIdDesc(Long customerId, Message.Direction direction);
}
