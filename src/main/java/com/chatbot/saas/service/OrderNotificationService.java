package com.chatbot.saas.service;

import com.chatbot.saas.entity.Business;
import com.chatbot.saas.entity.Order;
import lombok.extern.slf4j.Slf4j;
import com.chatbot.saas.util.PublicAddressGuard;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Pushes events to the business's notification webhook URL (if configured): new orders, QPay
 * payments, and customers asking to talk to a person.
 */
@Service
@Slf4j
public class OrderNotificationService {

    /** Only reaches public addresses; see NotificationClientConfig. */
    private final WebClient notificationWebClient;

    private final boolean allowHttp;

    public OrderNotificationService(@Qualifier("notificationWebClient") WebClient notificationWebClient,
                                    @Value("${notifications.allow-http:false}") boolean allowHttp) {
        this.notificationWebClient = notificationWebClient;
        this.allowHttp = allowHttp;
    }

    @Async
    public void notifyNewOrder(Business business, Order order) {
        if (business.getNotificationWebhookUrl() == null || business.getNotificationWebhookUrl().isBlank()) {
            return;
        }
        try {
            Map<String, Object> payload = Map.ofEntries(
                    Map.entry("event", "NEW_ORDER"),
                    Map.entry("orderId", order.getId()),
                    Map.entry("businessId", business.getId()),
                    Map.entry("product", order.getProductName()),
                    Map.entry("quantity", order.getQuantity()),
                    Map.entry("unitPrice", order.getUnitPrice()),
                    Map.entry("totalAmount", order.getTotalAmount()),
                    Map.entry("customerName", order.getCustomerName() != null ? order.getCustomerName() : ""),
                    Map.entry("phone", order.getPhone() != null ? order.getPhone() : ""),
                    Map.entry("address", order.getAddress() != null ? order.getAddress() : ""),
                    Map.entry("status", order.getStatus().name())
            );
            post(business.getNotificationWebhookUrl(), payload, "order " + order.getId());
        } catch (Exception e) {
            log.warn("Could not send order notification: {}", e.getMessage());
        }
    }

    /**
     * A customer asked for a person; the bot is paused for them until staff reply or resume it.
     * Takes plain values (not entities) since it runs on another thread after the caller's
     * transaction may have ended.
     */
    @Async
    public void notifyHandoffRequested(String webhookUrl, Long businessId, Long customerId,
                                       String platform, String message) {
        if (webhookUrl == null || webhookUrl.isBlank()) {
            return;
        }
        try {
            Map<String, Object> payload = Map.of(
                    "event", "HANDOFF_REQUESTED",
                    "businessId", businessId,
                    "customerId", customerId,
                    "platform", platform,
                    "message", message != null ? message : "");
            post(webhookUrl, payload, "handoff for customer " + customerId);
        } catch (Exception e) {
            log.warn("Could not send handoff notification: {}", e.getMessage());
        }
    }

    /** A customer paid an order's QPay invoice. Takes plain values, like notifyHandoffRequested. */
    @Async
    public void notifyPaymentReceived(String webhookUrl, Long businessId, Long orderId,
                                      BigDecimal amount, String paymentId) {
        if (webhookUrl == null || webhookUrl.isBlank()) {
            return;
        }
        try {
            Map<String, Object> payload = Map.of(
                    "event", "PAYMENT_RECEIVED",
                    "businessId", businessId,
                    "orderId", orderId,
                    "amount", amount,
                    "provider", "QPAY",
                    "paymentId", paymentId != null ? paymentId : "");
            post(webhookUrl, payload, "payment of order " + orderId);
        } catch (Exception e) {
            log.warn("Could not send payment notification: {}", e.getMessage());
        }
    }

    private void post(String url, Map<String, Object> payload, String what) {
        // Re-checked at send time: the resolver guard doesn't see IP-literal hosts, and URLs
        // saved before validation existed were never checked
        String problem = PublicAddressGuard.problemWith(url, allowHttp);
        if (problem != null) {
            log.warn("Not sending notification for {}: webhook URL refused ({})", what, problem);
            return;
        }
        notificationWebClient
                .post()
                .uri(url)
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(String.class)
                .doOnSuccess(r -> log.info("Notification sent for {}", what))
                .doOnError(e -> log.warn("Failed to send notification for {}: {}", what, e.getMessage()))
                .subscribe();
    }
}
