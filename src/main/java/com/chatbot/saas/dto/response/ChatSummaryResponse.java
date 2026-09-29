package com.chatbot.saas.dto.response;

import com.chatbot.saas.entity.Message;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/** One customer's conversation in the dashboard's chat list. */
@Data
@Builder
public class ChatSummaryResponse {
    private Long customerId;
    /** The name the customer gave on their latest order; null if they never ordered. */
    private String displayName;
    /** FACEBOOK or INSTAGRAM (FACEBOOK if the customer wrote on both). */
    private String platform;
    private String lastMessage;
    private Message.SenderType lastMessageSender;
    private LocalDateTime lastMessageAt;
    private LocalDateTime lastInteractionAt;
    private LocalDateTime botPausedUntil;
    /** Set while the customer is waiting for a person. */
    private LocalDateTime handoffRequestedAt;
}
