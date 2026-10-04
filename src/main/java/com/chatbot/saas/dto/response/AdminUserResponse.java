package com.chatbot.saas.dto.response;

import com.chatbot.saas.entity.StaffUser;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/** A dashboard login with the shop it belongs to, for the platform admin's list of all users. */
@Data
@Builder
public class AdminUserResponse {
    private Long id;
    private String email;
    private String name;
    private String role;
    private boolean active;
    /** Invited but hasn't set a password yet. */
    private boolean invitePending;
    /** Too many wrong passwords; sign-in is blocked until lockedUntil. */
    private boolean locked;
    private LocalDateTime lockedUntil;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;
    private Long businessId;
    private String businessName;
    private String businessStatus;

    public static AdminUserResponse from(StaffUser user, LocalDateTime now) {
        LocalDateTime lockedUntil = user.getLockedUntil();
        return AdminUserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .name(user.getName())
                .role(user.getRole().name())
                .active(user.isActive())
                .invitePending(user.getPasswordHash() == null)
                .locked(lockedUntil != null && lockedUntil.isAfter(now))
                .lockedUntil(lockedUntil)
                .lastLoginAt(user.getLastLoginAt())
                .createdAt(user.getCreatedAt())
                .businessId(user.getBusiness().getId())
                .businessName(user.getBusiness().getName())
                .businessStatus(user.getBusiness().getStatus().name())
                .build();
    }
}
