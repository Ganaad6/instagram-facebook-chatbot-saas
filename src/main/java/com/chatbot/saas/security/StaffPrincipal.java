package com.chatbot.saas.security;

import com.chatbot.saas.entity.StaffUser;

/** A dashboard user signed in with email + password (see {@link StaffSessionFilter}). */
public record StaffPrincipal(Long userId, Long businessId, StaffUser.Role role) {
}
