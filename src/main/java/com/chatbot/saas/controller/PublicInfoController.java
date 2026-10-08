package com.chatbot.saas.controller;

import com.chatbot.saas.service.MetaDataDeletionService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/** Unauthenticated data for the public pages: privacy policy, terms and data-deletion status. */
@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PublicInfoController {

    private final MetaDataDeletionService metaDataDeletionService;

    @Value("${legal.operator-name:}")
    private String operatorName;

    @Value("${legal.contact-email:}")
    private String contactEmail;

    @Value("${legal.address:}")
    private String address;

    @Value("${privacy.message-retention-days:365}")
    private int messageRetentionDays;

    public record LegalInfo(String operatorName, String contactEmail, String address, int messageRetentionDays) {
    }

    public record DeletionStatus(String confirmationCode, String status, LocalDateTime completedAt) {
    }

    @GetMapping("/legal")
    public ResponseEntity<LegalInfo> legal() {
        return ResponseEntity.ok(new LegalInfo(operatorName, contactEmail, address, messageRetentionDays));
    }

    @GetMapping("/data-deletion/{code}")
    public ResponseEntity<DeletionStatus> deletionStatus(@PathVariable String code) {
        return metaDataDeletionService.findRequest(code)
                .map(r -> ResponseEntity.ok(new DeletionStatus(r.getConfirmationCode(), "COMPLETED", r.getCompletedAt())))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
