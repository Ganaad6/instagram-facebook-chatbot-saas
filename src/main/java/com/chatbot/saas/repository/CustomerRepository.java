package com.chatbot.saas.repository;

import com.chatbot.saas.entity.Customer;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {
    Optional<Customer> findByBusinessIdAndInstagramUserId(Long businessId, String instagramUserId);
    Optional<Customer> findByBusinessIdAndFacebookUserId(Long businessId, String facebookUserId);
    List<Customer> findAllByBusinessId(Long businessId);

    /**
     * Row-locks the customer until the surrounding transaction ends. Used to process one
     * customer's messages strictly one at a time (see MessageHandlerService).
     */
    /** Waiting for a person first (oldest request first), then other paused customers. */
    @Query("select c from Customer c where c.business.id = :businessId "
            + "and (c.handoffRequestedAt is not null or c.botPausedUntil > :now) "
            + "order by case when c.handoffRequestedAt is null then 1 else 0 end, "
            + "c.handoffRequestedAt asc, c.lastInteractionAt desc")
    List<Customer> findInbox(@Param("businessId") Long businessId, @Param("now") java.time.LocalDateTime now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Customer c where c.id = :id")
    Optional<Customer> findByIdForUpdate(@Param("id") Long id);
}
