package com.chatbot.saas.repository;

import com.chatbot.saas.entity.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
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

    List<Order> findAllByBusinessIdAndCreatedAtBetween(Long businessId, LocalDateTime from, LocalDateTime to);

    long countByBusinessId(Long businessId);

    long countByBusinessIdAndStatus(Long businessId, Order.Status status);

    long countByBusinessIdAndCreatedAtBetween(Long businessId, LocalDateTime from, LocalDateTime to);

    @Query("SELECT o.product.name, COUNT(o) as cnt FROM CustomerOrder o WHERE o.business.id = :businessId GROUP BY o.product.name ORDER BY COUNT(o) DESC")
    List<Object[]> findTopProductsByBusiness(@Param("businessId") Long businessId, Pageable pageable);

    @Query(value = "SELECT DATE(created_at) as day, COUNT(*) as cnt FROM orders WHERE business_id = :businessId AND created_at BETWEEN :from AND :to GROUP BY day ORDER BY day",
           nativeQuery = true)
    List<Object[]> countOrdersByDay(@Param("businessId") Long businessId, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
