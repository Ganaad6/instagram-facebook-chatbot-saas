package com.chatbot.saas.service;

import com.chatbot.saas.entity.Business;
import com.chatbot.saas.repository.BusinessRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class OAuthService {

    private final BusinessRepository businessRepository;
    private final WebClient metaWebClient;

    @Value("${meta.app.id}")
    private String appId;

    @Value("${meta.app.secret}")
    private String appSecret;

    @Value("${meta.oauth.redirect-uri}")
    private String redirectUri;

    public String generateAuthorizationUrl() {
        return UriComponentsBuilder.fromUriString("https://www.facebook.com/v18.0/dialog/oauth")
                .queryParam("client_id", appId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("scope", "instagram_basic,instagram_manage_messages,pages_messaging,pages_show_list")
                .queryParam("response_type", "code")
                .build()
                .toUriString();
    }

    @Transactional
    public void handleCallback(String code, Long businessId) {
        log.debug("Handling OAuth callback for business {}", businessId);
        Map<?, ?> tokenResponse = metaWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/oauth/access_token")
                        .queryParam("client_id", appId)
                        .queryParam("client_secret", appSecret)
                        .queryParam("redirect_uri", redirectUri)
                        .queryParam("code", code)
                        .build())
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        if (tokenResponse != null && tokenResponse.containsKey("access_token")) {
            Business business = businessRepository.findById(businessId)
                    .orElseThrow(() -> new RuntimeException("Business not found: " + businessId));
            business.setAccessToken(tokenResponse.get("access_token").toString());
            business.setTokenExpiresAt(LocalDateTime.now().plusDays(60));
            businessRepository.save(business);
            log.info("Access token saved for business {}", businessId);
        }
    }

    @Transactional
    public void refreshToken(Long businessId) {
        log.debug("Refreshing token for business {}", businessId);
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new RuntimeException("Business not found: " + businessId));

        if (business.getAccessToken() == null) {
            throw new RuntimeException("No access token to refresh for business: " + businessId);
        }

        Map<?, ?> tokenResponse = metaWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/oauth/access_token")
                        .queryParam("grant_type", "fb_exchange_token")
                        .queryParam("client_id", appId)
                        .queryParam("client_secret", appSecret)
                        .queryParam("fb_exchange_token", business.getAccessToken())
                        .build())
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        if (tokenResponse != null && tokenResponse.containsKey("access_token")) {
            business.setAccessToken(tokenResponse.get("access_token").toString());
            business.setTokenExpiresAt(LocalDateTime.now().plusDays(60));
            businessRepository.save(business);
        }
    }
}
