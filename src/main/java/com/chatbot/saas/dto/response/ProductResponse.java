package com.chatbot.saas.dto.response;

import com.chatbot.saas.entity.Product;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class ProductResponse {
    private Long id;
    private Long businessId;
    private Long categoryId;
    private String categoryName;
    private String name;
    private BigDecimal price;
    private String description;
    private Boolean isActive;
    private String imageUrl;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ProductResponse from(Product product, String directusPublicUrl) {
        return ProductResponse.builder()
                .id(product.getId())
                .businessId(product.getBusiness().getId())
                .categoryId(product.getCategory().getId())
                .categoryName(product.getCategory().getName())
                .name(product.getName())
                .price(product.getPrice())
                .description(product.getDescription())
                .isActive(product.getIsActive())
                .imageUrl(resolveImageUrl(product.getImageFileId(), directusPublicUrl))
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .build();
    }

    public static String resolveImageUrl(java.util.UUID imageFileId, String directusPublicUrl) {
        if (imageFileId == null || directusPublicUrl == null || directusPublicUrl.isBlank()) {
            return null;
        }
        String base = directusPublicUrl.endsWith("/")
                ? directusPublicUrl.substring(0, directusPublicUrl.length() - 1)
                : directusPublicUrl;
        return base + "/assets/" + imageFileId;
    }
}
