package com.chatbot.saas.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
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
    void menuKeepsFullTextAndTruncatesButtonTitles() {
        MetaReplyService service = service(HttpStatus.OK, "{\"message_id\":\"m\"}");

        service.sendMenuMessage("r", "Pick:",
                List.of("Very long product name that exceeds limits — ₮125,000"), "token");

        JsonNode message = sentBodies.get(0).path("message");
        assertTrue(message.path("text").asString().contains("1. Very long product name that exceeds limits — ₮125,000"));
        String title = message.path("quick_replies").get(0).path("title").asString();
        assertTrue(title.length() <= 20, title);
        assertEquals("1", message.path("quick_replies").get(0).path("payload").asString());
    }

    @Test
    void menuOverQuickReplyLimitFallsBackToText() {
        MetaReplyService service = service(HttpStatus.OK, "{\"message_id\":\"m\"}");
        List<String> items = IntStream.rangeClosed(1, 14).mapToObj(i -> "Item " + i).toList();

        service.sendMenuMessage("r", "Pick:", items, "token");

        JsonNode message = sentBodies.get(0).path("message");
        assertTrue(message.path("quick_replies").isMissingNode());
        assertTrue(message.path("text").asString().contains("14. Item 14"));
    }

    @Test
    void buttonsMetaRefusesAreResentAsPlainText() {
        MetaReplyService service = service(HttpStatus.BAD_REQUEST, "{\"error\":{\"message\":\"nope\"}}");

        service.sendWithQuickReplies("r", "Pick:\n1. A", List.of(Map.of("title", "1. A", "payload", "1")), "token");

        assertEquals(2, sentBodies.size());
        assertFalse(sentBodies.get(0).path("message").path("quick_replies").isMissingNode());
        assertTrue(sentBodies.get(1).path("message").path("quick_replies").isMissingNode());
        assertEquals("Pick:\n1. A", sentBodies.get(1).path("message").path("text").asString());
    }

    @Test
    void serverErrorsAreNotResent() {
        MetaReplyService service = service(HttpStatus.INTERNAL_SERVER_ERROR, "{}");

        service.sendWithQuickReplies("r", "Pick:", List.of(Map.of("title", "1", "payload", "1")), "token");

        assertEquals(1, sentBodies.size(), "the first message may still have arrived");
    }

    @Test
    void phoneRequestUsesTheShareNumberButton() {
        MetaReplyService service = service(HttpStatus.OK, "{\"message_id\":\"m\"}");

        service.sendPhoneRequest("r", "Утас?", "token");

        assertEquals("user_phone_number", sentBodies.get(0).path("message").path("quick_replies").get(0).path("content_type").asString());
    }

    @Test
    void cardsAreSentTenPerMessageWithPostbackButtons() {
        MetaReplyService service = service(HttpStatus.OK, "{\"message_id\":\"m\"}");
        List<MetaReplyService.Card> cards = IntStream.rangeClosed(1, 12)
                .mapToObj(i -> new MetaReplyService.Card(i + ". " + "x".repeat(100), "₮10", i == 1 ? "https://shop/media/1" : null,
                        "Захиалах", "PRODUCT_" + i))
                .toList();

        assertEquals("m", service.sendCards("r", cards, "token"));

        assertEquals(2, sentBodies.size());
        JsonNode payload = sentBodies.get(0).path("message").path("attachment").path("payload");
        assertEquals("generic", payload.path("template_type").asString());
        JsonNode elements = payload.path("elements");
        assertEquals(10, elements.size());
        assertTrue(elements.get(0).path("title").asString().length() <= 80);
        assertEquals("https://shop/media/1", elements.get(0).path("image_url").asString());
        assertTrue(elements.get(1).path("image_url").isMissingNode());
        JsonNode button = elements.get(0).path("buttons").get(0);
        assertEquals("postback", button.path("type").asString());
        assertEquals("PRODUCT_1", button.path("payload").asString());
        assertEquals(2, sentBodies.get(1).path("message").path("attachment").path("payload").path("elements").size());
    }

    @Test
    void refusedCardsReturnNullSoTheCallerCanFallBack() {
        MetaReplyService service = service(HttpStatus.BAD_REQUEST, "{}");

        assertNull(service.sendCards("r", List.of(new MetaReplyService.Card("1. A", "₮10", null, "Захиалах", "PRODUCT_1")), "token"));
    }
}
