package com.chatbot.saas.service;

import com.chatbot.saas.exception.WebhookAuthenticationException;
import com.chatbot.saas.service.MessageHandlerService.EchoMessage;
import com.chatbot.saas.service.MessageHandlerService.InboundMessage;
import com.chatbot.saas.util.SignatureValidator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
        throw new WebhookAuthenticationException("Webhook verification failed");
    }

    /**
     * Verifies and dispatches a webhook delivery. Only a bad signature is reported as an error;
     * anything else is logged and swallowed, because a non-2xx response makes Meta retry the
     * same delivery repeatedly (and eventually disable the webhook).
     */
    public void processWebhookEvent(String payload, String signature) {
        if (!verifySignature(payload, signature)) {
            log.warn("Invalid webhook signature");
            throw new WebhookAuthenticationException("Invalid webhook signature");
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(payload);
        } catch (Exception e) {
            // Don't log the parser message: it can quote the payload, i.e. customer messages
            log.error("Unparseable webhook payload ({} chars)", payload.length());
            return;
        }

        String objectType = root.path("object").asText();
        log.debug("Processing webhook for object type: {}", objectType);
        // "page" = Facebook Messenger, "instagram" = Instagram messaging
        String platform = "page".equalsIgnoreCase(objectType) ? "FACEBOOK" : "INSTAGRAM";

        for (JsonNode entry : root.path("entry")) {
            for (JsonNode messagingEvent : entry.path("messaging")) {
                try {
                    processMessagingEvent(messagingEvent, platform);
                } catch (Exception e) {
                    log.error("Error dispatching messaging event: {}", e.getMessage(), e);
                }
            }
        }
    }

    private void processMessagingEvent(JsonNode messagingEvent, String platform) {
        String senderId = messagingEvent.path("sender").path("id").asText();
        String recipientId = messagingEvent.path("recipient").path("id").asText();
        if (senderId.isEmpty() || recipientId.isEmpty()) {
            return;
        }

        if (messagingEvent.has("message")) {
            JsonNode message = messagingEvent.path("message");
            if (message.path("is_deleted").asBoolean(false)) {
                return; // the customer unsent a message
            }
            if (message.path("is_echo").asBoolean(false)) {
                // A message the shop's account sent: the sender is the shop, the recipient the
                // customer. Either this app's own reply or staff typing in the Meta inbox.
                messageHandlerService.handleEcho(new EchoMessage(platform, senderId, recipientId,
                        message.path("mid").asText(null), message.path("text").asText(""),
                        message.path("app_id").asText(null)));
                return;
            }
            // Tapping a quick reply sends its title as text; the payload holds the menu number
            String text = message.path("quick_reply").path("payload").asText("");
            if (text.isEmpty()) {
                text = message.path("text").asText("");
            }
            boolean hasAttachments = message.path("attachments").size() > 0;
            if (text.isEmpty() && !hasAttachments) {
                return;
            }
            dispatch(platform, senderId, recipientId, message.path("mid").asText(null), text);
        } else if (messagingEvent.has("postback")) {
            JsonNode postback = messagingEvent.path("postback");
            String payload = postback.path("payload").asText("");
            if (!payload.isEmpty()) {
                dispatch(platform, senderId, recipientId, postback.path("mid").asText(null), payload);
            }
        }
    }

    private void dispatch(String platform, String senderId, String recipientId, String mid, String text) {
        log.debug("Message from {} to {} [{}] mid={}", senderId, recipientId, platform, mid);
        messageHandlerService.handleIncomingMessage(new InboundMessage(platform, senderId, recipientId, mid, text));
    }

    public boolean verifySignature(String payload, String signature) {
        if (signature == null || signature.isEmpty()) {
            return false;
        }
        return SignatureValidator.validateSignature(payload, signature, appSecret);
    }
}
