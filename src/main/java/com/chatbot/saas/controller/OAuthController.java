package com.chatbot.saas.controller;

import com.chatbot.saas.security.TenantContext;
import com.chatbot.saas.service.OAuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/auth/meta")
@RequiredArgsConstructor
public class OAuthController {

    private final OAuthService oAuthService;
    private final TenantContext tenantContext;

    @GetMapping("/authorize")
    public ResponseEntity<Void> authorize(@RequestParam Long businessId) {
        tenantContext.assertAccess(businessId);
        String authUrl = oAuthService.generateAuthorizationUrl(businessId);
        return ResponseEntity.status(302).location(URI.create(authUrl)).build();
    }

    /**
     * Public callback - Meta redirects the business owner's browser here with no way to attach
     * an API key. Trust is instead derived from the signed, expiring `state` (see
     * OAuthStateService), not from a client-suppliable businessId.
     */
    @GetMapping("/callback")
    public ResponseEntity<String> callback(@RequestParam String code,
                                           @RequestParam String state) {
        oAuthService.handleCallback(code, state);
        return ResponseEntity.ok("Authorization successful");
    }

    @PostMapping("/refresh")
    public ResponseEntity<String> refreshToken(@RequestParam Long businessId) {
        tenantContext.assertAccess(businessId);
        oAuthService.refreshToken(businessId);
        return ResponseEntity.ok("Token refreshed successfully");
    }
}
