package com.chatbot.saas.dto.request;

import com.chatbot.saas.entity.StaffUser;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Request bodies for dashboard sign-in and staff management. */
public final class AuthRequests {

    private AuthRequests() {
    }

    public record Login(@NotBlank @Email String email, @NotBlank String password) {
    }

    public record Signup(@NotBlank @Size(max = 255) String businessName,
                         @NotBlank @Size(max = 255) String name,
                         @NotBlank @Email @Size(max = 255) String email,
                         @NotBlank String password) {
    }

    public record AcceptLink(@NotBlank String token, @Size(max = 255) String name, @NotBlank String password) {
    }

    public record ChangePassword(@NotBlank String currentPassword, @NotBlank String newPassword) {
    }

    public record InviteStaff(@NotBlank @Email @Size(max = 255) String email,
                              @Size(max = 255) String name,
                              @NotNull StaffUser.Role role) {
    }

    public record UpdateStaff(StaffUser.Role role, Boolean active) {
    }

    public record InviteOwner(@NotBlank @Email @Size(max = 255) String email, @Size(max = 255) String name) {
    }
}
