package com.chatbot.saas.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class MessageService {

    private final WebClient metaWebClient;

    public void sendMessage(String recipientId, String message, String accessToken) {
        log.debug("Sending message to recipient: {}", recipientId);
        Map<String, Object> body = Map.of(
                "recipient", Map.of("id", recipientId),
                "message", Map.of("text", message)
        );

        metaWebClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/me/messages")
                        .queryParam("access_token", accessToken)
                        .build())
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .doOnSuccess(resp -> log.debug("Message sent successfully to {}", recipientId))
                .doOnError(err -> log.error("Failed to send message to {}: {}", recipientId, err.getMessage()))
                .subscribe();
    }
}
