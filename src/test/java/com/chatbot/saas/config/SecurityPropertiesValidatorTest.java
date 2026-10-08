package com.chatbot.saas.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class SecurityPropertiesValidatorTest {

    private SecurityPropertiesValidator validator;

    @BeforeEach
    void validProductionConfig() {
        validator = new SecurityPropertiesValidator();
        ReflectionTestUtils.setField(validator, "encryptionSecretKey", "a-unique-32-byte-production-key!");
        ReflectionTestUtils.setField(validator, "webhookVerifyToken", "unique-verify-token");
        ReflectionTestUtils.setField(validator, "oauthStateSecret", "unique-state-secret");
        ReflectionTestUtils.setField(validator, "adminPassword", "a-strong-admin-password");
        ReflectionTestUtils.setField(validator, "corsAllowedOrigins", "https://shop.example");
        ReflectionTestUtils.setField(validator, "databasePassword", "a-strong-db-password");
        ReflectionTestUtils.setField(validator, "metaAppId", "123");
        ReflectionTestUtils.setField(validator, "metaAppSecret", "secret");
        ReflectionTestUtils.setField(validator, "oauthRedirectUri", "https://api.example/api/auth/meta/callback");
        ReflectionTestUtils.setField(validator, "qpayApiUrl", "https://merchant.qpay.mn/v2");
        ReflectionTestUtils.setField(validator, "legalOperatorName", "Shop Platform LLC");
        ReflectionTestUtils.setField(validator, "legalContactEmail", "privacy@shop.example");
    }

    @Test
    void validConfigurationStarts() {
        assertDoesNotThrow(validator::validate);
    }

    @Test
    void defaultDatabasePasswordIsRejected() {
        ReflectionTestUtils.setField(validator, "databasePassword", "password");

        IllegalStateException e = assertThrows(IllegalStateException.class, validator::validate);
        assertTrue(e.getMessage().contains("DATABASE_PASSWORD"));
    }

    @Test
    void missingMetaSecretIsRejected() {
        ReflectionTestUtils.setField(validator, "metaAppSecret", "");

        IllegalStateException e = assertThrows(IllegalStateException.class, validator::validate);
        assertTrue(e.getMessage().contains("META_APP_SECRET"));
    }

    @Test
    void plainHttpBaseUrlIsRejected() {
        ReflectionTestUtils.setField(validator, "oauthRedirectUri", "http://api.example/api/auth/meta/callback");

        IllegalStateException e = assertThrows(IllegalStateException.class, validator::validate);
        assertTrue(e.getMessage().contains("BASE_URL"));
    }

    @Test
    void missingLegalContactIsRejected() {
        ReflectionTestUtils.setField(validator, "legalContactEmail", "");

        IllegalStateException e = assertThrows(IllegalStateException.class, validator::validate);
        assertTrue(e.getMessage().contains("LEGAL_CONTACT_EMAIL"));
    }

    @Test
    void qpaySandboxIsRejected() {
        ReflectionTestUtils.setField(validator, "qpayApiUrl", "https://merchant-sandbox.qpay.mn/v2");

        IllegalStateException e = assertThrows(IllegalStateException.class, validator::validate);
        assertTrue(e.getMessage().contains("QPAY_API_URL"));
    }

    @Test
    void valuesCopiedFromTheEnvTemplateAreRejected() {
        ReflectionTestUtils.setField(validator, "encryptionSecretKey", "set_32_unique_characters_here___");
        ReflectionTestUtils.setField(validator, "adminPassword", "set_a_strong_unique_password_here");
        ReflectionTestUtils.setField(validator, "metaAppId", "your_meta_app_id");

        IllegalStateException e = assertThrows(IllegalStateException.class, validator::validate);
        assertTrue(e.getMessage().contains("ENCRYPTION_SECRET_KEY"));
        assertTrue(e.getMessage().contains("ADMIN_PASSWORD"));
        assertTrue(e.getMessage().contains("META_APP_ID"));
    }

    @Test
    void stateSecretMustDifferFromEncryptionKey() {
        ReflectionTestUtils.setField(validator, "oauthStateSecret", "a-unique-32-byte-production-key!");

        IllegalStateException e = assertThrows(IllegalStateException.class, validator::validate);
        assertTrue(e.getMessage().contains("OAUTH_STATE_SECRET"));
    }
}
