package com.chatbot.saas.service;

import com.chatbot.saas.exception.MetaConnectException;
import tools.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

/**
 * Thin wrapper around the Graph API calls needed to connect a business's Facebook Page (and its
 * linked Instagram professional account). Kept separate from OAuthService so the page-selection
 * logic can be unit tested without a live Meta endpoint.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MetaGraphClient {

    private static final String PAGE_FIELDS = "id,name,access_token";
    private static final String INSTAGRAM_PAGE_FIELD = "instagram_business_account{id}";

    private final WebClient metaWebClient;

    @Value("${meta.app.id}")
    private String appId;

    @Value("${meta.app.secret}")
    private String appSecret;

    @Value("${meta.oauth.redirect-uri}")
    private String redirectUri;

    @Value("${meta.oauth.scopes}")
    private String scopes;

    public record PageAccount(String pageId, String name, String pageAccessToken, String instagramAccountId) {
    }

    /** Exchanges the OAuth authorization code for a short-lived (~1-2h) user access token. */
    public String exchangeCodeForUserToken(String code) {
        JsonNode response = call(() -> metaWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/oauth/access_token")
                        .queryParam("client_id", appId)
                        .queryParam("client_secret", appSecret)
                        .queryParam("redirect_uri", redirectUri)
                        .queryParam("code", code)
                        .build())
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block());
        return requireText(response, "access_token", "exchange the authorization code");
    }

    /**
     * Exchanges a short-lived user token for a long-lived (~60 day) one. Page tokens fetched
     * with a long-lived user token do not expire, which is why this step matters.
     */
    public String exchangeForLongLivedUserToken(String shortLivedUserToken) {
        JsonNode response = call(() -> metaWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/oauth/access_token")
                        .queryParam("grant_type", "fb_exchange_token")
                        .queryParam("client_id", appId)
                        .queryParam("client_secret", appSecret)
                        .queryParam("fb_exchange_token", shortLivedUserToken)
                        .build())
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block());
        return requireText(response, "access_token", "obtain a long-lived user token");
    }

    /** The app-scoped id of the user the token belongs to. */
    public String fetchUserId(String userToken) {
        JsonNode response = call(() -> metaWebClient.get()
                .uri(uriBuilder -> uriBuilder.path("/me").queryParam("fields", "id").build())
                .headers(h -> h.setBearerAuth(userToken))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block());
        if (response == null || !response.hasNonNull("id")) {
            throw new MetaConnectException("Meta did not return the Facebook user id");
        }
        return response.get("id").asText();
    }

    /** Lists the Pages the user granted access to, with each Page's token and linked Instagram account. */
    public List<PageAccount> listPages(String longLivedUserToken) {
        JsonNode response = call(() -> metaWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/me/accounts")
                        // Passed as a template value: the literal "{id}" would otherwise be
                        // parsed as a URI variable
                        .queryParam("fields", "{fields}")
                        .queryParam("limit", 100)
                        .build(pageFields()))
                .headers(h -> h.setBearerAuth(longLivedUserToken))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block());

        List<PageAccount> pages = new ArrayList<>();
        if (response == null) {
            return pages;
        }
        for (JsonNode page : response.path("data")) {
            String instagramId = page.path("instagram_business_account").path("id").asString(null);
            pages.add(new PageAccount(
                    page.path("id").asString(),
                    page.path("name").asString(),
                    page.path("access_token").asString(null),
                    instagramId));
        }
        return pages;
    }

    /**
     * The linked Instagram account is only readable with instagram_basic; asking for it
     * without that permission (a Messenger-only setup) could fail the whole Page listing.
     */
    private String pageFields() {
        boolean instagram = Arrays.stream(scopes.split(","))
                .map(String::trim)
                .anyMatch("instagram_basic"::equals);
        return instagram ? PAGE_FIELDS + "," + INSTAGRAM_PAGE_FIELD : PAGE_FIELDS;
    }

    /**
     * Subscribes our app to the Page's messaging webhooks - without this Meta never delivers
     * messages. message_echoes lets the app notice staff replying from the shop's Meta inbox.
     */
    public void subscribePageToWebhooks(String pageId, String pageAccessToken) {
        JsonNode response = call(() -> metaWebClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/{pageId}/subscribed_apps")
                        .queryParam("subscribed_fields", "messages,messaging_postbacks,message_echoes")
                        .build(pageId))
                .headers(h -> h.setBearerAuth(pageAccessToken))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block());
        if (response == null || !response.path("success").asBoolean(false)) {
            throw new MetaConnectException("Meta did not confirm the webhook subscription for page " + pageId);
        }
        log.info("Subscribed page {} to messaging webhooks", pageId);
    }

    private JsonNode call(Supplier<JsonNode> request) {
        try {
            return request.get();
        } catch (WebClientResponseException e) {
            throw new MetaConnectException("Meta API error: " + extractErrorMessage(e), e);
        } catch (WebClientRequestException e) {
            // The message would include the request URL, which carries tokens/the app secret
            throw new MetaConnectException("Could not reach Meta. Please try again.", e);
        }
    }

    private String extractErrorMessage(WebClientResponseException e) {
        try {
            JsonNode body = e.getResponseBodyAs(JsonNode.class);
            if (body != null && body.path("error").hasNonNull("message")) {
                return body.path("error").path("message").asString();
            }
        } catch (Exception ignored) {
            // Fall through to the status text
        }
        return e.getStatusCode() + " " + e.getStatusText();
    }

    private String requireText(JsonNode response, String field, String action) {
        if (response == null || !response.hasNonNull(field)) {
            throw new MetaConnectException("Meta did not return a token while trying to " + action);
        }
        return response.get(field).asString();
    }
}
