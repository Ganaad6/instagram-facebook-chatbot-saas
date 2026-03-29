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
    private Long flowId;
    private Long currentStepId;
    private String status;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;

    public static ConversationResponse from(Conversation conversation) {
        return ConversationResponse.builder()
                .id(conversation.getId())
                .customerId(conversation.getCustomer().getId())
                .businessId(conversation.getBusiness().getId())
                .flowId(conversation.getFlow().getId())
                .currentStepId(conversation.getCurrentStep() != null ? conversation.getCurrentStep().getId() : null)
                .status(conversation.getStatus().name())
                .startedAt(conversation.getStartedAt())
                .completedAt(conversation.getCompletedAt())
                .build();
    }
}
