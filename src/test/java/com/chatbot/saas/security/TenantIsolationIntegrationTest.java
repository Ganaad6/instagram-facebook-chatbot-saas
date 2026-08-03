package com.chatbot.saas.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end regression test for the multi-tenant IDOR fix: a business's API key must only
 * ever grant access to that business's own resources, and a suspended business must be
 * locked out entirely.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TenantIsolationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private long businessAId;
    private String businessAApiKey;
    private long businessBId;
    private String businessBApiKey;

    @BeforeEach
    void registerTwoBusinesses() throws Exception {
        JsonNode businessA = register("Business A", "business-a-" + System.nanoTime() + "@example.com");
        businessAId = businessA.get("id").asLong();
        businessAApiKey = businessA.get("apiKey").asText();

        JsonNode businessB = register("Business B", "business-b-" + System.nanoTime() + "@example.com");
        businessBId = businessB.get("id").asLong();
        businessBApiKey = businessB.get("apiKey").asText();
    }

    private JsonNode register(String name, String email) throws Exception {
        String body = objectMapper.writeValueAsString(java.util.Map.of("name", name, "email", email));
        String response = mockMvc.perform(post("/api/businesses/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response);
    }

    @Test
    void requestWithoutApiKeyIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/customers").param("businessId", String.valueOf(businessAId)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void requestWithInvalidApiKeyIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/customers")
                        .param("businessId", String.valueOf(businessAId))
                        .header("X-API-Key", "sk_live_not_a_real_key"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void ownApiKeyGrantsAccessToOwnBusiness() throws Exception {
        mockMvc.perform(get("/api/customers")
                        .param("businessId", String.valueOf(businessAId))
                        .header("X-API-Key", businessAApiKey))
                .andExpect(status().isOk());
    }

    @Test
    void otherBusinessApiKeyCannotAccessThisBusinessResources() throws Exception {
        mockMvc.perform(get("/api/customers")
                        .param("businessId", String.valueOf(businessBId))
                        .header("X-API-Key", businessAApiKey))
                .andExpect(status().isForbidden());
    }

    @Test
    void suspendedBusinessApiKeyIsRejected() throws Exception {
        mockMvc.perform(post("/api/admin/businesses/{id}/suspend", businessAId)
                        .with(SecurityMockMvcRequestPostProcessors.httpBasic("admin", "test_admin_password")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/customers")
                        .param("businessId", String.valueOf(businessAId))
                        .header("X-API-Key", businessAApiKey))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminEndpointsRequireAdminCredentials() throws Exception {
        mockMvc.perform(post("/api/admin/businesses/{id}/suspend", businessAId))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/admin/businesses/{id}/suspend", businessAId)
                        .header("X-API-Key", businessAApiKey))
                .andExpect(status().isUnauthorized());
    }
}
