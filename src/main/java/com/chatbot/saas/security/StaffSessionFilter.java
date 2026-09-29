package com.chatbot.saas.security;

import com.chatbot.saas.entity.StaffUser;
import com.chatbot.saas.service.StaffAuthService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Authenticates dashboard requests from the login session. The session only holds the user id
 * and the user's session version; the user, their role and the shop's status are re-read on
 * every request, so deactivating a user, suspending the shop or changing a password takes
 * effect immediately rather than when the session expires.
 */
@RequiredArgsConstructor
public class StaffSessionFilter extends OncePerRequestFilter {

    public static final String USER_ID_ATTRIBUTE = "staff.userId";
    public static final String SESSION_VERSION_ATTRIBUTE = "staff.sessionVersion";

    private final StaffAuthService staffAuthService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        if (session != null && SecurityContextHolder.getContext().getAuthentication() == null
                && session.getAttribute(USER_ID_ATTRIBUTE) instanceof Long userId
                && session.getAttribute(SESSION_VERSION_ATTRIBUTE) instanceof Integer version) {
            staffAuthService.resolveSession(userId, version).ifPresentOrElse(
                    principal -> SecurityContextHolder.getContext().setAuthentication(authentication(principal)),
                    session::invalidate);
        }
        filterChain.doFilter(request, response);
    }

    static UsernamePasswordAuthenticationToken authentication(StaffPrincipal principal) {
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_BUSINESS"));
        if (principal.role() == StaffUser.Role.OWNER) {
            authorities.add(new SimpleGrantedAuthority("ROLE_OWNER"));
        }
        return new UsernamePasswordAuthenticationToken(principal, null, authorities);
    }
}
