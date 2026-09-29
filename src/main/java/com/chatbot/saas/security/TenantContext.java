package com.chatbot.saas.security;

import com.chatbot.saas.exception.TenantAccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Ties the authenticated business - from an API key ({@link ApiKeyAuthenticationFilter}) or a
 * dashboard login ({@link StaffSessionFilter}) - to the business id a request is trying to
 * operate on, closing the IDOR gap left by endpoints that take a businessId directly from the
 * client.
 */
@Component
public class TenantContext {

    public Long currentBusinessId() {
        Object principal = principal();
        if (principal instanceof Long businessId) {
            return businessId;
        }
        if (principal instanceof StaffPrincipal staff) {
            return staff.businessId();
        }
        throw new TenantAccessDeniedException();
    }

    public void assertAccess(Long businessId) {
        if (businessId == null || !businessId.equals(currentBusinessId())) {
            throw new TenantAccessDeniedException();
        }
    }

    /**
     * Like {@link #assertAccess} but for shop settings, connections and staff management:
     * the shop's owner or its API key, not ordinary staff.
     */
    public void assertOwner(Long businessId) {
        assertAccess(businessId);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean owner = authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_OWNER".equals(a.getAuthority()));
        if (!owner) {
            throw new TenantAccessDeniedException();
        }
    }

    /** The signed-in dashboard user, if the request came from one (not from an API key). */
    public Optional<StaffPrincipal> currentStaff() {
        return principal() instanceof StaffPrincipal staff ? Optional.of(staff) : Optional.empty();
    }

    private Object principal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null ? authentication.getPrincipal() : null;
    }
}
