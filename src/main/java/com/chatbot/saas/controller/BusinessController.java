package com.chatbot.saas.controller;

import com.chatbot.saas.dto.request.BusinessRegistrationRequest;
import com.chatbot.saas.dto.request.BusinessUpdateRequest;
import com.chatbot.saas.dto.request.NotificationWebhookRequest;
import com.chatbot.saas.dto.response.BusinessResponse;
import com.chatbot.saas.entity.Business;
import com.chatbot.saas.repository.BusinessRepository;
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

    @PostMapping("/register")
    public ResponseEntity<BusinessResponse> registerBusiness(@Valid @RequestBody BusinessRegistrationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(businessService.registerBusiness(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<BusinessResponse> getBusinessById(@PathVariable Long id) {
        return ResponseEntity.ok(businessService.getBusinessById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<BusinessResponse> updateBusiness(@PathVariable Long id,
                                                           @Valid @RequestBody BusinessUpdateRequest request) {
        return ResponseEntity.ok(businessService.updateBusiness(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteBusiness(@PathVariable Long id) {
        businessService.deleteBusiness(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/notifications/webhook-url")
    public ResponseEntity<Map<String, String>> setNotificationWebhookUrl(
            @PathVariable Long id,
            @RequestBody NotificationWebhookRequest request) {
        Business business = businessService.findBusinessById(id);
        business.setNotificationWebhookUrl(request.getWebhookUrl());
        businessRepository.save(business);
        return ResponseEntity.ok(Map.of("message", "Notification webhook URL updated"));
    }
}
