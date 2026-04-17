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

    private final WebClient metaWebClient;

    @Async
    public void notifyNewOrder(Business business, Order order) {
        if (business.getNotificationWebhookUrl() == null || business.getNotificationWebhookUrl().isBlank()) {
            return;
        }
        try {
            Map<String, Object> payload = Map.of(
                    "event", "NEW_ORDER",
                    "orderId", order.getId(),
                    "businessId", business.getId(),
                    "product", order.getProduct().getName(),
                    "customerName", order.getCustomerName() != null ? order.getCustomerName() : "",
                    "phone", order.getPhone() != null ? order.getPhone() : "",
                    "address", order.getAddress() != null ? order.getAddress() : "",
                    "status", order.getStatus().name()
            );
            WebClient.create(business.getNotificationWebhookUrl())
                    .post()
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
