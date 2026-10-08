package com.chatbot.saas.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.codec.digest.HmacAlgorithms;
import org.apache.commons.codec.digest.HmacUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Optional;

/**
 * Meta's {@code signed_request} (deauthorize and data-deletion callbacks):
 * {@code base64url(HMAC-SHA256(payload part, app secret)) + "." + base64url(JSON payload)}.
 */
public final class MetaSignedRequest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private MetaSignedRequest() {
    }

    /** The {@code user_id} it was issued for, or empty if it is malformed or not signed with the secret. */
    public static Optional<String> verifiedUserId(String signedRequest, String appSecret) {
        if (signedRequest == null || appSecret == null || appSecret.isBlank()) {
            return Optional.empty();
        }
        int dot = signedRequest.indexOf('.');
        if (dot <= 0 || dot == signedRequest.length() - 1) {
            return Optional.empty();
        }
        String encodedPayload = signedRequest.substring(dot + 1);
        try {
            byte[] signature = Base64.getUrlDecoder().decode(signedRequest.substring(0, dot));
            byte[] expected = new HmacUtils(HmacAlgorithms.HMAC_SHA_256, appSecret).hmac(encodedPayload);
            if (!MessageDigest.isEqual(expected, signature)) {
                return Optional.empty();
            }
            JsonNode payload = OBJECT_MAPPER.readTree(
                    new String(Base64.getUrlDecoder().decode(encodedPayload), StandardCharsets.UTF_8));
            if (!"HMAC-SHA256".equalsIgnoreCase(payload.path("algorithm").asText())
                    || !payload.hasNonNull("user_id")) {
                return Optional.empty();
            }
            return Optional.of(payload.get("user_id").asText());
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
