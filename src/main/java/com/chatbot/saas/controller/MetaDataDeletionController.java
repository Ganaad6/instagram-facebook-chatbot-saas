package com.chatbot.saas.controller;

import com.chatbot.saas.entity.DataDeletionRequest;
import com.chatbot.saas.service.MetaDataDeletionService;
import com.chatbot.saas.util.MetaSignedRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

/**
 * Meta's "Deauthorize callback URL" and "Data deletion request URL" (App settings → Basic,
 * Facebook Login settings). Public: trust comes from the signed_request, signed with the app
 * secret.
 */
@RestController
@RequestMapping("/webhook/meta")
@RequiredArgsConstructor
@Slf4j
public class MetaDataDeletionController {

    private final MetaDataDeletionService metaDataDeletionService;

    @Value("${meta.app.secret}")
    private String appSecret;

    @Value("${app.base-url}")
    private String baseUrl;

    @PostMapping(value = "/deauthorize", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Void> deauthorize(@RequestParam(name = "signed_request", required = false) String signedRequest) {
        return MetaSignedRequest.verifiedUserId(signedRequest, appSecret)
                .map(userId -> {
                    metaDataDeletionService.deauthorize(userId);
                    return ResponseEntity.ok().<Void>build();
                })
                .orElseGet(this::rejected);
    }

    /** Meta expects {@code {url, confirmation_code}}; the url is the public status page. */
    @PostMapping(value = "/data-deletion", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Map<String, String>> dataDeletion(
            @RequestParam(name = "signed_request", required = false) String signedRequest) {
        return MetaSignedRequest.verifiedUserId(signedRequest, appSecret)
                .map(userId -> {
                    DataDeletionRequest request = metaDataDeletionService.deleteUserData(userId);
                    String statusUrl = UriComponentsBuilder
                            .fromUriString(StringUtils.trimTrailingCharacter(baseUrl, '/'))
                            .path("/data-deletion")
                            .queryParam("code", request.getConfirmationCode())
                            .toUriString();
                    return ResponseEntity.ok(Map.of(
                            "url", statusUrl,
                            "confirmation_code", request.getConfirmationCode()));
                })
                .orElseGet(this::rejected);
    }

    private <T> ResponseEntity<T> rejected() {
        log.warn("Rejected a Meta callback with a missing or invalid signed_request");
        return ResponseEntity.badRequest().build();
    }
}
