package com.chatbot.saas.controller;

import com.chatbot.saas.dto.request.CreateCategoryRequest;
import com.chatbot.saas.dto.request.UpdateCategoryRequest;
import com.chatbot.saas.dto.response.CategoryResponse;
import com.chatbot.saas.service.CategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/businesses/{businessId}/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @PostMapping
    public ResponseEntity<CategoryResponse> createCategory(
            @PathVariable Long businessId,
            @Valid @RequestBody CreateCategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(categoryService.createCategory(businessId, request));
    }

    @GetMapping
    public ResponseEntity<List<CategoryResponse>> getCategories(@PathVariable Long businessId) {
        return ResponseEntity.ok(categoryService.getCategoriesByBusiness(businessId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CategoryResponse> updateCategory(
            @PathVariable Long businessId,
            @PathVariable Long id,
            @RequestBody UpdateCategoryRequest request) {
        return ResponseEntity.ok(categoryService.updateCategory(businessId, id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCategory(
            @PathVariable Long businessId,
            @PathVariable Long id) {
        categoryService.deleteCategory(businessId, id);
        return ResponseEntity.noContent().build();
    }
}
