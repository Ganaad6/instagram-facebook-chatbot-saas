package com.chatbot.saas.util;

import org.apache.commons.codec.digest.HmacAlgorithms;
import org.apache.commons.codec.digest.HmacUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SignatureValidatorTest {

    private static final String SECRET = "my_webhook_secret";

    @Test
    void validSignatureIsAccepted() {
        String payload = "{\"field\":\"value\"}";
        String signature = "sha256=" + new HmacUtils(HmacAlgorithms.HMAC_SHA_256, SECRET).hmacHex(payload);

        assertTrue(SignatureValidator.validateSignature(payload, signature, SECRET));
    }

    @Test
    void tamperedPayloadIsRejected() {
        String originalPayload = "{\"field\":\"value\"}";
        String signature = "sha256=" + new HmacUtils(HmacAlgorithms.HMAC_SHA_256, SECRET).hmacHex(originalPayload);

        String tamperedPayload = "{\"field\":\"tampered\"}";
        assertFalse(SignatureValidator.validateSignature(tamperedPayload, signature, SECRET));
    }

    @Test
    void wrongSecretIsRejected() {
        String payload = "{\"field\":\"value\"}";
        String signature = "sha256=" + new HmacUtils(HmacAlgorithms.HMAC_SHA_256, "wrong_secret").hmacHex(payload);

        assertFalse(SignatureValidator.validateSignature(payload, signature, SECRET));
    }

    @Test
    void missingSignatureIsRejected() {
        assertFalse(SignatureValidator.validateSignature("payload", null, SECRET));
    }

    @Test
    void malformedSignatureIsRejected() {
        assertFalse(SignatureValidator.validateSignature("payload", "not-a-valid-signature", SECRET));
    }
}
