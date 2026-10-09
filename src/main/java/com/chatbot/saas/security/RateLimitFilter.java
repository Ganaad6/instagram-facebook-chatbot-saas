package com.chatbot.saas.security;

import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;

/**
 * Simple in-memory, per-IP, fixed-window rate limiter for the endpoints reachable without an
 * API key: business registration, the Meta webhook, the admin endpoints (whose HTTP Basic
 * password would otherwise be open to brute force) and the dashboard sign-in endpoints (which
 * additionally lock an account after repeated wrong passwords, see StaffAuthService). In-memory means this only works correctly
 * for a single app instance - acceptable for now since deployment is single-instance; would
 * need a shared store (e.g. Redis) if this is ever scaled horizontally.
 *
 * The client IP is request.getRemoteAddr(), never a raw X-Forwarded-For header (which any
 * client can set). Behind a reverse proxy, server.forward-headers-strategy=native makes Tomcat
 * resolve the real client IP from X-Forwarded-For - but only when the request comes from a
 * trusted proxy (server.tomcat.remoteip.internal-proxies, private networks by default).
 *
 * Ordered before the Spring Security filter chain: otherwise a wrong admin password is
 * rejected with 401 before this filter ever counts the attempt, leaving brute force unlimited.
 *
 * Disabled under the "test" profile: MockMvc-driven integration tests all originate from the
 * same simulated client, so a real fixed-window limiter would throttle unrelated test methods
 * rather than anything resembling abuse.
 */
@Component
@Profile("!test")
@Order(SecurityFilterProperties.DEFAULT_FILTER_ORDER - 10)
public class RateLimitFilter extends OncePerRequestFilter {

    static final long WINDOW_MILLIS = 60_000;
    static final int REGISTER_LIMIT_PER_MINUTE = 10;
    static final int WEBHOOK_LIMIT_PER_MINUTE = 600;
    static final int ADMIN_LIMIT_PER_MINUTE = 30;
    static final int AUTH_LIMIT_PER_MINUTE = 20;
    /** Expired windows are purged once a map grows past this, bounding memory under IP churn. */
    static final int SWEEP_THRESHOLD = 10_000;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ConcurrentHashMap<String, Window> registerWindows = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Window> webhookWindows = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Window> adminWindows = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Window> authWindows = new ConcurrentHashMap<>();
    private final LongSupplier clock;

    public RateLimitFilter() {
        this(System::currentTimeMillis);
    }

    RateLimitFilter(LongSupplier clock) {
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        String clientIp = request.getRemoteAddr();

        boolean limited = false;
        if ("POST".equalsIgnoreCase(request.getMethod()) && path.startsWith("/api/businesses/register")) {
            limited = isRateLimited(registerWindows, clientIp, REGISTER_LIMIT_PER_MINUTE);
        } else if (path.startsWith("/webhook")) {
            limited = isRateLimited(webhookWindows, clientIp, WEBHOOK_LIMIT_PER_MINUTE);
        } else if (path.startsWith("/api/admin")) {
            limited = isRateLimited(adminWindows, clientIp, ADMIN_LIMIT_PER_MINUTE);
        } else if ("POST".equalsIgnoreCase(request.getMethod()) && isSignInPath(path)) {
            limited = isRateLimited(authWindows, clientIp, AUTH_LIMIT_PER_MINUTE);
        }

        if (limited) {
            writeTooManyRequests(response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    /** Password guessing and account creation; not logout or password change (already signed in). */
    private static boolean isSignInPath(String path) {
        return path.equals("/api/auth/login") || path.equals("/api/auth/signup") || path.equals("/api/auth/links/accept");
    }

    private boolean isRateLimited(ConcurrentHashMap<String, Window> windows, String key, int limit) {
        long now = clock.getAsLong();
        if (windows.size() > SWEEP_THRESHOLD) {
            windows.values().removeIf(w -> now - w.windowStart > WINDOW_MILLIS);
        }
        Window window = windows.compute(key, (k, existing) -> {
            if (existing == null || now - existing.windowStart > WINDOW_MILLIS) {
                return new Window(now);
            }
            return existing;
        });
        return window.count.incrementAndGet() > limit;
    }

    int trackedClientCount() {
        return registerWindows.size() + webhookWindows.size() + adminWindows.size() + authWindows.size();
    }

    private void writeTooManyRequests(HttpServletResponse response) throws IOException {
        response.setStatus(429);
        response.setHeader("Retry-After", String.valueOf(WINDOW_MILLIS / 1000));
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
