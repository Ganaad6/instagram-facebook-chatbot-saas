package com.chatbot.saas.repository;

import com.chatbot.saas.entity.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    Page<Order> findAllByBusinessId(Long businessId, Pageable pageable);

    Page<Order> findAllByBusinessIdAndStatus(Long businessId, Order.Status status, Pageable pageable);

    Optional<Order> findByIdAndBusinessId(Long id, Long businessId);

    /** Locks the order row so a QPay callback and a reconcile can't both record the payment. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM CustomerOrder o WHERE o.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") Long id);

    @Query("SELECT o.id FROM CustomerOrder o WHERE o.paymentStatus = :status AND o.createdAt > :since ORDER BY o.id")
    List<Long> findIdsByPaymentStatusCreatedAfter(@Param("status") Order.PaymentStatus status,
                                                  @Param("since") LocalDateTime since,
                                                  Pageable pageable);

    List<Order> findAllByBusinessIdAndCreatedAtBetween(Long businessId, LocalDateTime from, LocalDateTime to);

    long countByBusinessId(Long businessId);

    long countByBusinessIdAndStatus(Long businessId, Order.Status status);

    long countByBusinessIdAndCreatedAtBetween(Long businessId, LocalDateTime from, LocalDateTime to);

    @Query("SELECT o.productName, COUNT(o) as cnt, SUM(o.quantity) FROM CustomerOrder o WHERE o.business.id = :businessId GROUP BY o.productName ORDER BY COUNT(o) DESC")
    List<Object[]> findTopProductsByBusiness(@Param("businessId") Long businessId, Pageable pageable);

    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM CustomerOrder o "
            + "WHERE o.business.id = :businessId AND o.status <> :excluded")
    java.math.BigDecimal sumTotalAmountExcludingStatus(@Param("businessId") Long businessId,
                                                       @Param("excluded") Order.Status excluded);

    /** Total value of all non-cancelled orders. */
    default java.math.BigDecimal sumRevenueByBusinessId(Long businessId) {
        return sumTotalAmountExcludingStatus(businessId, Order.Status.CANCELLED);
    }

    @Query(value = "SELECT DATE(created_at) as day, COUNT(*) as cnt FROM orders WHERE business_id = :businessId AND created_at BETWEEN :from AND :to GROUP BY day ORDER BY day",
           nativeQuery = true)
    List<Object[]> countOrdersByDay(@Param("businessId") Long businessId, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
