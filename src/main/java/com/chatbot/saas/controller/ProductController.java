package com.chatbot.saas.controller;

import com.chatbot.saas.dto.request.CreateProductRequest;
import com.chatbot.saas.dto.request.UpdateProductRequest;
import com.chatbot.saas.dto.response.ProductResponse;
import com.chatbot.saas.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/businesses/{businessId}/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @PostMapping
    public ResponseEntity<ProductResponse> createProduct(
            @PathVariable Long businessId,
            @Valid @RequestBody CreateProductRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(productService.createProduct(businessId, request));
    }

    @GetMapping
    public ResponseEntity<List<ProductResponse>> getProducts(
            @PathVariable Long businessId,
            @RequestParam(required = false) Long categoryId) {
        return ResponseEntity.ok(productService.getProductsByBusiness(businessId, categoryId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> getProduct(
            @PathVariable Long businessId,
            @PathVariable Long id) {
        return ResponseEntity.ok(productService.getProductById(businessId, id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProductResponse> updateProduct(
            @PathVariable Long businessId,
            @PathVariable Long id,
            @RequestBody UpdateProductRequest request) {
        return ResponseEntity.ok(productService.updateProduct(businessId, id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(
            @PathVariable Long businessId,
            @PathVariable Long id) {
        productService.deleteProduct(businessId, id);
        return ResponseEntity.noContent().build();
    }
}
