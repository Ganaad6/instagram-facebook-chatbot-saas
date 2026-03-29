package com.chatbot.saas.service;

import com.chatbot.saas.util.SignatureValidator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class WebhookService {

    private final MessageHandlerService messageHandlerService;
    private final ObjectMapper objectMapper;

    @Value("${webhook.verify-token}")
    private String verifyToken;

    @Value("${meta.app.secret}")
    private String appSecret;

    public WebhookService(MessageHandlerService messageHandlerService, ObjectMapper objectMapper) {
        this.messageHandlerService = messageHandlerService;
        this.objectMapper = objectMapper;
    }

    public String verifyWebhook(String mode, String token, String challenge) {
        if ("subscribe".equals(mode) && verifyToken.equals(token)) {
            log.info("Webhook verified successfully");
            return challenge;
        }
        throw new RuntimeException("Webhook verification failed");
    }

    public void processWebhookEvent(String payload, String signature) {
        if (!verifySignature(payload, signature)) {
            log.warn("Invalid webhook signature");
            throw new RuntimeException("Invalid webhook signature");
        }

        try {
            JsonNode root = objectMapper.readTree(payload);
            log.debug("Processing webhook for object type: {}", root.path("object").asText());

            JsonNode entries = root.path("entry");
            for (JsonNode entry : entries) {
                JsonNode messagingArray = entry.path("messaging");

                for (JsonNode messagingEvent : messagingArray) {
                    JsonNode sender = messagingEvent.path("sender");
                    JsonNode recipient = messagingEvent.path("recipient");
                    JsonNode message = messagingEvent.path("message");

                    String senderId = sender.path("id").asText();
                    String recipientId = recipient.path("id").asText();
                    String messageText = message.path("text").asText();

                    if (!senderId.isEmpty() && !messageText.isEmpty()) {
                        log.debug("Routing message from {} to {}", senderId, recipientId);
                        messageHandlerService.handleIncomingMessage(senderId, messageText, recipientId);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error processing webhook payload: {}", e.getMessage(), e);
            throw new RuntimeException("Error processing webhook", e);
        }
    }

    public boolean verifySignature(String payload, String signature) {
        if (signature == null || signature.isEmpty()) {
            return false;
        }
        return SignatureValidator.validateSignature(payload, signature, appSecret);
    }
}
