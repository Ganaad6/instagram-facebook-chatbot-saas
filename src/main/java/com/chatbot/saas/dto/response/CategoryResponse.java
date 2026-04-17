package com.chatbot.saas.dto.response;

import com.chatbot.saas.entity.Category;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class CategoryResponse {
    private Long id;
    private Long businessId;
    private String name;
    private Integer sortOrder;
    private Boolean isActive;
    private LocalDateTime createdAt;

    public static CategoryResponse from(Category category) {
        return CategoryResponse.builder()
                .id(category.getId())
                .businessId(category.getBusiness().getId())
                .name(category.getName())
                .sortOrder(category.getSortOrder())
                .isActive(category.getIsActive())
                .createdAt(category.getCreatedAt())
                .build();
    }
}
