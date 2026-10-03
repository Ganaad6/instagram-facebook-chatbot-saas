package com.chatbot.saas.controller;

import com.chatbot.saas.entity.Business;
import com.chatbot.saas.exception.BusinessNotFoundException;
import com.chatbot.saas.exception.MetaConnectException;
import com.chatbot.saas.security.TenantContext;
import com.chatbot.saas.service.OAuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

@RestController
@RequestMapping("/api/auth/meta")
@RequiredArgsConstructor
@Slf4j
public class OAuthController {

    private final OAuthService oAuthService;
    private final TenantContext tenantContext;

    @Value("${app.base-url}")
    private String baseUrl;

    /**
     * Returns the Meta connect link as JSON rather than redirecting: a browser can't attach the
     * X-API-Key header, so the link is fetched via the API and then opened by the shop owner.
     */
    @GetMapping("/authorize")
    public ResponseEntity<Map<String, Object>> authorize(@RequestParam Long businessId) {
        tenantContext.assertOwner(businessId);
        return ResponseEntity.ok(Map.of(
                "authorizationUrl", oAuthService.generateAuthorizationUrl(businessId),
                "expiresInMinutes", oAuthService.getAuthorizationUrlTtlMinutes()));
    }

    /**
     * Public callback - Meta redirects the business owner's browser here with no way to attach
     * an API key. Trust is instead derived from the signed, expiring `state` (see
     * OAuthStateService), not from a client-suppliable businessId. Sends the person back to the
     * dashboard's settings page with the outcome.
     */
    @GetMapping("/callback")
    public ResponseEntity<Void> callback(@RequestParam(required = false) String code,
                                         @RequestParam(required = false) String state,
                                         @RequestParam(name = "error_description", required = false) String errorDescription) {
        if (code == null || state == null) {
            return backToDashboard("failed", errorDescription != null ? errorDescription : "Холболтыг цуцалсан байна.");
        }
        try {
            Business business = oAuthService.handleCallback(code, state);
            return backToDashboard(business.getInstagramAccountId() != null ? "connected" : "connected-page-only", null);
        } catch (IllegalArgumentException e) {
            return backToDashboard("failed", "Холбох холбоосын хугацаа дууссан байна. Дахин оролдоно уу.");
        } catch (MetaConnectException | BusinessNotFoundException e) {
            log.warn("Meta connect failed: {}", e.getMessage());
            return backToDashboard("failed", e.getMessage());
        }
    }

    private ResponseEntity<Void> backToDashboard(String outcome, String message) {
        UriComponentsBuilder uri = UriComponentsBuilder.fromUriString(StringUtils.trimTrailingCharacter(baseUrl, '/'))
                .path("/settings")
                .queryParam("meta", outcome);
        if (message != null) {
            uri.queryParam("message", message);
        }
        return ResponseEntity.status(HttpStatus.FOUND).location(uri.encode().build().toUri()).build();
    }
}
