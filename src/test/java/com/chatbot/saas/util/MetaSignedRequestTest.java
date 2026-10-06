package com.chatbot.saas.util;

import org.apache.commons.codec.digest.HmacAlgorithms;
import org.apache.commons.codec.digest.HmacUtils;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

public class MetaSignedRequestTest {

    public static String sign(String json, String secret) {
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        String payload = encoder.encodeToString(json.getBytes(StandardCharsets.UTF_8));
        byte[] signature = new HmacUtils(HmacAlgorithms.HMAC_SHA_256, secret).hmac(payload.getBytes(StandardCharsets.US_ASCII));
        return encoder.encodeToString(signature) + "." + payload;
    }

    @Test
    void validRequestYieldsTheUserId() {
        String request = sign("{\"algorithm\":\"HMAC-SHA256\",\"issued_at\":1700000000,\"user_id\":\"1234567\"}", "secret");
        assertEquals("1234567", MetaSignedRequest.verifiedUserId(request, "secret"));
    }

    @Test
    void wrongSecretTamperingOrGarbageIsRejected() {
        String request = sign("{\"algorithm\":\"HMAC-SHA256\",\"user_id\":\"1234567\"}", "secret");
        assertNull(MetaSignedRequest.verifiedUserId(request, "other-secret"));

        String forgedPayload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"algorithm\":\"HMAC-SHA256\",\"user_id\":\"999\"}".getBytes(StandardCharsets.UTF_8));
        assertNull(MetaSignedRequest.verifiedUserId(request.split("\\.")[0] + "." + forgedPayload, "secret"));

        assertNull(MetaSignedRequest.verifiedUserId(sign("{\"algorithm\":\"none\",\"user_id\":\"1\"}", "secret"), "secret"));
        assertNull(MetaSignedRequest.verifiedUserId(sign("{\"algorithm\":\"HMAC-SHA256\"}", "secret"), "secret"));
        for (String garbage : new String[]{null, "", "abc", "!!!.###", "."}) {
            assertNull(MetaSignedRequest.verifiedUserId(garbage, "secret"), String.valueOf(garbage));
        }
        assertNull(MetaSignedRequest.verifiedUserId(request, ""), "an unset app secret accepts nothing");
    }
}
