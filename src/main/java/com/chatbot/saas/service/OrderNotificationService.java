package com.chatbot.saas.service;

import com.chatbot.saas.entity.Business;
import com.chatbot.saas.entity.Order;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderNotificationService {

    private final WebClient.Builder webClientBuilder;

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
            webClientBuilder.build()
                    .post()
                    .uri(business.getNotificationWebhookUrl())
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(String.class)
                    .doOnSuccess(r -> log.info("Notification sent for order {} to {}", order.getId(), business.getNotificationWebhookUrl()))
                    .doOnError(e -> log.warn("Failed to send notification for order {}: {}", order.getId(), e.getMessage()))
                    .subscribe();
        } catch (Exception e) {
            log.warn("Could not send order notification: {}", e.getMessage());
        }
    }
}
