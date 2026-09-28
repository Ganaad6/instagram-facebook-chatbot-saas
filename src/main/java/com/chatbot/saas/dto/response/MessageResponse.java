package com.chatbot.saas.dto.response;

import com.chatbot.saas.entity.Message;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class MessageResponse {
    private Long id;
    /** INBOUND = from the customer, OUTBOUND = sent by the bot. */
    private Message.Direction direction;
    /** CUSTOMER, BOT, or AGENT (a staff member). */
    private Message.SenderType senderType;
    private String content;
    private LocalDateTime sentAt;

    public static MessageResponse from(Message message) {
        return MessageResponse.builder()
                .id(message.getId())
                .direction(message.getDirection())
                .senderType(message.getSenderType())
                .content(message.getContent())
                .sentAt(message.getSentAt())
                .build();
    }
}
