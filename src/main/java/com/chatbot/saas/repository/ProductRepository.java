package com.chatbot.saas.repository;

import com.chatbot.saas.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    List<Product> findAllByBusinessIdAndIsActiveTrueOrderByNameAsc(Long businessId);
    List<Product> findAllByBusinessIdAndCategoryIdAndIsActiveTrueOrderByNameAsc(Long businessId, Long categoryId);
    List<Product> findAllByBusinessIdOrderByNameAsc(Long businessId);
    Optional<Product> findByIdAndBusinessId(Long id, Long businessId);
}
