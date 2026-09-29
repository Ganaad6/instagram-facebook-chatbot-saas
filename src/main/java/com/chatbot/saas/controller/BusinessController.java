package com.chatbot.saas.controller;

import com.chatbot.saas.dto.request.BusinessRegistrationRequest;
import com.chatbot.saas.dto.request.BusinessUpdateRequest;
import com.chatbot.saas.dto.request.NotificationWebhookRequest;
import com.chatbot.saas.dto.response.BusinessRegistrationResponse;
import com.chatbot.saas.dto.response.BusinessResponse;
import com.chatbot.saas.entity.Business;
import com.chatbot.saas.repository.BusinessRepository;
import com.chatbot.saas.security.TenantContext;
import com.chatbot.saas.service.BusinessService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/businesses")
@RequiredArgsConstructor
public class BusinessController {

    private final BusinessService businessService;
    private final BusinessRepository businessRepository;
    private final TenantContext tenantContext;

    @PostMapping("/register")
    public ResponseEntity<BusinessRegistrationResponse> registerBusiness(@Valid @RequestBody BusinessRegistrationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(businessService.registerBusiness(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<BusinessResponse> getBusinessById(@PathVariable Long id) {
        tenantContext.assertAccess(id);
        return ResponseEntity.ok(businessService.getBusinessById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<BusinessResponse> updateBusiness(@PathVariable Long id,
                                                           @Valid @RequestBody BusinessUpdateRequest request) {
        tenantContext.assertOwner(id);
        return ResponseEntity.ok(businessService.updateBusiness(id, request));
    }

    /**
     * Issues a new API key for integrations, returned once; the previous key stops working.
     * Lets a dashboard owner get a key without going through the admin.
     */
    @PostMapping("/{id}/api-key")
    public ResponseEntity<BusinessRegistrationResponse> rotateApiKey(@PathVariable Long id) {
        tenantContext.assertOwner(id);
        return ResponseEntity.ok(businessService.rotateApiKey(id));
    }

    @PostMapping("/{id}/notifications/webhook-url")
    public ResponseEntity<Map<String, String>> setNotificationWebhookUrl(
            @PathVariable Long id,
            @RequestBody NotificationWebhookRequest request) {
        tenantContext.assertOwner(id);
        Business business = businessService.findBusinessById(id);
        business.setNotificationWebhookUrl(request.getWebhookUrl());
        businessRepository.save(business);
        return ResponseEntity.ok(Map.of("message", "Notification webhook URL updated"));
    }
}
