package com.chatbot.saas.repository;

import com.chatbot.saas.entity.Message;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {
    List<Message> findAllByConversationIdOrderByIdAsc(Long conversationId);
    List<Message> findAllByBusinessId(Long businessId);
    boolean existsByBusinessIdAndMessageId(Long businessId, String messageId);
    List<Message> findAllByCustomerIdOrderByIdDesc(Long customerId, Pageable pageable);
    Optional<Message> findFirstByCustomerIdOrderByIdDesc(Long customerId);
    Optional<Message> findFirstByCustomerIdAndDirectionOrderByIdDesc(Long customerId, Message.Direction direction);

    @Modifying
    @Query("delete from Message m where m.customer.id = :customerId")
    int deleteAllByCustomerId(@Param("customerId") Long customerId);

    /** Retention: chat history older than the cutoff. */
    @Modifying
    @Query("delete from Message m where m.createdAt < :cutoff")
    int deleteAllCreatedBefore(@Param("cutoff") java.time.LocalDateTime cutoff);
}
