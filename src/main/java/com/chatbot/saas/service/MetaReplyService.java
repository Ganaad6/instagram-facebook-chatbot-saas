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
 * Sends the bot's and staff's messages through the Send API (Messenger and Instagram alike).
 *
 * Quick-reply buttons and product cards work on both platforms, but Instagram shows quick
 * replies only in its phone app, so every message with buttons also carries its choices as
 * text. If Meta rejects a message with buttons, it is sent again as plain text so the customer
 * still gets it.
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

    /** Send API limits (both platforms): 13 quick replies with 20-character titles. */
    static final int MAX_QUICK_REPLIES = 13;
    private static final int MAX_QUICK_REPLY_TITLE = 20;
    /** Generic template limits: 10 cards per message, 80-character title and subtitle. */
    static final int MAX_CARDS = 10;
    private static final int MAX_CARD_TEXT = 80;
    private static final int MAX_BUTTON_TITLE = 20;

    /** A product card: tapping its button sends {@code payload} back as a postback. */
    public record Card(String title, String subtitle, String imageUrl, String buttonTitle, String payload) {
    }

    /** What happened to one send: Meta's message id, or whether Meta refused it. */
    private record Sent(String messageId, boolean rejected) {
    }

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
     * Send a message written by a staff member. Inside Meta's 24-hour window after the
     * customer's last message this is an ordinary reply; after that (up to 7 days) Meta only
     * accepts it with the HUMAN_AGENT tag, which requires the Human Agent permission from App
     * Review.
     *
     * @return Meta's message id, or null if the message could not be delivered
     */
    public String sendAgentText(String recipientId, String text, boolean humanAgentTag, String accessToken) {
        log.debug("Sending agent text to {} (humanAgentTag={})", recipientId, humanAgentTag);
        Map<String, Object> body = new HashMap<>();
        body.put("recipient", Map.of("id", recipientId));
        body.put("message", Map.of("text", text));
        if (humanAgentTag) {
            body.put("messaging_type", "MESSAGE_TAG");
            body.put("tag", "HUMAN_AGENT");
        } else {
            body.put("messaging_type", "RESPONSE");
        }
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
     * Send a message with quick-reply buttons. Each option is a Map with "title" and "payload";
     * titles are truncated to the 20-character limit. The text should still name the choices:
     * Instagram on desktop doesn't show the buttons.
     *
     * @return Meta's message id, or null if the message could not be delivered
     */
    public String sendWithQuickReplies(String recipientId, String text,
                                       List<Map<String, String>> options, String accessToken) {
        if (options.isEmpty() || options.size() > MAX_QUICK_REPLIES) {
            return sendText(recipientId, text, accessToken);
        }
        log.debug("Sending quick-replies to {}", recipientId);
        List<Map<String, Object>> quickReplies = new ArrayList<>();
        for (Map<String, String> opt : options) {
            Map<String, Object> qr = new HashMap<>();
            qr.put("content_type", "text");
            qr.put("title", truncate(opt.get("title"), MAX_QUICK_REPLY_TITLE));
            qr.put("payload", opt.get("payload"));
            quickReplies.add(qr);
        }
        return sendWithButtonsOrPlain(recipientId, text, quickReplies, accessToken);
    }

    /**
     * Ask for a phone number with Meta's "share my number" button, which fills in the number
     * from the customer's profile when tapped. Typing it still works.
     *
     * @return Meta's message id, or null if the message could not be delivered
     */
    public String sendPhoneRequest(String recipientId, String text, String accessToken) {
        return sendWithButtonsOrPlain(recipientId, text, List.of(Map.of("content_type", "user_phone_number")), accessToken);
    }

    /**
     * Send product cards that scroll sideways, at most 10 per message (more go in further
     * messages).
     *
     * @return the last message's id, or null if any part could not be delivered - the caller
     *         then falls back to a plain list
     */
    public String sendCards(String recipientId, List<Card> cards, String accessToken) {
        String messageId = null;
        for (int from = 0; from < cards.size(); from += MAX_CARDS) {
            List<Map<String, Object>> elements = new ArrayList<>();
            for (Card card : cards.subList(from, Math.min(from + MAX_CARDS, cards.size()))) {
                Map<String, Object> element = new HashMap<>();
                element.put("title", truncate(card.title(), MAX_CARD_TEXT));
                if (card.subtitle() != null && !card.subtitle().isBlank()) {
                    element.put("subtitle", truncate(card.subtitle(), MAX_CARD_TEXT));
                }
                if (card.imageUrl() != null) {
                    element.put("image_url", card.imageUrl());
                }
                element.put("buttons", List.of(Map.of(
                        "type", "postback",
                        "title", truncate(card.buttonTitle(), MAX_BUTTON_TITLE),
                        "payload", card.payload())));
                elements.add(element);
            }
            Map<String, Object> body = Map.of(
                    "recipient", Map.of("id", recipientId),
                    "message", Map.of("attachment", Map.of(
                            "type", "template",
                            "payload", Map.of("template_type", "generic", "elements", elements))));
            messageId = post(body, accessToken, recipientId).messageId();
            if (messageId == null) {
                return null;
            }
        }
        return messageId;
    }

    private String sendWithButtonsOrPlain(String recipientId, String text, List<Map<String, Object>> quickReplies,
                                          String accessToken) {
        Map<String, Object> message = new HashMap<>();
        message.put("text", text);
        message.put("quick_replies", quickReplies);
        Sent sent = post(Map.of("recipient", Map.of("id", recipientId), "message", message), accessToken, recipientId);
        if (sent.rejected()) {
            // Only when Meta refused it: after a timeout the first message may still arrive
            log.info("Meta refused buttons for {}; sending as plain text", recipientId);
            return sendText(recipientId, text, accessToken);
        }
        return sent.messageId();
    }

    /**
     * Send a numbered menu. The numbered list is always in the text, so full item names and
     * prices are visible everywhere; quick-reply buttons are added as a shortcut when the menu
     * fits the limit. The customer answers with the number.
     *
     * @return Meta's message id, or null if the message could not be delivered
     */
    public String sendMenuMessage(String recipientId, String introText, List<String> menuItems, String accessToken) {
        String text = renderMenuText(introText, menuItems);
        List<Map<String, String>> options = new ArrayList<>();
        for (int i = 0; i < menuItems.size(); i++) {
            String number = String.valueOf(i + 1);
            options.add(Map.of("title", number + ". " + menuItems.get(i), "payload", number));
        }
        return sendWithQuickReplies(recipientId, text, options, accessToken);
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
        return post(body, accessToken, recipientId).messageId();
    }

    private Sent post(Map<String, Object> body, String accessToken, String recipientId) {
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
            return new Sent(messageId != null ? messageId.toString() : null, false);
        } catch (WebClientResponseException e) {
            // Don't fail the conversation over one undelivered message; the state is already saved
            log.error("Meta rejected message to {}: {} {}", recipientId, e.getStatusCode(), e.getResponseBodyAsString());
            return new Sent(null, e.getStatusCode().is4xxClientError());
        } catch (Exception e) {
            log.error("Failed to send message to {}: {}", recipientId, e.getMessage());
            return new Sent(null, false);
        }
    }
}
