package com.chatbot.saas.controller;

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

    @GetMapping("/authorize")
    public ResponseEntity<Void> authorize() {
        String authUrl = oAuthService.generateAuthorizationUrl();
        return ResponseEntity.status(302).location(URI.create(authUrl)).build();
    }

    @GetMapping("/callback")
    public ResponseEntity<String> callback(@RequestParam String code,
                                           @RequestParam(name = "state") Long businessId) {
        oAuthService.handleCallback(code, businessId);
        return ResponseEntity.ok("Authorization successful");
    }

    @PostMapping("/refresh")
    public ResponseEntity<String> refreshToken(@RequestParam Long businessId) {
        oAuthService.refreshToken(businessId);
        return ResponseEntity.ok("Token refreshed successfully");
    }
}
