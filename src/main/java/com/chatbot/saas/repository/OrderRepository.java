package com.chatbot.saas.repository;

import com.chatbot.saas.entity.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    Page<Order> findAllByBusinessId(Long businessId, Pageable pageable);

    Page<Order> findAllByBusinessIdAndStatus(Long businessId, Order.Status status, Pageable pageable);

    Page<Order> findAllByBusinessIdAndCustomerId(Long businessId, Long customerId, Pageable pageable);

    Page<Order> findAllByBusinessIdAndCustomerIdAndStatus(Long businessId, Long customerId, Order.Status status, Pageable pageable);

    Optional<Order> findByIdAndBusinessId(Long id, Long businessId);

    Optional<Order> findFirstByCustomerIdAndCustomerNameIsNotNullAndPhoneIsNotNullAndAddressIsNotNullOrderByCreatedAtDesc(Long customerId);

    Optional<Order> findFirstByCustomerIdAndStatusInAndCreatedAtAfterOrderByCreatedAtDesc(
            Long customerId, Collection<Order.Status> statuses, LocalDateTime after);

    Optional<Order> findFirstByCustomerIdAndCustomerNameIsNotNullOrderByIdDesc(Long customerId);

    long countByBusinessIdAndPaymentStatusAndStatusNot(Long businessId, Order.PaymentStatus paymentStatus, Order.Status status);

    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM CustomerOrder o "
            + "WHERE o.business.id = :businessId AND o.paymentStatus = :paymentStatus")
    java.math.BigDecimal sumTotalAmountByPaymentStatus(@Param("businessId") Long businessId,
                                                       @Param("paymentStatus") Order.PaymentStatus paymentStatus);

    /** Total value of orders paid through QPay. */
    default java.math.BigDecimal sumPaidByBusinessId(Long businessId) {
        return sumTotalAmountByPaymentStatus(businessId, Order.PaymentStatus.PAID);
    }

    /** Locks the order row so a QPay callback and a reconcile can't both record the payment. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM CustomerOrder o WHERE o.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") Long id);

    /** Least recently checked first, so every invoice in the window gets its turn. */
    @Query("SELECT o.id FROM CustomerOrder o WHERE o.paymentStatus = :status AND o.createdAt > :since "
            + "ORDER BY o.paymentCheckedAt ASC NULLS FIRST, o.id ASC")
    List<Long> findIdsByPaymentStatusCreatedAfter(@Param("status") Order.PaymentStatus status,
                                                  @Param("since") LocalDateTime since,
                                                  Pageable pageable);

    @Query("SELECT o.id FROM CustomerOrder o WHERE o.business.id = :businessId AND o.paymentStatus = :status")
    List<Long> findIdsByBusinessIdAndPaymentStatus(@Param("businessId") Long businessId,
                                                   @Param("status") Order.PaymentStatus status);

    @org.springframework.data.jpa.repository.Modifying
    @Query("UPDATE CustomerOrder o SET o.paymentCheckedAt = :at WHERE o.id = :id")
    void markPaymentChecked(@Param("id") Long id, @Param("at") LocalDateTime at);

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

    /** Keeps the order (the shop's sales record) but drops who it was for. */
    @Modifying
    @Query("update CustomerOrder o set o.customerName = null, o.phone = null, o.address = null, o.notes = null "
            + "where o.customer.id = :customerId")
    int anonymizeAllByCustomerId(@Param("customerId") Long customerId);
}
