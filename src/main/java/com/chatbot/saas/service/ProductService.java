package com.chatbot.saas.service;

import com.chatbot.saas.dto.request.CreateProductRequest;
import com.chatbot.saas.dto.request.UpdateProductRequest;
import com.chatbot.saas.dto.response.ProductResponse;
import com.chatbot.saas.entity.Business;
import com.chatbot.saas.entity.Category;
import com.chatbot.saas.entity.Product;
import com.chatbot.saas.exception.ProductNotFoundException;
import com.chatbot.saas.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {

    private final ProductRepository productRepository;
    private final BusinessService businessService;
    private final CategoryService categoryService;

    private final MediaService mediaService;

    @Transactional
    public ProductResponse createProduct(Long businessId, CreateProductRequest request) {
        Business business = businessService.findBusinessById(businessId);
        Category category = categoryService.findCategoryByIdAndBusiness(request.getCategoryId(), businessId);
        mediaService.assertUsable(businessId, request.getImageFileId());
        Product product = Product.builder()
                .business(business)
                .category(category)
                .name(request.getName())
                .price(request.getPrice())
                .description(request.getDescription())
                .isActive(true)
                .imageFileId(request.getImageFileId())
                .build();
        return toResponse(productRepository.save(product));
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> getProductsByBusiness(Long businessId, Long categoryId) {
        List<Product> products = categoryId != null
                ? productRepository.findAllByBusinessIdAndCategoryIdAndIsActiveTrueOrderByNameAsc(businessId, categoryId)
                : productRepository.findAllByBusinessIdOrderByNameAsc(businessId);
        Map<UUID, String> imageUrls = mediaService.imageUrls(products.stream().map(Product::getImageFileId).toList());
        return products.stream()
                .map(p -> ProductResponse.from(p, p.getImageFileId() != null ? imageUrls.get(p.getImageFileId()) : null))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ProductResponse getProductById(Long businessId, Long productId) {
        return toResponse(findProductByIdAndBusiness(productId, businessId));
    }

    @Transactional
    public ProductResponse updateProduct(Long businessId, Long productId, UpdateProductRequest request) {
        Product product = findProductByIdAndBusiness(productId, businessId);
        if (request.getName() != null) product.setName(request.getName());
        if (request.getPrice() != null) product.setPrice(request.getPrice());
        if (request.getDescription() != null) product.setDescription(request.getDescription());
        if (request.getIsActive() != null) product.setIsActive(request.getIsActive());
        if (Boolean.TRUE.equals(request.getRemoveImage())) {
            product.setImageFileId(null);
        } else if (request.getImageFileId() != null && !request.getImageFileId().equals(product.getImageFileId())) {
            mediaService.assertUsable(businessId, request.getImageFileId());
            product.setImageFileId(request.getImageFileId());
        }
        if (request.getCategoryId() != null) {
            Category category = categoryService.findCategoryByIdAndBusiness(request.getCategoryId(), businessId);
            product.setCategory(category);
        }
        return toResponse(productRepository.save(product));
    }

    @Transactional
    public void deleteProduct(Long businessId, Long productId) {
        Product product = findProductByIdAndBusiness(productId, businessId);
        product.setIsActive(false);
        productRepository.save(product);
    }

    public List<Product> getActiveProductsByCategory(Long businessId, Long categoryId) {
        return productRepository.findAllByBusinessIdAndCategoryIdAndIsActiveTrueOrderByNameAsc(businessId, categoryId);
    }

    private ProductResponse toResponse(Product product) {
        return ProductResponse.from(product, mediaService.imageUrl(product.getImageFileId()));
    }

    /** The product if a customer can order it now: this shop's, active, in an active category. */
    @Transactional(readOnly = true)
    public Optional<Product> findOrderable(Long businessId, Long productId) {
        return productRepository.findByIdAndBusinessId(productId, businessId)
                .filter(p -> Boolean.TRUE.equals(p.getIsActive()))
                .filter(p -> Boolean.TRUE.equals(p.getCategory().getIsActive()));
    }

    public Product findProductByIdAndBusiness(Long productId, Long businessId) {
        return productRepository.findByIdAndBusinessId(productId, businessId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
    }
}
