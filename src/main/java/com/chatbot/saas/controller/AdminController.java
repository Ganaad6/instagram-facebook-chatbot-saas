package com.chatbot.saas.controller;

import com.chatbot.saas.dto.response.BusinessRegistrationResponse;
import com.chatbot.saas.dto.response.BusinessResponse;
import com.chatbot.saas.service.BusinessService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Operator-only endpoints for onboarding and lifecycle management of tenant businesses.
 * Protected by HTTP Basic auth (ROLE_ADMIN) - see SecurityConfig. There is no admin UI yet,
 * so this is used directly (e.g. via curl) as part of the manual-billing workflow: suspend a
 * business on non-payment, re-activate once paid, rotate a leaked API key.
 */
@RestController
@RequestMapping("/api/admin/businesses")
@RequiredArgsConstructor
public class AdminController {

    private final BusinessService businessService;

    @GetMapping
    public ResponseEntity<List<BusinessResponse>> listBusinesses() {
        return ResponseEntity.ok(businessService.getAllBusinesses());
    }

    @PostMapping("/{id}/suspend")
    public ResponseEntity<BusinessResponse> suspend(@PathVariable Long id) {
        return ResponseEntity.ok(businessService.suspend(id));
    }

    @PostMapping("/{id}/activate")
    public ResponseEntity<BusinessResponse> activate(@PathVariable Long id) {
        return ResponseEntity.ok(businessService.activate(id));
    }

    @PostMapping("/{id}/rotate-api-key")
    public ResponseEntity<BusinessRegistrationResponse> rotateApiKey(@PathVariable Long id) {
        return ResponseEntity.ok(businessService.rotateApiKey(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteBusiness(@PathVariable Long id) {
        businessService.deleteBusiness(id);
        return ResponseEntity.noContent().build();
    }
}
