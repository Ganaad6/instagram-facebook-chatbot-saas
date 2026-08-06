package com.chatbot.saas.service;

import org.apache.commons.codec.digest.HmacAlgorithms;
import org.apache.commons.codec.digest.HmacUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OAuthStateServiceTest {

    private static final String SECRET = "oauth_state_test_secret";

    private OAuthStateService oAuthStateService;

    @BeforeEach
    void setUp() {
        oAuthStateService = new OAuthStateService();
        ReflectionTestUtils.setField(oAuthStateService, "secret", SECRET);
    }

    @Test
    void validStateRoundTripsToOriginalBusinessId() {
        String state = oAuthStateService.createState(42L);

        assertEquals(42L, oAuthStateService.parseAndValidate(state));
    }

    @Test
    void tamperedBusinessIdIsRejected() {
        long expiresAt = System.currentTimeMillis() / 1000 + 900;
        String tamperedPayload = "999." + expiresAt;
        String signatureForDifferentBusiness = "1." + expiresAt;
        String signature = new HmacUtils(HmacAlgorithms.HMAC_SHA_256, SECRET).hmacHex(signatureForDifferentBusiness);
        String forgedState = Base64.getUrlEncoder().withoutPadding()
                .encodeToString((tamperedPayload + "." + signature).getBytes(StandardCharsets.UTF_8));

        assertThrows(IllegalArgumentException.class, () -> oAuthStateService.parseAndValidate(forgedState));
    }

    @Test
    void expiredStateIsRejected() {
        long expiredAt = System.currentTimeMillis() / 1000 - 60;
        String payload = "42." + expiredAt;
        String signature = new HmacUtils(HmacAlgorithms.HMAC_SHA_256, SECRET).hmacHex(payload);
        String expiredState = Base64.getUrlEncoder().withoutPadding()
                .encodeToString((payload + "." + signature).getBytes(StandardCharsets.UTF_8));

        assertThrows(IllegalArgumentException.class, () -> oAuthStateService.parseAndValidate(expiredState));
    }

    @Test
    void garbageStateIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> oAuthStateService.parseAndValidate("not-a-valid-state"));
    }
}
