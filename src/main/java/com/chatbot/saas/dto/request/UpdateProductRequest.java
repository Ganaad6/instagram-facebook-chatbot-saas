package com.chatbot.saas.dto.request;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class UpdateProductRequest {
    private String name;
    private Long categoryId;
    private BigDecimal price;
    private String description;
    private Boolean isActive;
}
