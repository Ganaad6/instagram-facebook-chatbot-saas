package com.chatbot.saas.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.core.annotation.Order;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class RateLimitFilterTest {

    private final AtomicLong now = new AtomicLong(1_000_000);
    private final RateLimitFilter filter = new RateLimitFilter(now::get);

    private int send(String method, String path, String remoteAddr, String forwardedFor) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRemoteAddr(remoteAddr);
        if (forwardedFor != null) {
            request.addHeader("X-Forwarded-For", forwardedFor);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response.getStatus();
    }

    @Test
    void registrationIsLimitedPerIpAndResetsAfterWindow() throws Exception {
        for (int i = 0; i < RateLimitFilter.REGISTER_LIMIT_PER_MINUTE; i++) {
            assertEquals(200, send("POST", "/api/businesses/register", "1.2.3.4", null));
        }
        assertEquals(429, send("POST", "/api/businesses/register", "1.2.3.4", null));
        assertEquals(200, send("POST", "/api/businesses/register", "5.6.7.8", null), "other IPs unaffected");

        now.addAndGet(RateLimitFilter.WINDOW_MILLIS + 1);
        assertEquals(200, send("POST", "/api/businesses/register", "1.2.3.4", null));
    }

    @Test
    void spoofedForwardedForHeaderDoesNotBypassTheLimit() throws Exception {
        for (int i = 0; i < RateLimitFilter.ADMIN_LIMIT_PER_MINUTE; i++) {
            send("GET", "/api/admin/businesses", "1.2.3.4", "10.0.0." + i);
        }
        assertEquals(429, send("GET", "/api/admin/businesses", "1.2.3.4", "10.0.0.250"));
    }

    @Test
    void expiredWindowsArePurgedSoMemoryStaysBounded() throws Exception {
        for (int i = 0; i <= RateLimitFilter.SWEEP_THRESHOLD; i++) {
            send("POST", "/webhook", "ip-" + i, null);
        }
        now.addAndGet(RateLimitFilter.WINDOW_MILLIS + 1);
        send("POST", "/webhook", "fresh", null);

        assertEquals(1, filter.trackedClientCount());
    }

    @Test
    void dashboardSignInAttemptsShareOneLimitPerIp() throws Exception {
        for (int i = 0; i < RateLimitFilter.AUTH_LIMIT_PER_MINUTE; i++) {
            String path = i % 2 == 0 ? "/api/auth/login" : "/api/auth/links/accept";
            assertEquals(200, send("POST", path, "1.2.3.4", null));
        }
        assertEquals(429, send("POST", "/api/auth/signup", "1.2.3.4", null));
        assertEquals(200, send("POST", "/api/auth/logout", "1.2.3.4", null), "signing out is never blocked");
    }

    @Test
    void authenticatedApiIsNotLimitedHere() throws Exception {
        for (int i = 0; i < 1000; i++) {
            assertEquals(200, send("GET", "/api/customers", "1.2.3.4", null));
        }
    }

    @Test
    void runsBeforeSpringSecuritySoRejectedLoginsAreCounted() {
        Order order = RateLimitFilter.class.getAnnotation(Order.class);
        assertNotNull(order);
        assertTrue(order.value() < SecurityProperties.DEFAULT_FILTER_ORDER);
    }
}
