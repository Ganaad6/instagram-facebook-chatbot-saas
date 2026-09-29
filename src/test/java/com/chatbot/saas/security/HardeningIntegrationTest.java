package com.chatbot.saas.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** Production hardening: SSRF-safe webhook URLs, error responses, security headers. */
@SpringBootTest
@AutoConfigureEmbeddedDatabase(provider = AutoConfigureEmbeddedDatabase.DatabaseProvider.ZONKY)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HardeningIntegrationTest {

    @Autowired private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private long businessId;
    private String apiKey;

    @BeforeEach
    void shop() throws Exception {
        JsonNode shop = objectMapper.readTree(mockMvc.perform(post("/api/businesses/register").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", "Shop", "email", "h" + System.nanoTime() + "@example.com"))))
                .andReturn().getResponse().getContentAsString());
        businessId = shop.get("id").asLong();
        apiKey = shop.get("apiKey").asText();
    }

    private MockHttpServletResponse setWebhook(String url) throws Exception {
        return mockMvc.perform(post("/api/businesses/" + businessId + "/notifications/webhook-url")
                .header("X-API-Key", apiKey).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(java.util.Collections.singletonMap("webhookUrl", url))))
                .andReturn().getResponse();
    }

    @Test
    void notificationWebhookMustBeAPublicHttpsUrl() throws Exception {
        for (String url : new String[]{"http://93.184.215.14/hook", "https://localhost:8080/api/admin/businesses",
                "https://127.0.0.1/", "https://169.254.169.254/latest/meta-data/", "https://10.0.0.5/hook", "https://[::1]/"}) {
            assertEquals(400, setWebhook(url).getStatus(), url);
        }
        assertEquals(200, setWebhook("https://93.184.215.14/hooks/orders").getStatus());
        assertEquals(200, setWebhook("").getStatus(), "clearing it is allowed");
    }

    @Test
    void badInputIs400WithoutInternals() throws Exception {
        MockHttpServletResponse malformed = mockMvc.perform(put("/api/businesses/" + businessId + "/orders/1/status")
                .header("X-API-Key", apiKey).contentType(MediaType.APPLICATION_JSON).content("{not json")).andReturn().getResponse();
        assertEquals(400, malformed.getStatus());
        assertFalse(malformed.getContentAsString().contains("JSON parse"), malformed.getContentAsString());

        MockHttpServletResponse badStatus = mockMvc.perform(get("/api/businesses/" + businessId + "/orders")
                .param("status", "SHIPPED").header("X-API-Key", apiKey)).andReturn().getResponse();
        assertEquals(400, badStatus.getStatus());
        assertFalse(badStatus.getContentAsString().contains("com.chatbot"), badStatus.getContentAsString());

        assertEquals(400, mockMvc.perform(get("/api/businesses/" + businessId + "/orders/abc")
                .header("X-API-Key", apiKey)).andReturn().getResponse().getStatus());
    }

    @Test
    void responsesCarrySecurityHeaders() throws Exception {
        MockHttpServletResponse page = mockMvc.perform(get("/login")).andReturn().getResponse();

        assertTrue(page.getHeader("Content-Security-Policy").contains("frame-ancestors 'none'"));
        assertTrue(page.getHeader("Content-Security-Policy").contains("script-src 'self'"));
        assertEquals("DENY", page.getHeader("X-Frame-Options"));
        assertEquals("nosniff", page.getHeader("X-Content-Type-Options"));
        assertEquals("strict-origin-when-cross-origin", page.getHeader("Referrer-Policy"));
        assertNotNull(page.getHeader("Permissions-Policy"));

        MockHttpServletResponse secure = mockMvc.perform(get("/login").secure(true)).andReturn().getResponse();
        assertTrue(secure.getHeader("Strict-Transport-Security").contains("max-age=31536000"));
    }

    @Test
    void errorMessagesStayReadable() throws Exception {
        MockHttpServletResponse notFound = mockMvc.perform(get("/api/businesses/" + businessId + "/orders/999999")
                .header("X-API-Key", apiKey)).andReturn().getResponse();
        assertEquals(404, notFound.getStatus());
        assertTrue(objectMapper.readTree(notFound.getContentAsString(StandardCharsets.UTF_8)).has("error"));
    }
}
