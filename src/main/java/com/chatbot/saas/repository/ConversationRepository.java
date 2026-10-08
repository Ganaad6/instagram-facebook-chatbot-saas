package com.chatbot.saas.repository;

import com.chatbot.saas.entity.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, Long> {
    Optional<Conversation> findFirstByCustomerIdAndStatusOrderByUpdatedAtDesc(Long customerId, Conversation.Status status);
    List<Conversation> findAllByBusinessId(Long businessId);
    Optional<Conversation> findFirstByOrderId(Long orderId);

    @Modifying
    @Query("delete from Conversation c where c.customer.id = :customerId")
    int deleteAllByCustomerId(@Param("customerId") Long customerId);

    /** Retention: contact details typed into old conversations (orders keep their own copy). */
    @Modifying
    @Query("update Conversation c set c.collectedName = null, c.collectedPhone = null, c.collectedAddress = null "
            + "where c.updatedAt < :cutoff and (c.collectedName is not null or c.collectedPhone is not null "
            + "or c.collectedAddress is not null)")
    int clearContactDetailsUpdatedBefore(@Param("cutoff") java.time.LocalDateTime cutoff);
}
