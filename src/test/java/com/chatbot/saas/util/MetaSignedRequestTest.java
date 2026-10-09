package com.chatbot.saas.util;

import org.apache.commons.codec.digest.HmacAlgorithms;
import org.apache.commons.codec.digest.HmacUtils;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MetaSignedRequestTest {

    private static final String SECRET = "app-secret";

    /** Builds a signed_request the way Meta does. */
    public static String sign(String payloadJson, String secret) {
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        String payload = encoder.encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));
        String signature = encoder.encodeToString(new HmacUtils(HmacAlgorithms.HMAC_SHA_256, secret).hmac(payload));
        return signature + "." + payload;
    }

    @Test
    void validRequestYieldsUserId() {
        String request = sign("{\"algorithm\":\"HMAC-SHA256\",\"issued_at\":1700000000,\"user_id\":\"1234567\"}", SECRET);

        assertEquals(Optional.of("1234567"), MetaSignedRequest.verifiedUserId(request, SECRET));
    }

    @Test
    void wrongSecretIsRejected() {
        String request = sign("{\"algorithm\":\"HMAC-SHA256\",\"user_id\":\"1234567\"}", "other-secret");

        assertTrue(MetaSignedRequest.verifiedUserId(request, SECRET).isEmpty());
    }

    @Test
    void tamperedPayloadIsRejected() {
        String request = sign("{\"algorithm\":\"HMAC-SHA256\",\"user_id\":\"1234567\"}", SECRET);
        String forged = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"algorithm\":\"HMAC-SHA256\",\"user_id\":\"999\"}".getBytes(StandardCharsets.UTF_8));

        assertTrue(MetaSignedRequest.verifiedUserId(request.substring(0, request.indexOf('.') + 1) + forged, SECRET).isEmpty());
    }

    @Test
    void otherAlgorithmOrMissingUserIsRejected() {
        assertTrue(MetaSignedRequest.verifiedUserId(sign("{\"algorithm\":\"none\",\"user_id\":\"1\"}", SECRET), SECRET).isEmpty());
        assertTrue(MetaSignedRequest.verifiedUserId(sign("{\"algorithm\":\"HMAC-SHA256\"}", SECRET), SECRET).isEmpty());
    }

    @Test
    void malformedInputIsRejected() {
        assertTrue(MetaSignedRequest.verifiedUserId(null, SECRET).isEmpty());
        assertTrue(MetaSignedRequest.verifiedUserId("no-dot", SECRET).isEmpty());
        assertTrue(MetaSignedRequest.verifiedUserId("!!!.???", SECRET).isEmpty());
        assertTrue(MetaSignedRequest.verifiedUserId(sign("{}", SECRET), "").isEmpty());
    }
}
