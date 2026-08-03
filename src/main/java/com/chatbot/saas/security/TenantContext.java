package com.chatbot.saas.security;

import com.chatbot.saas.exception.TenantAccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Ties the authenticated business (set by {@link ApiKeyAuthenticationFilter}) to the
 * business id a request is trying to operate on, closing the IDOR gap left by endpoints
 * that take a businessId directly from the client.
 */
@Component
public class TenantContext {

    public Long currentBusinessId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Long)) {
            throw new TenantAccessDeniedException();
        }
        return (Long) authentication.getPrincipal();
    }

    public void assertAccess(Long businessId) {
        if (businessId == null || !businessId.equals(currentBusinessId())) {
            throw new TenantAccessDeniedException();
        }
    }
}
