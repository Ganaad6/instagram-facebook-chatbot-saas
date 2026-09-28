package com.chatbot.saas.service;

import com.chatbot.saas.exception.MetaConnectException;
import com.chatbot.saas.service.MetaGraphClient.PageAccount;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Exercises the real WebClient request building against a stubbed transport, so mistakes in
 * paths, query parameters or URI templating surface here rather than against live Meta.
 */
class MetaGraphClientTest {

    private final List<ClientRequest> requests = new ArrayList<>();

    private MetaGraphClient client(HttpStatus status, String json) {
        WebClient webClient = WebClient.builder()
                .baseUrl("https://graph.example/v23.0")
                .exchangeFunction(request -> {
                    requests.add(request);
                    return Mono.just(ClientResponse.create(status)
                            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                            .body(json)
                            .build());
                })
                .build();
        MetaGraphClient client = new MetaGraphClient(webClient);
        ReflectionTestUtils.setField(client, "appId", "app");
        ReflectionTestUtils.setField(client, "appSecret", "secret");
        ReflectionTestUtils.setField(client, "redirectUri", "https://host/cb");
        return client;
    }

    @Test
    void listPagesRequestsInstagramAccountAndParsesResponse() {
        MetaGraphClient client = client(HttpStatus.OK, """
                {"data":[{"id":"p1","name":"Shop","access_token":"pt1","instagram_business_account":{"id":"ig1"}},
                         {"id":"p2","name":"Other","access_token":"pt2"}]}""");

        List<PageAccount> pages = client.listPages("user-token");

        assertEquals(List.of(new PageAccount("p1", "Shop", "pt1", "ig1"),
                new PageAccount("p2", "Other", "pt2", null)), pages);
        ClientRequest request = requests.get(0);
        String query = URLDecoder.decode(request.url().getRawQuery(), StandardCharsets.UTF_8);
        assertEquals("/v23.0/me/accounts", request.url().getPath());
        assertTrue(query.contains("fields=id,name,access_token,instagram_business_account{id}"), query);
        assertFalse(query.contains("user-token"), "token must not be in the URL");
        assertEquals("Bearer user-token", request.headers().getFirst(HttpHeaders.AUTHORIZATION));
    }

    @Test
    void subscribePagePostsToPageSubscribedApps() {
        MetaGraphClient client = client(HttpStatus.OK, "{\"success\":true}");

        client.subscribePageToWebhooks("p1", "page-token");

        ClientRequest request = requests.get(0);
        assertEquals("/v23.0/p1/subscribed_apps", request.url().getPath());
        assertTrue(request.url().getQuery().contains("subscribed_fields=messages,messaging_postbacks"));
        assertEquals("Bearer page-token", request.headers().getFirst(HttpHeaders.AUTHORIZATION));
    }

    @Test
    void metaErrorMessageIsSurfaced() {
        MetaGraphClient client = client(HttpStatus.BAD_REQUEST,
                "{\"error\":{\"message\":\"Invalid verification code format.\"}}");

        MetaConnectException e = assertThrows(MetaConnectException.class,
                () -> client.exchangeCodeForUserToken("bad"));
        assertTrue(e.getMessage().contains("Invalid verification code format."));
        assertFalse(e.getMessage().contains("secret"));
    }
}
