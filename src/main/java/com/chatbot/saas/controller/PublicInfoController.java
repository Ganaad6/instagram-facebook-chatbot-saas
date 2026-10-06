package com.chatbot.saas.controller;

import com.chatbot.saas.service.MetaDataRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/** Unauthenticated data for the public privacy, terms and data-deletion pages. */
@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PublicInfoController {

    private final MetaDataRequestService metaDataRequestService;

    @Value("${legal.operator-name}")
    private String operatorName;

    @Value("${legal.contact-email}")
    private String contactEmail;

    @Value("${legal.policy-updated}")
    private String policyUpdated;

    @GetMapping("/legal")
    public Map<String, String> legal() {
        return Map.of("operatorName", operatorName, "contactEmail", contactEmail, "policyUpdated", policyUpdated);
    }

    @GetMapping("/data-deletion/{code}")
    public ResponseEntity<Map<String, Object>> deletionStatus(@PathVariable String code) {
        return metaDataRequestService.findRequest(code)
                .map(request -> {
                    Map<String, Object> body = new HashMap<>();
                    body.put("confirmationCode", request.getConfirmationCode());
                    body.put("requestedAt", request.getRequestedAt());
                    body.put("completedAt", request.getCompletedAt());
                    return ResponseEntity.ok(body);
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
