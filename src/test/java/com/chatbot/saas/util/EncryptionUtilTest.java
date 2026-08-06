package com.chatbot.saas.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EncryptionUtilTest {

    private final EncryptionUtil encryptionUtil = new EncryptionUtil("0123456789abcdef0123456789abcdef");

    @Test
    void encryptThenDecryptRoundTripsToOriginalValue() {
        String plaintext = "EAABsomeMetaLongLivedAccessToken123";
        String ciphertext = encryptionUtil.encrypt(plaintext);

        assertNotEquals(plaintext, ciphertext);
        assertEquals(plaintext, encryptionUtil.decrypt(ciphertext));
    }

    @Test
    void sameInputEncryptsDifferentlyEachTime() {
        String plaintext = "same-token";
        String first = encryptionUtil.encrypt(plaintext);
        String second = encryptionUtil.encrypt(plaintext);

        assertNotEquals(first, second, "IV should be randomized per encryption");
    }

    @Test
    void tamperedCiphertextFailsToDecrypt() {
        String ciphertext = encryptionUtil.encrypt("some-token");
        byte[] bytes = java.util.Base64.getDecoder().decode(ciphertext);
        bytes[bytes.length - 1] ^= 0x01;
        String tampered = java.util.Base64.getEncoder().encodeToString(bytes);

        assertThrows(RuntimeException.class, () -> encryptionUtil.decrypt(tampered));
    }

    @Test
    void rejectsInvalidKeyLength() {
        assertThrows(IllegalArgumentException.class, () -> new EncryptionUtil("too-short"));
    }
}
