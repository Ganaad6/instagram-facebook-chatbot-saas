package com.chatbot.saas.service;

import com.chatbot.saas.dto.request.CreateCategoryRequest;
import com.chatbot.saas.dto.request.UpdateCategoryRequest;
import com.chatbot.saas.dto.response.CategoryResponse;
import com.chatbot.saas.entity.Business;
import com.chatbot.saas.entity.Category;
import com.chatbot.saas.exception.CategoryNotFoundException;
import com.chatbot.saas.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final BusinessService businessService;

    @Transactional
    public CategoryResponse createCategory(Long businessId, CreateCategoryRequest request) {
        Business business = businessService.findBusinessById(businessId);
        Category category = Category.builder()
                .business(business)
                .name(request.getName())
                .sortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0)
                .isActive(true)
                .build();
        return CategoryResponse.from(categoryRepository.save(category));
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> getCategoriesByBusiness(Long businessId) {
        return categoryRepository.findAllByBusinessIdOrderBySortOrderAsc(businessId)
                .stream()
                .map(CategoryResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public CategoryResponse updateCategory(Long businessId, Long categoryId, UpdateCategoryRequest request) {
        Category category = findCategoryByIdAndBusiness(categoryId, businessId);
        if (request.getName() != null) category.setName(request.getName());
        if (request.getSortOrder() != null) category.setSortOrder(request.getSortOrder());
        if (request.getIsActive() != null) category.setIsActive(request.getIsActive());
        return CategoryResponse.from(categoryRepository.save(category));
    }

    @Transactional
    public void deleteCategory(Long businessId, Long categoryId) {
        Category category = findCategoryByIdAndBusiness(categoryId, businessId);
        category.setIsActive(false);
        categoryRepository.save(category);
    }

    public List<Category> getActiveCategories(Long businessId) {
        return categoryRepository.findAllByBusinessIdAndIsActiveTrueOrderBySortOrderAsc(businessId);
    }

    public Category findCategoryByIdAndBusiness(Long categoryId, Long businessId) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new CategoryNotFoundException(categoryId));
        if (!category.getBusiness().getId().equals(businessId)) {
            throw new CategoryNotFoundException(categoryId);
        }
        return category;
    }
}
