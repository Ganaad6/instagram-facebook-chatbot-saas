package com.chatbot.saas.repository;

import com.chatbot.saas.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {
    List<Category> findAllByBusinessIdAndIsActiveTrueOrderBySortOrderAsc(Long businessId);
    List<Category> findAllByBusinessIdOrderBySortOrderAsc(Long businessId);
}
