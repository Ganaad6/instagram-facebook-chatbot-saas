package com.chatbot.saas.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;

import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Dashboard sign-in end to end over HTTP: session cookies (Spring Session in Postgres), CSRF,
 * roles, and sessions ending when access is withdrawn.
 */
@SpringBootTest
@AutoConfigureEmbeddedDatabase(provider = AutoConfigureEmbeddedDatabase.DatabaseProvider.ZONKY)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DashboardAuthIntegrationTest {

    private static final String PASSWORD = "correct horse battery";

    @Autowired private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Keeps cookies between requests and echoes the CSRF cookie in the header, like the dashboard does. */
    private class Browser {
        private final Map<String, String> cookies = new LinkedHashMap<>();
        boolean sendCsrfHeader = true;

        MockHttpServletResponse send(HttpMethod method, String path, Object body) throws Exception {
            if (!cookies.containsKey("XSRF-TOKEN")) {
                store(mockMvc.perform(withCookies(MockMvcRequestBuilders.get("/api/auth/csrf"))).andReturn().getResponse());
            }
            MockHttpServletRequestBuilder request = withCookies(MockMvcRequestBuilders.request(method, path));
            if (sendCsrfHeader && cookies.containsKey("XSRF-TOKEN")) {
                request.header("X-XSRF-TOKEN", cookies.get("XSRF-TOKEN"));
            }
            if (body != null) {
                request.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body));
            }
            MockHttpServletResponse response = mockMvc.perform(request).andReturn().getResponse();
            store(response);
            return response;
        }

        int status(HttpMethod method, String path, Object body) throws Exception {
            return send(method, path, body).getStatus();
        }

        JsonNode json(HttpMethod method, String path, Object body, int expectedStatus) throws Exception {
            MockHttpServletResponse response = send(method, path, body);
            assertEquals(expectedStatus, response.getStatus(), response.getContentAsString(StandardCharsets.UTF_8));
            String content = response.getContentAsString(StandardCharsets.UTF_8);
            return content.isEmpty() ? null : objectMapper.readTree(content);
        }

        private MockHttpServletRequestBuilder withCookies(MockHttpServletRequestBuilder request) {
            if (!cookies.isEmpty()) {
                request.cookie(cookies.entrySet().stream().map(e -> new Cookie(e.getKey(), e.getValue())).toArray(Cookie[]::new));
            }
            return request;
        }

        private void store(MockHttpServletResponse response) {
            for (String header : response.getHeaders("Set-Cookie")) {
                String[] nameValue = header.split(";", 2)[0].split("=", 2);
                boolean expired = header.contains("Max-Age=0") || nameValue[1].isEmpty();
                if (expired) {
                    cookies.remove(nameValue[0]);
                } else {
                    cookies.put(nameValue[0], nameValue[1]);
                }
            }
        }
    }

    private static String uniqueEmail(String who) {
        return who + "-" + System.nanoTime() + "@example.com";
    }

    /** A new shop signed up from the dashboard; returns the owner's browser and the shop id. */
    private record Shop(Browser owner, long businessId, String ownerEmail) {
    }

    private Shop signup() throws Exception {
        Browser owner = new Browser();
        String email = uniqueEmail("owner");
        JsonNode me = owner.json(HttpMethod.POST, "/api/auth/signup", Map.of(
                "businessName", "Цэцэг дэлгүүр", "name", "Болд", "email", email, "password", PASSWORD), 201);
        return new Shop(owner, me.get("business").get("id").asLong(), email);
    }

    private Browser acceptInvite(String inviteUrl) throws Exception {
        String token = inviteUrl.substring(inviteUrl.lastIndexOf('/') + 1);
        Browser browser = new Browser();
        browser.json(HttpMethod.POST, "/api/auth/links/accept", Map.of("token", token, "name", "Сараа", "password", PASSWORD), 200);
        return browser;
    }

    @Test
    void signedUpOwnerUsesTheApiThroughTheSession() throws Exception {
        Shop shop = signup();

        JsonNode me = shop.owner().json(HttpMethod.GET, "/api/auth/me", null, 200);
        assertEquals("OWNER", me.get("user").get("role").asText());
        assertEquals("Цэцэг дэлгүүр", me.get("business").get("name").asText());
        assertEquals(200, shop.owner().status(HttpMethod.GET, "/api/businesses/" + shop.businessId() + "/orders", null));
        assertEquals(201, shop.owner().status(HttpMethod.POST, "/api/businesses/" + shop.businessId() + "/categories",
                Map.of("name", "Цэцэг")));

        assertEquals(204, shop.owner().status(HttpMethod.POST, "/api/auth/logout", null));
        assertEquals(401, shop.owner().status(HttpMethod.GET, "/api/auth/me", null));
    }

    @Test
    void sessionAndCsrfCookiesStayPutBetweenRequests() throws Exception {
        Shop shop = signup();
        Browser owner = shop.owner();
        owner.status(HttpMethod.GET, "/api/auth/me", null);
        Map<String, String> before = Map.copyOf(owner.cookies);

        owner.status(HttpMethod.GET, "/api/businesses/" + shop.businessId() + "/orders", null);
        assertEquals(201, owner.status(HttpMethod.POST, "/api/businesses/" + shop.businessId() + "/categories",
                Map.of("name", "Цэцэг")));
        assertEquals(201, owner.status(HttpMethod.POST, "/api/businesses/" + shop.businessId() + "/categories",
                Map.of("name", "Бэлэг")));

        assertEquals(before, owner.cookies, "session id and CSRF token must not change on ordinary requests");
    }

    @Test
    void cookieAuthenticatedWritesNeedTheCsrfHeader() throws Exception {
        Shop shop = signup();
        shop.owner().sendCsrfHeader = false;

        assertEquals(403, shop.owner().status(HttpMethod.POST, "/api/businesses/" + shop.businessId() + "/categories",
                Map.of("name", "Цэцэг")));
        // Reads are fine
        assertEquals(200, shop.owner().status(HttpMethod.GET, "/api/businesses/" + shop.businessId() + "/categories", null));

        // A foreign site can't sign a visitor in either
        Browser visitor = new Browser();
        visitor.sendCsrfHeader = false;
        assertEquals(403, visitor.status(HttpMethod.POST, "/api/auth/login",
                Map.of("email", shop.ownerEmail(), "password", PASSWORD)));
    }

    @Test
    void wrongPasswordsLockTheAccountForAWhile() throws Exception {
        Shop shop = signup();
        Browser attacker = new Browser();
        for (int i = 0; i < 10; i++) {
            assertEquals(401, attacker.status(HttpMethod.POST, "/api/auth/login",
                    Map.of("email", shop.ownerEmail(), "password", "guess-" + i)));
        }

        assertEquals(429, attacker.status(HttpMethod.POST, "/api/auth/login",
                Map.of("email", shop.ownerEmail(), "password", PASSWORD)));
    }

    @Test
    void unknownEmailAndWrongPasswordLookTheSame() throws Exception {
        Shop shop = signup();
        Browser browser = new Browser();

        MockHttpServletResponse unknown = browser.send(HttpMethod.POST, "/api/auth/login",
                Map.of("email", uniqueEmail("nobody"), "password", PASSWORD));
        MockHttpServletResponse wrong = browser.send(HttpMethod.POST, "/api/auth/login",
                Map.of("email", shop.ownerEmail(), "password", "wrong password"));

        assertEquals(401, unknown.getStatus());
        assertEquals(unknown.getContentAsString(StandardCharsets.UTF_8), wrong.getContentAsString(StandardCharsets.UTF_8));
    }

    @Test
    void staffHandleOrdersButOnlyTheOwnerChangesSettings() throws Exception {
        Shop shop = signup();
        String base = "/api/businesses/" + shop.businessId();
        JsonNode invitation = shop.owner().json(HttpMethod.POST, base + "/staff",
                Map.of("email", uniqueEmail("staff"), "name", "Сараа", "role", "STAFF"), 201);
        assertTrue(invitation.get("user").get("invitePending").asBoolean());

        Browser staff = acceptInvite(invitation.get("link").get("url").asText());

        assertEquals("STAFF", staff.json(HttpMethod.GET, "/api/auth/me", null, 200).get("user").get("role").asText());
        assertEquals(200, staff.status(HttpMethod.GET, base + "/orders", null));
        assertEquals(201, staff.status(HttpMethod.POST, base + "/categories", Map.of("name", "Бэлэг")));
        assertEquals(403, staff.status(HttpMethod.GET, base + "/staff", null));
        assertEquals(403, staff.status(HttpMethod.PUT, base + "/payments/qpay",
                Map.of("username", "u", "password", "p", "invoiceCode", "c")));
        assertEquals(403, staff.status(HttpMethod.POST, base + "/api-key", null));
        assertEquals(403, staff.status(HttpMethod.GET, "/api/auth/meta/authorize?businessId=" + shop.businessId(), null));
    }

    @Test
    void inviteLinksWorkOnce() throws Exception {
        Shop shop = signup();
        String url = shop.owner().json(HttpMethod.POST, "/api/businesses/" + shop.businessId() + "/staff",
                Map.of("email", uniqueEmail("staff"), "role", "STAFF"), 201).get("link").get("url").asText();
        String token = url.substring(url.lastIndexOf('/') + 1);
        assertTrue(url.startsWith("http://localhost:8080/invite/"), url);

        Browser browser = new Browser();
        JsonNode info = browser.json(HttpMethod.GET, "/api/auth/links/" + token, null, 200);
        assertEquals("Цэцэг дэлгүүр", info.get("businessName").asText());
        assertEquals("INVITE", info.get("purpose").asText());

        acceptInvite(url);
        assertEquals(401, new Browser().status(HttpMethod.POST, "/api/auth/links/accept",
                Map.of("token", token, "password", "another password")));
    }

    @Test
    void sessionsEndWhenStaffAreDeactivatedOrTheShopIsSuspended() throws Exception {
        Shop shop = signup();
        String base = "/api/businesses/" + shop.businessId();
        JsonNode invitation = shop.owner().json(HttpMethod.POST, base + "/staff",
                Map.of("email", uniqueEmail("staff"), "role", "STAFF"), 201);
        Browser staff = acceptInvite(invitation.get("link").get("url").asText());
        long staffId = invitation.get("user").get("id").asLong();

        shop.owner().json(HttpMethod.PUT, base + "/staff/" + staffId, Map.of("active", false), 200);
        assertEquals(401, staff.status(HttpMethod.GET, "/api/auth/me", null));

        mockMvc.perform(post("/api/admin/businesses/{id}/suspend", shop.businessId())
                .with(SecurityMockMvcRequestPostProcessors.httpBasic("admin", "test_admin_password")));
        assertEquals(401, shop.owner().status(HttpMethod.GET, "/api/auth/me", null));
        assertEquals(403, new Browser().status(HttpMethod.POST, "/api/auth/login",
                Map.of("email", shop.ownerEmail(), "password", PASSWORD)));
    }

    @Test
    void changingThePasswordSignsOutOtherSessions() throws Exception {
        Shop shop = signup();
        Browser laptop = shop.owner();
        Browser phone = new Browser();
        phone.json(HttpMethod.POST, "/api/auth/login", Map.of("email", shop.ownerEmail(), "password", PASSWORD), 200);

        laptop.json(HttpMethod.POST, "/api/auth/password",
                Map.of("currentPassword", PASSWORD, "newPassword", "a brand new password"), 204);

        assertEquals(200, laptop.status(HttpMethod.GET, "/api/auth/me", null));
        assertEquals(401, phone.status(HttpMethod.GET, "/api/auth/me", null));
        assertEquals(401, new Browser().status(HttpMethod.POST, "/api/auth/login",
                Map.of("email", shop.ownerEmail(), "password", PASSWORD)));
    }

    @Test
    void aSessionOnlyReachesItsOwnShop() throws Exception {
        Shop mine = signup();
        Shop other = signup();

        assertEquals(403, mine.owner().status(HttpMethod.GET, "/api/businesses/" + other.businessId() + "/orders", null));
        assertEquals(403, mine.owner().status(HttpMethod.GET, "/api/businesses/" + other.businessId() + "/staff", null));
    }

    @Test
    void theLastOwnerCannotLockThemselvesOut() throws Exception {
        Shop shop = signup();
        String base = "/api/businesses/" + shop.businessId();
        long ownerId = shop.owner().json(HttpMethod.GET, "/api/auth/me", null, 200).get("user").get("id").asLong();

        assertEquals(400, shop.owner().status(HttpMethod.PUT, base + "/staff/" + ownerId, Map.of("role", "STAFF")));
        assertEquals(400, shop.owner().status(HttpMethod.DELETE, base + "/staff/" + ownerId, null));
    }

    @Test
    void theOwnerCanIssueAnApiKeyForIntegrations() throws Exception {
        Shop shop = signup();

        String apiKey = shop.owner().json(HttpMethod.POST, "/api/businesses/" + shop.businessId() + "/api-key", null, 200)
                .get("apiKey").asText();

        assertEquals(200, mockMvc.perform(MockMvcRequestBuilders.get("/api/businesses/" + shop.businessId() + "/orders")
                .header("X-API-Key", apiKey)).andReturn().getResponse().getStatus());
    }

    @Test
    void adminCanOnboardTheOwnerOfAnApiRegisteredShop() throws Exception {
        String email = uniqueEmail("api-shop");
        String registered = mockMvc.perform(post("/api/businesses/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "API shop", "email", email))))
                .andReturn().getResponse().getContentAsString();
        long businessId = objectMapper.readTree(registered).get("id").asLong();

        String invitation = mockMvc.perform(post("/api/admin/businesses/{id}/owner-invite", businessId)
                        .with(SecurityMockMvcRequestPostProcessors.httpBasic("admin", "test_admin_password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "name", "Owner"))))
                .andReturn().getResponse().getContentAsString();
        Browser owner = acceptInvite(objectMapper.readTree(invitation).get("link").get("url").asText());

        JsonNode me = owner.json(HttpMethod.GET, "/api/auth/me", null, 200);
        assertEquals(businessId, me.get("business").get("id").asLong());
        assertEquals("OWNER", me.get("user").get("role").asText());
    }

    @Test
    void signupRejectsWeakPasswordsAndTakenEmails() throws Exception {
        Shop shop = signup();

        assertEquals(400, new Browser().status(HttpMethod.POST, "/api/auth/signup", Map.of(
                "businessName", "X", "name", "Y", "email", uniqueEmail("weak"), "password", "short")));
        assertEquals(400, new Browser().status(HttpMethod.POST, "/api/auth/signup", Map.of(
                "businessName", "X", "name", "Y", "email", shop.ownerEmail().toUpperCase(), "password", PASSWORD)));
    }
}
