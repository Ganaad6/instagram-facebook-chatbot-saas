package com.chatbot.saas.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.codec.digest.HmacAlgorithms;
import org.apache.commons.codec.digest.HmacUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * Meta's signed_request (deauthorize and data-deletion callbacks): base64url(signature) "."
 * base64url(json), where signature = HMAC-SHA256(json part, app secret).
 */
public final class MetaSignedRequest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private MetaSignedRequest() {
    }

    /** @return the app-scoped user id, or null if the request is malformed or not signed with this secret */
    public static String verifiedUserId(String signedRequest, String appSecret) {
        if (signedRequest == null || appSecret == null || appSecret.isEmpty()) {
            return null;
        }
        String[] parts = signedRequest.split("\\.", 2);
        if (parts.length != 2) {
            return null;
        }
        try {
            byte[] signature = Base64.getUrlDecoder().decode(parts[0]);
            byte[] expected = new HmacUtils(HmacAlgorithms.HMAC_SHA_256, appSecret)
                    .hmac(parts[1].getBytes(StandardCharsets.US_ASCII));
            if (!MessageDigest.isEqual(expected, signature)) {
                return null;
            }
            JsonNode payload = OBJECT_MAPPER.readTree(Base64.getUrlDecoder().decode(parts[1]));
            if (!"HMAC-SHA256".equalsIgnoreCase(payload.path("algorithm").asText())
                    || !payload.hasNonNull("user_id")) {
                return null;
            }
            return payload.get("user_id").asText();
        } catch (Exception e) {
            return null;
        }
    }
}
