package com.chatbot.saas.dto.response;

import com.chatbot.saas.entity.Customer;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class CustomerResponse {
    private Long id;
    private Long businessId;
    private String instagramUserId;
    private String facebookUserId;
    private LocalDateTime firstInteractionAt;
    private LocalDateTime lastInteractionAt;
    private LocalDateTime botPausedUntil;
    private LocalDateTime handoffRequestedAt;

    public static CustomerResponse from(Customer customer) {
        return CustomerResponse.builder()
                .id(customer.getId())
                .businessId(customer.getBusiness().getId())
                .instagramUserId(customer.getInstagramUserId())
                .facebookUserId(customer.getFacebookUserId())
                .firstInteractionAt(customer.getFirstInteractionAt())
                .lastInteractionAt(customer.getLastInteractionAt())
                .botPausedUntil(customer.getBotPausedUntil())
                .handoffRequestedAt(customer.getHandoffRequestedAt())
                .build();
    }
}
