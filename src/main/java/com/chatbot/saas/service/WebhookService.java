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
            String objectType = root.path("object").asText();
            log.debug("Processing webhook for object type: {}", objectType);

            // Determine platform from object type
            String platform = "page".equalsIgnoreCase(objectType) ? "FACEBOOK" : "INSTAGRAM";

            JsonNode entries = root.path("entry");
            for (JsonNode entry : entries) {
                // Facebook Messenger uses "messaging"; Instagram uses "messaging" too
                JsonNode messagingArray = entry.path("messaging");
                for (JsonNode messagingEvent : messagingArray) {
                    processMessagingEvent(messagingEvent, platform);
                }
            }
        } catch (Exception e) {
            log.error("Error processing webhook payload: {}", e.getMessage(), e);
            throw new RuntimeException("Error processing webhook", e);
        }
    }

    private void processMessagingEvent(JsonNode messagingEvent, String platform) {
        JsonNode sender = messagingEvent.path("sender");
        JsonNode recipient = messagingEvent.path("recipient");
        String senderId = sender.path("id").asText();
        String recipientId = recipient.path("id").asText();

        if (senderId.isEmpty() || recipientId.isEmpty()) {
            return;
        }

        // Handle text messages
        if (messagingEvent.has("message")) {
            JsonNode message = messagingEvent.path("message");
            // Skip echo messages (messages sent by the page itself)
            if (message.path("is_echo").asBoolean(false)) {
                return;
            }
            String messageText = message.path("text").asText();
            if (!messageText.isEmpty()) {
                log.debug("Text message from {} to {} [{}]", senderId, recipientId, platform);
                messageHandlerService.handleIncomingMessage(senderId, messageText, recipientId, platform);
            }
        }

        // Handle postback (Facebook quick-reply button clicks)
        if (messagingEvent.has("postback")) {
            JsonNode postback = messagingEvent.path("postback");
            String payload = postback.path("payload").asText();
            if (!payload.isEmpty()) {
                log.debug("Postback from {} payload={}", senderId, payload);
                messageHandlerService.handlePostback(senderId, payload, recipientId);
            }
        }
    }

    public boolean verifySignature(String payload, String signature) {
        if (signature == null || signature.isEmpty()) {
            return false;
        }
        return SignatureValidator.validateSignature(payload, signature, appSecret);
    }
}
