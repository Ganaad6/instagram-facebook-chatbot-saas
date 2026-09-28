package com.chatbot.saas.dto.response;

import com.chatbot.saas.entity.Conversation;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ConversationResponse {
    private Long id;
    private Long customerId;
    private Long businessId;
    private String status;
    private String state;
    private String platform;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;

    public static ConversationResponse from(Conversation conversation) {
        return ConversationResponse.builder()
                .id(conversation.getId())
                .customerId(conversation.getCustomer().getId())
                .businessId(conversation.getBusiness().getId())
                .status(conversation.getStatus().name())
                .state(conversation.getState() != null ? conversation.getState().name() : null)
                .platform(conversation.getPlatform())
                .startedAt(conversation.getStartedAt())
                .completedAt(conversation.getCompletedAt())
                .build();
    }
}
