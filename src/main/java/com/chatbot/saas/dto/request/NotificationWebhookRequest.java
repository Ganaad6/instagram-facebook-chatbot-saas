package com.chatbot.saas.dto.request;

import lombok.Data;

@Data
public class NotificationWebhookRequest {
    private String webhookUrl;
}
