package com.chatbot.saas.controller;

import com.chatbot.saas.dto.request.BusinessRegistrationRequest;
import com.chatbot.saas.dto.request.BusinessUpdateRequest;
import com.chatbot.saas.dto.request.NotificationWebhookRequest;
import com.chatbot.saas.dto.response.BusinessRegistrationResponse;
import com.chatbot.saas.dto.response.BusinessResponse;
import com.chatbot.saas.entity.Business;
import com.chatbot.saas.repository.BusinessRepository;
import com.chatbot.saas.security.TenantContext;
import com.chatbot.saas.exception.ValidationException;
import com.chatbot.saas.service.BusinessService;
import com.chatbot.saas.util.PublicAddressGuard;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;
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

    /** Plain-http webhook URLs are only for local development. */
    @Value("${notifications.allow-http:false}")
    private boolean allowHttpNotifications;

    /** Self-serve sign-up switch; covers this API registration as well as the dashboard's. */
    @Value("${auth.signup-enabled:true}")
    private boolean signupEnabled;

    @PostMapping("/register")
    public ResponseEntity<BusinessRegistrationResponse> registerBusiness(@Valid @RequestBody BusinessRegistrationRequest request) {
        if (!signupEnabled) {
            throw new ValidationException("Шинэ бүртгэл одоогоор хаалттай байна");
        }
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
        String url = StringUtils.hasText(request.getWebhookUrl()) ? request.getWebhookUrl().trim() : null;
        if (url != null) {
            String problem = PublicAddressGuard.problemWith(url, allowHttpNotifications);
            if (problem != null) {
                throw new ValidationException(problem);
            }
        }
        Business business = businessService.findBusinessById(id);
        business.setNotificationWebhookUrl(url);
        businessRepository.save(business);
        return ResponseEntity.ok(Map.of("message", "Notification webhook URL updated"));
    }
}
