package com.chatbot.saas.controller;

import com.chatbot.saas.entity.Business;
import com.chatbot.saas.repository.BusinessRepository;
import com.chatbot.saas.util.MetaSignedRequestTest;
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

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** Meta's deauthorize / data-deletion callbacks and the public pages App Review needs. */
@SpringBootTest(properties = {"legal.operator-name=Test Operator", "legal.contact-email=privacy@example.com",
        "app.base-url=https://shop.example/"})
@AutoConfigureEmbeddedDatabase(provider = AutoConfigureEmbeddedDatabase.DatabaseProvider.ZONKY)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MetaDataRequestIntegrationTest {

    private static final String APP_SECRET = "test_app_secret";

    @Autowired private MockMvc mockMvc;
    @Autowired private BusinessRepository businessRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private String metaUserId;
    private long businessId;

    @BeforeEach
    void connectedShop() throws Exception {
        JsonNode shop = objectMapper.readTree(mockMvc.perform(post("/api/businesses/register").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", "Shop", "email", "m" + System.nanoTime() + "@example.com"))))
                .andReturn().getResponse().getContentAsString());
        businessId = shop.get("id").asLong();
        metaUserId = "fb-" + System.nanoTime();
        Business business = businessRepository.findById(businessId).orElseThrow();
        business.setMetaUserId(metaUserId);
        business.setFacebookPageId("page-" + metaUserId);
        business.setInstagramAccountId("ig-" + metaUserId);
        business.setAccessToken("encrypted-token");
        businessRepository.save(business);
    }

    private MockHttpServletResponse callback(String path, String signedRequest) throws Exception {
        return mockMvc.perform(post("/webhook/meta/" + path).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("signed_request", signedRequest)).andReturn().getResponse();
    }

    private String signedFor(String userId, String secret) {
        return MetaSignedRequestTest.sign("{\"algorithm\":\"HMAC-SHA256\",\"issued_at\":1700000000,\"user_id\":\"" + userId + "\"}", secret);
    }

    private void assertDisconnected() {
        Business business = businessRepository.findById(businessId).orElseThrow();
        assertNull(business.getAccessToken());
        assertNull(business.getMetaUserId());
        assertNull(business.getFacebookPageId());
        assertNull(business.getInstagramAccountId());
        assertEquals("Shop", business.getName(), "the shop itself stays");
    }

    @Test
    void deauthorizeRemovesTheFacebookConnection() throws Exception {
        assertEquals(200, callback("deauthorize", signedFor(metaUserId, APP_SECRET)).getStatus());
        assertDisconnected();
    }

    @Test
    void dataDeletionRemovesTheConnectionAndReturnsAStatusLink() throws Exception {
        MockHttpServletResponse response = callback("data-deletion", signedFor(metaUserId, APP_SECRET));
        assertEquals(200, response.getStatus());
        JsonNode body = objectMapper.readTree(response.getContentAsString());
        String code = body.get("confirmation_code").asText();
        assertEquals("https://shop.example/data-deletion?code=" + code, body.get("url").asText());
        assertDisconnected();

        JsonNode status = objectMapper.readTree(mockMvc.perform(get("/api/public/data-deletion/" + code))
                .andReturn().getResponse().getContentAsString());
        assertEquals(code, status.get("confirmationCode").asText());
        assertFalse(status.get("completedAt").isNull());
        assertEquals(404, mockMvc.perform(get("/api/public/data-deletion/unknown")).andReturn().getResponse().getStatus());
    }

    @Test
    void unsignedOrForgedCallbacksAreRejected() throws Exception {
        assertEquals(403, callback("deauthorize", signedFor(metaUserId, "not-the-app-secret")).getStatus());
        assertEquals(403, callback("data-deletion", "garbage").getStatus());
        assertEquals(403, mockMvc.perform(post("/webhook/meta/data-deletion").contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andReturn().getResponse().getStatus());
        assertNotNull(businessRepository.findById(businessId).orElseThrow().getAccessToken());
    }

    @Test
    void legalInfoAndPagesArePublic() throws Exception {
        JsonNode legal = objectMapper.readTree(mockMvc.perform(get("/api/public/legal")).andReturn().getResponse().getContentAsString());
        assertEquals("Test Operator", legal.get("operatorName").asText());
        assertEquals("privacy@example.com", legal.get("contactEmail").asText());
        for (String page : new String[]{"/privacy", "/terms", "/data-deletion"}) {
            int status = mockMvc.perform(get(page)).andReturn().getResponse().getStatus();
            // 404 only when the frontend isn't built into static/ (backend-only build); never 401/403
            assertTrue(status == 200 || status == 404, page + " -> " + status);
        }
    }
}
