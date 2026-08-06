package com.chatbot.saas.security;

import com.chatbot.saas.entity.Business;
import com.chatbot.saas.service.BusinessService;
import com.chatbot.saas.util.ApiKeyGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Authenticates business-scoped API requests via the {@code X-API-Key} header. Runs ahead of
 * Spring Security's authorization checks, so it must exclude public/admin routes itself via
 * {@link #shouldNotFilter} - the {@code permitAll()}/{@code hasRole("ADMIN")} rules in
 * SecurityConfig only govern the later AuthorizationFilter, not this one.
 */
@RequiredArgsConstructor
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final String API_KEY_HEADER = "X-API-Key";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final BusinessService businessService;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path.startsWith("/webhook") || path.startsWith("/api/admin/") || path.startsWith("/actuator/")) {
            return true;
        }
        if ("POST".equalsIgnoreCase(request.getMethod()) && path.equals("/api/businesses/register")) {
            return true;
        }
        return "GET".equalsIgnoreCase(request.getMethod()) && path.equals("/api/auth/meta/callback");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        String apiKey = request.getHeader(API_KEY_HEADER);
        if (apiKey == null || apiKey.isBlank()) {
            writeError(response, 401, "Missing X-API-Key header");
            return;
        }

        Optional<Business> businessOpt = businessService.findByApiKeyHash(ApiKeyGenerator.hash(apiKey));
        if (businessOpt.isEmpty()) {
            writeError(response, 401, "Invalid API key");
            return;
        }

        Business business = businessOpt.get();
        if (business.getStatus() != Business.Status.ACTIVE) {
            writeError(response, 403, "Business is suspended");
            return;
        }

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                business.getId(), null, List.of(new SimpleGrantedAuthority("ROLE_BUSINESS")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        filterChain.doFilter(request, response);
    }

    private void writeError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(OBJECT_MAPPER.writeValueAsString(Map.of("error", message, "status", status)));
    }
}
