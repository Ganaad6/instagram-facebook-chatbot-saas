package com.chatbot.saas.controller;

import com.chatbot.saas.dto.request.AuthRequests;
import com.chatbot.saas.dto.response.StaffUserResponse;
import com.chatbot.saas.security.StaffPrincipal;
import com.chatbot.saas.security.TenantContext;
import com.chatbot.saas.service.StaffAuthService;
import com.chatbot.saas.service.StaffService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** The shop owner managing dashboard logins. Invite and reset links are returned to hand over. */
@RestController
@RequestMapping("/api/businesses/{businessId}/staff")
@RequiredArgsConstructor
public class StaffController {

    private final StaffService staffService;
    private final TenantContext tenantContext;

    @GetMapping
    public ResponseEntity<List<StaffUserResponse>> list(@PathVariable Long businessId) {
        tenantContext.assertOwner(businessId);
        return ResponseEntity.ok(staffService.list(businessId));
    }

    @PostMapping
    public ResponseEntity<StaffService.Invitation> invite(@PathVariable Long businessId,
                                                          @Valid @RequestBody AuthRequests.InviteStaff request) {
        tenantContext.assertOwner(businessId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(staffService.invite(businessId, request.email(), request.name(), request.role()));
    }

    @PostMapping("/{userId}/password-link")
    public ResponseEntity<StaffAuthService.IssuedLink> passwordLink(@PathVariable Long businessId,
                                                                    @PathVariable Long userId) {
        tenantContext.assertOwner(businessId);
        return ResponseEntity.ok(staffService.passwordLink(businessId, userId));
    }

    @PutMapping("/{userId}")
    public ResponseEntity<StaffUserResponse> update(@PathVariable Long businessId, @PathVariable Long userId,
                                                    @RequestBody AuthRequests.UpdateStaff request) {
        tenantContext.assertOwner(businessId);
        return ResponseEntity.ok(staffService.update(businessId, userId, request.role(), request.active(), actingUserId()));
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> remove(@PathVariable Long businessId, @PathVariable Long userId) {
        tenantContext.assertOwner(businessId);
        staffService.remove(businessId, userId, actingUserId());
        return ResponseEntity.noContent().build();
    }

    /** Null when the shop's API key is used, which may manage anyone. */
    private Long actingUserId() {
        return tenantContext.currentStaff().map(StaffPrincipal::userId).orElse(null);
    }
}
