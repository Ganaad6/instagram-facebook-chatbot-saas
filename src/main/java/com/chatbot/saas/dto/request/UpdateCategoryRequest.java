package com.chatbot.saas.dto.request;

import lombok.Data;

@Data
public class UpdateCategoryRequest {
    private String name;
    private Integer sortOrder;
    private Boolean isActive;
}
