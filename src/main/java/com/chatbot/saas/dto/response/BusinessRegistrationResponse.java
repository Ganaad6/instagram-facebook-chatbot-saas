package com.chatbot.saas.dto.response;

import com.chatbot.saas.entity.Business;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Returned only at registration/rotation time - the only moment the raw API key is available.
 */
@Data
@Builder
public class BusinessRegistrationResponse {
    private Long id;
    private String name;
    private String email;
    private String instagramAccountId;
    private String facebookPageId;
    private Business.Status status;
    private LocalDateTime createdAt;
    private String apiKey;

    public static BusinessRegistrationResponse from(Business business, String apiKey) {
        return BusinessRegistrationResponse.builder()
                .id(business.getId())
                .name(business.getName())
                .email(business.getEmail())
                .instagramAccountId(business.getInstagramAccountId())
                .facebookPageId(business.getFacebookPageId())
                .status(business.getStatus())
                .createdAt(business.getCreatedAt())
                .apiKey(apiKey)
                .build();
    }
}
