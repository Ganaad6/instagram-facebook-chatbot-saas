package com.chatbot.saas.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

class OrderNotificationServiceTest {

    private final List<ClientRequest> sent = new CopyOnWriteArrayList<>();
    private final OrderNotificationService service = new OrderNotificationService(WebClient.builder()
            .exchangeFunction(request -> {
                sent.add(request);
                return Mono.just(ClientResponse.create(HttpStatus.OK).build());
            }).build(), false);

    /** URLs saved before validation existed, and IP literals the resolver guard never sees. */
    @Test
    void refusesPrivateOrPlainHttpUrlsAtSendTime() {
        for (String url : new String[]{"http://127.0.0.1:8080/api/admin", "https://169.254.169.254/latest",
                "https://10.0.0.7/hook", "http://93.184.215.14/hook"}) {
            service.notifyHandoffRequested(url, 1L, 2L, "FACEBOOK", "hi");
        }
        assertTrue(sent.isEmpty(), "sent to " + sent.stream().map(ClientRequest::url).toList());
    }

    @Test
    void sendsToPublicHttpsUrls() {
        service.notifyHandoffRequested("https://93.184.215.14/hook", 1L, 2L, "FACEBOOK", "hi");

        assertEquals(1, sent.size());
        assertEquals("https://93.184.215.14/hook", sent.get(0).url().toString());
    }
}
