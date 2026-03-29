package com.chatbot.saas.dto.response;

import com.chatbot.saas.entity.ConversationData;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ConversationDataResponse {
    private Long id;
    private Long conversationId;
    private String fieldName;
    private String fieldValue;
    private LocalDateTime collectedAt;

    public static ConversationDataResponse from(ConversationData data) {
        return ConversationDataResponse.builder()
                .id(data.getId())
                .conversationId(data.getConversation().getId())
                .fieldName(data.getFieldName())
                .fieldValue(data.getFieldValue())
                .collectedAt(data.getCollectedAt())
                .build();
    }
}
