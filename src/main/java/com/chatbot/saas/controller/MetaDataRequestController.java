package com.chatbot.saas.controller;

import com.chatbot.saas.entity.DataDeletionRequest;
import com.chatbot.saas.service.MetaDataRequestService;
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
 * Meta's "Deauthorize callback URL" and "Data deletion request URL" (App settings → Basic and
 * Facebook Login settings). Public; trust comes from the signed_request, signed with the app secret.
 */
@RestController
@RequestMapping("/webhook/meta")
@RequiredArgsConstructor
@Slf4j
public class MetaDataRequestController {

    private final MetaDataRequestService metaDataRequestService;

    @Value("${meta.app.secret}")
    private String appSecret;

    @Value("${app.base-url}")
    private String baseUrl;

    @PostMapping(path = "/deauthorize", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Void> deauthorize(@RequestParam(name = "signed_request", required = false) String signedRequest) {
        String userId = MetaSignedRequest.verifiedUserId(signedRequest, appSecret);
        if (userId == null) {
            log.warn("Rejected a deauthorize callback with an invalid signed_request");
            return ResponseEntity.status(403).build();
        }
        metaDataRequestService.deauthorize(userId);
        return ResponseEntity.ok().build();
    }

    /** Meta expects {url, confirmation_code}; it shows both to the person who asked. */
    @PostMapping(path = "/data-deletion", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Map<String, String>> dataDeletion(@RequestParam(name = "signed_request", required = false) String signedRequest) {
        String userId = MetaSignedRequest.verifiedUserId(signedRequest, appSecret);
        if (userId == null) {
            log.warn("Rejected a data-deletion callback with an invalid signed_request");
            return ResponseEntity.status(403).build();
        }
        DataDeletionRequest request = metaDataRequestService.deleteUserData(userId);
        String statusUrl = UriComponentsBuilder.fromUriString(StringUtils.trimTrailingCharacter(baseUrl, '/'))
                .path("/data-deletion")
                .queryParam("code", request.getConfirmationCode())
                .toUriString();
        return ResponseEntity.ok(Map.of("url", statusUrl, "confirmation_code", request.getConfirmationCode()));
    }
}
