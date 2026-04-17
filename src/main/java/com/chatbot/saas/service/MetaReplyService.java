package com.chatbot.saas.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Platform-aware Meta reply service.
 * Facebook Messenger supports quick-reply buttons; Instagram only supports plain text.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MetaReplyService {

    private final WebClient metaWebClient;

    /**
     * Send a plain text message.
     */
    public void sendText(String recipientId, String text, String accessToken) {
        log.debug("Sending text to {}", recipientId);
        Map<String, Object> body = Map.of(
                "recipient", Map.of("id", recipientId),
                "message", Map.of("text", text)
        );
        doPost(body, accessToken, recipientId);
    }

    /**
     * Send a message with quick-reply buttons (Facebook Messenger only).
     * Each option is a Map with "title" and "payload".
     */
    public void sendWithQuickReplies(String recipientId, String text,
                                     List<Map<String, String>> options, String accessToken) {
        log.debug("Sending quick-replies to {}", recipientId);
        List<Map<String, Object>> quickReplies = new ArrayList<>();
        for (Map<String, String> opt : options) {
            Map<String, Object> qr = new HashMap<>();
            qr.put("content_type", "text");
            qr.put("title", opt.get("title"));
            qr.put("payload", opt.get("payload"));
            quickReplies.add(qr);
        }
        Map<String, Object> message = new HashMap<>();
        message.put("text", text);
        message.put("quick_replies", quickReplies);

        Map<String, Object> body = Map.of(
                "recipient", Map.of("id", recipientId),
                "message", message
        );
        doPost(body, accessToken, recipientId);
    }

    /**
     * Send message appropriate for the platform.
     * Instagram → plain numbered text. Facebook → quick-reply buttons (if options provided).
     */
    public void sendMenuMessage(String recipientId, String platform, String introText,
                                List<String> menuItems, String accessToken) {
        if ("FACEBOOK".equalsIgnoreCase(platform)) {
            List<Map<String, String>> options = new ArrayList<>();
            for (int i = 0; i < menuItems.size(); i++) {
                options.add(Map.of("title", menuItems.get(i), "payload", String.valueOf(i + 1)));
            }
            sendWithQuickReplies(recipientId, introText, options, accessToken);
        } else {
            // Instagram (and fallback): numbered text list
            StringBuilder sb = new StringBuilder(introText).append("\n");
            for (int i = 0; i < menuItems.size(); i++) {
                sb.append(i + 1).append(". ").append(menuItems.get(i)).append("\n");
            }
            sendText(recipientId, sb.toString().trim(), accessToken);
        }
    }

    private void doPost(Map<String, Object> body, String accessToken, String recipientId) {
        metaWebClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/me/messages")
                        .queryParam("access_token", accessToken)
                        .build())
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .doOnSuccess(resp -> log.debug("Message sent to {}", recipientId))
                .doOnError(err -> log.error("Failed to send message to {}: {}", recipientId, err.getMessage()))
                .subscribe();
    }
}
