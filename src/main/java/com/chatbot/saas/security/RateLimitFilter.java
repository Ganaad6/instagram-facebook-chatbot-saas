package com.chatbot.saas.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simple in-memory, per-IP, fixed-window rate limiter for the two unauthenticated endpoints
 * (business registration and the Meta webhook). In-memory means this only works correctly for
 * a single app instance - acceptable for now since deployment is single-instance; would need a
 * shared store (e.g. Redis) if this is ever scaled horizontally.
 *
 * Disabled under the "test" profile: MockMvc-driven integration tests all originate from the
 * same simulated client, so a real fixed-window limiter would throttle unrelated test methods
 * rather than anything resembling abuse.
 */
@Component
@Profile("!test")
public class RateLimitFilter extends OncePerRequestFilter {

    private static final long WINDOW_MILLIS = 60_000;
    private static final int REGISTER_LIMIT_PER_MINUTE = 10;
    private static final int WEBHOOK_LIMIT_PER_MINUTE = 600;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ConcurrentHashMap<String, Window> registerWindows = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Window> webhookWindows = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        String clientIp = clientIp(request);

        if ("POST".equalsIgnoreCase(request.getMethod()) && path.startsWith("/api/businesses/register")) {
            if (isRateLimited(registerWindows, clientIp, REGISTER_LIMIT_PER_MINUTE)) {
                writeTooManyRequests(response);
                return;
            }
        } else if (path.startsWith("/webhook")) {
            if (isRateLimited(webhookWindows, clientIp, WEBHOOK_LIMIT_PER_MINUTE)) {
                writeTooManyRequests(response);
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean isRateLimited(ConcurrentHashMap<String, Window> windows, String key, int limit) {
        long now = System.currentTimeMillis();
        Window window = windows.compute(key, (k, existing) -> {
            if (existing == null || now - existing.windowStart > WINDOW_MILLIS) {
                return new Window(now);
            }
            return existing;
        });
        return window.count.incrementAndGet() > limit;
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private void writeTooManyRequests(HttpServletResponse response) throws IOException {
        response.setStatus(429);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(OBJECT_MAPPER.writeValueAsString(
                Map.of("error", "Too many requests", "status", 429)));
    }

    private static final class Window {
        final long windowStart;
        final AtomicInteger count = new AtomicInteger(0);

        Window(long windowStart) {
            this.windowStart = windowStart;
        }
    }
}
