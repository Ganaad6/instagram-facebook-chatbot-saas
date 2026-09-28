package com.chatbot.saas.dto.response;

import com.chatbot.saas.entity.Business;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class BusinessResponse {
    private Long id;
    private String name;
    private String email;
    private String instagramAccountId;
    private String facebookPageId;
    private Business.Status status;
    /** True once the owner completed the Meta connect flow and a Page token is stored. */
    private boolean metaConnected;
    private LocalDateTime createdAt;

    public static BusinessResponse from(Business business) {
        return BusinessResponse.builder()
                .id(business.getId())
                .name(business.getName())
                .email(business.getEmail())
                .instagramAccountId(business.getInstagramAccountId())
                .facebookPageId(business.getFacebookPageId())
                .status(business.getStatus())
                .metaConnected(business.getAccessToken() != null)
                .createdAt(business.getCreatedAt())
                .build();
    }
}
