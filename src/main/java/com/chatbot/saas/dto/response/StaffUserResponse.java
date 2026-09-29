package com.chatbot.saas.dto.response;

import com.chatbot.saas.entity.StaffUser;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class StaffUserResponse {
    private Long id;
    private String email;
    private String name;
    private String role;
    private boolean active;
    /** Invited but hasn't set a password yet. */
    private boolean invitePending;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;

    public static StaffUserResponse from(StaffUser user) {
        return StaffUserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .name(user.getName())
                .role(user.getRole().name())
                .active(user.isActive())
                .invitePending(user.getPasswordHash() == null)
                .lastLoginAt(user.getLastLoginAt())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
