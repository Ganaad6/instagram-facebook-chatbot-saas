package com.chatbot.saas.dto.response;

import com.chatbot.saas.entity.Order;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class OrderResponse {
    private Long id;
    private Long businessId;
    private Long customerId;
    private Long productId;
    private String productName;
    private BigDecimal productPrice;
    private String customerName;
    private String phone;
    private String address;
    private String status;
    private String platform;
    private String notes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static OrderResponse from(Order order) {
        return OrderResponse.builder()
                .id(order.getId())
                .businessId(order.getBusiness().getId())
                .customerId(order.getCustomer().getId())
                .productId(order.getProduct().getId())
                .productName(order.getProduct().getName())
                .productPrice(order.getProduct().getPrice())
                .customerName(order.getCustomerName())
                .phone(order.getPhone())
                .address(order.getAddress())
                .status(order.getStatus().name())
                .platform(order.getPlatform().name())
                .notes(order.getNotes())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }
}
