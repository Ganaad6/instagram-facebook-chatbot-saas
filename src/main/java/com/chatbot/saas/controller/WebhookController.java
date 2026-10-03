package com.chatbot.saas.controller;

import com.chatbot.saas.service.WebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/webhook")
@RequiredArgsConstructor
@Slf4j
public class WebhookController {

    private final WebhookService webhookService;

    @GetMapping(produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> verifyWebhook(
            @RequestParam("hub.mode") String mode,
            @RequestParam("hub.verify_token") String token,
            @RequestParam("hub.challenge") String challenge) {
        log.debug("Webhook verification request received");
        String result = webhookService.verifyWebhook(mode, token, challenge);
        return ResponseEntity.ok(result);
    }

    @PostMapping
    public ResponseEntity<String> processWebhook(
            @RequestBody byte[] body,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature) {
        log.debug("Webhook event received");
        // Read raw bytes and decode as UTF-8 ourselves so the HMAC is computed over exactly
        // what Meta signed, whatever charset the request's Content-Type claims
        webhookService.processWebhookEvent(new String(body, StandardCharsets.UTF_8), signature);
        return ResponseEntity.ok("EVENT_RECEIVED");
    }
}
