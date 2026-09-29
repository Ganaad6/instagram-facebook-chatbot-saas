package com.chatbot.saas.security;

import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** SIGNUP_ENABLED=false: shops can only be onboarded by the admin. */
@SpringBootTest(properties = "auth.signup-enabled=false")
@AutoConfigureEmbeddedDatabase(provider = AutoConfigureEmbeddedDatabase.DatabaseProvider.ZONKY)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SignupDisabledIntegrationTest {

    @Autowired private MockMvc mockMvc;

    @Test
    void apiRegistrationIsClosed() throws Exception {
        assertEquals(400, mockMvc.perform(post("/api/businesses/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"X\",\"email\":\"x@example.com\"}")).andReturn().getResponse().getStatus());
    }
}
