package com.chatbot.saas.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Platform-aware Meta reply service.
 * Facebook Messenger supports quick-reply buttons; Instagram only supports plain text.
 *
 * Sends are blocking on purpose: callers run on the async message-handling thread, and a
 * conversation often sends several messages in a row (product photos, then the menu) which
 * must reach the customer in that order.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MetaReplyService {

    private static final Duration SEND_TIMEOUT = Duration.ofSeconds(10);

    private final WebClient metaWebClient;

    /** Messenger limits: at most 13 quick replies, each title at most 20 characters. */
    private static final int MAX_QUICK_REPLIES = 13;
    private static final int MAX_QUICK_REPLY_TITLE = 20;

    /**
     * Send a plain text message.
     *
     * @return Meta's message id, or null if the message could not be delivered
     */
    public String sendText(String recipientId, String text, String accessToken) {
        log.debug("Sending text to {}", recipientId);
        Map<String, Object> body = Map.of(
                "recipient", Map.of("id", recipientId),
                "message", Map.of("text", text)
        );
        return doPost(body, accessToken, recipientId);
    }

    /**
     * Send an image attachment message (product photo).
     *
     * @return Meta's message id, or null if the message could not be delivered
     */
    public String sendImage(String recipientId, String imageUrl, String accessToken) {
        log.debug("Sending image to {}", recipientId);
        Map<String, Object> body = Map.of(
                "recipient", Map.of("id", recipientId),
                "message", Map.of("attachment", Map.of(
                        "type", "image",
                        "payload", Map.of("url", imageUrl, "is_reusable", true)
                ))
        );
        return doPost(body, accessToken, recipientId);
    }

    /**
     * Send a message with quick-reply buttons (Facebook Messenger only).
     * Each option is a Map with "title" and "payload"; titles are truncated to Messenger's limit.
     *
     * @return Meta's message id, or null if the message could not be delivered
     */
    public String sendWithQuickReplies(String recipientId, String text,
                                       List<Map<String, String>> options, String accessToken) {
        log.debug("Sending quick-replies to {}", recipientId);
        List<Map<String, Object>> quickReplies = new ArrayList<>();
        for (Map<String, String> opt : options) {
            Map<String, Object> qr = new HashMap<>();
            qr.put("content_type", "text");
            qr.put("title", truncate(opt.get("title"), MAX_QUICK_REPLY_TITLE));
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
        return doPost(body, accessToken, recipientId);
    }

    /**
     * Send a numbered menu. The numbered list is always in the text, so full item names and
     * prices are visible on both platforms; on Facebook, quick-reply buttons are added as a
     * shortcut when the menu fits Messenger's limits. The customer answers with the number.
     *
     * @return Meta's message id, or null if the message could not be delivered
     */
    public String sendMenuMessage(String recipientId, String platform, String introText,
                                  List<String> menuItems, String accessToken) {
        String text = renderMenuText(introText, menuItems);
        if ("FACEBOOK".equalsIgnoreCase(platform) && menuItems.size() <= MAX_QUICK_REPLIES) {
            List<Map<String, String>> options = new ArrayList<>();
            for (int i = 0; i < menuItems.size(); i++) {
                String number = String.valueOf(i + 1);
                options.add(Map.of("title", number + ". " + menuItems.get(i), "payload", number));
            }
            return sendWithQuickReplies(recipientId, text, options, accessToken);
        }
        return sendText(recipientId, text, accessToken);
    }

    /** The menu as the customer sees it in text; also what gets recorded in the transcript. */
    public static String renderMenuText(String introText, List<String> menuItems) {
        StringBuilder sb = new StringBuilder(introText).append("\n");
        for (int i = 0; i < menuItems.size(); i++) {
            sb.append(i + 1).append(". ").append(menuItems.get(i)).append("\n");
        }
        return sb.toString().trim();
    }

    private static String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max - 1) + "…";
    }

    private String doPost(Map<String, Object> body, String accessToken, String recipientId) {
        try {
            Map<?, ?> response = metaWebClient.post()
                    .uri("/me/messages")
                    // Header rather than query param so the token never appears in logged URLs
                    .headers(h -> h.setBearerAuth(accessToken))
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(SEND_TIMEOUT);
            log.debug("Message sent to {}", recipientId);
            Object messageId = response != null ? response.get("message_id") : null;
            return messageId != null ? messageId.toString() : null;
        } catch (WebClientResponseException e) {
            // Don't fail the conversation over one undelivered message; the state is already saved
            log.error("Meta rejected message to {}: {} {}", recipientId, e.getStatusCode(), e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("Failed to send message to {}: {}", recipientId, e.getMessage());
        }
        return null;
    }
}
