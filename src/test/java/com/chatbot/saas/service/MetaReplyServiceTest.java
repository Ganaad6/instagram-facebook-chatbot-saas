package com.chatbot.saas.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ClientHttpRequest;
import org.springframework.http.codec.HttpMessageWriter;
import org.springframework.mock.http.client.reactive.MockClientHttpRequest;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class MetaReplyServiceTest {

    private final List<JsonNode> sentBodies = new ArrayList<>();

    private MetaReplyService service(HttpStatus status, String responseJson) {
        WebClient webClient = WebClient.builder()
                .baseUrl("https://graph.example/v23.0")
                .exchangeFunction(request -> {
                    sentBodies.add(readBody(request));
                    return Mono.just(ClientResponse.create(status)
                            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                            .body(responseJson)
                            .build());
                })
                .build();
        return new MetaReplyService(webClient);
    }

    private static JsonNode readBody(ClientRequest request) {
        MockClientHttpRequest mock = new MockClientHttpRequest(request.method(), request.url());
        request.body().insert(mock, new BodyInserter.Context() {
            @Override
            public List<HttpMessageWriter<?>> messageWriters() {
                return ExchangeStrategies.withDefaults().messageWriters();
            }

            @Override
            public Optional<org.springframework.http.server.reactive.ServerHttpRequest> serverRequest() {
                return Optional.empty();
            }

            @Override
            public Map<String, Object> hints() {
                return Map.of();
            }
        }).block();
        try {
            return new ObjectMapper().readTree(mock.getBodyAsString().block());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void returnsMetaMessageIdOnSuccess() {
        MetaReplyService service = service(HttpStatus.OK, "{\"recipient_id\":\"r\",\"message_id\":\"m_123\"}");

        assertEquals("m_123", service.sendText("r", "hi", "token"));
    }

    @Test
    void returnsNullWhenMetaRejectsTheMessage() {
        MetaReplyService service = service(HttpStatus.BAD_REQUEST, "{\"error\":{\"message\":\"nope\"}}");

        assertNull(service.sendText("r", "hi", "token"));
    }

    @Test
    void facebookMenuKeepsFullTextAndTruncatesButtonTitles() {
        MetaReplyService service = service(HttpStatus.OK, "{\"message_id\":\"m\"}");

        service.sendMenuMessage("r", "FACEBOOK", "Pick:",
                List.of("Very long product name that exceeds limits — ₮125,000"), "token");

        JsonNode message = sentBodies.get(0).path("message");
        assertTrue(message.path("text").asText().contains("1. Very long product name that exceeds limits — ₮125,000"));
        String title = message.path("quick_replies").get(0).path("title").asText();
        assertTrue(title.length() <= 20, title);
        assertEquals("1", message.path("quick_replies").get(0).path("payload").asText());
    }

    @Test
    void facebookMenuOverQuickReplyLimitFallsBackToText() {
        MetaReplyService service = service(HttpStatus.OK, "{\"message_id\":\"m\"}");
        List<String> items = IntStream.rangeClosed(1, 14).mapToObj(i -> "Item " + i).toList();

        service.sendMenuMessage("r", "FACEBOOK", "Pick:", items, "token");

        JsonNode message = sentBodies.get(0).path("message");
        assertTrue(message.path("quick_replies").isMissingNode());
        assertTrue(message.path("text").asText().contains("14. Item 14"));
    }
}
