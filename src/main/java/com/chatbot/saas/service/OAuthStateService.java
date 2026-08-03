package com.chatbot.saas.service;

import org.apache.commons.codec.digest.HmacAlgorithms;
import org.apache.commons.codec.digest.HmacUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * Signs the OAuth "state" parameter so /api/auth/meta/callback (a public, unauthenticated
 * redirect from Meta) can trust the businessId it carries instead of accepting a raw,
 * client-suppliable id - otherwise an attacker could link their own OAuth code to an
 * arbitrary business's Meta account.
 */
@Service
public class OAuthStateService {

    private static final long EXPIRY_MINUTES = 15;

    @Value("${oauth.state-secret}")
    private String secret;

    public String createState(Long businessId) {
        long expiresAt = System.currentTimeMillis() / 1000 + (EXPIRY_MINUTES * 60);
        String payload = businessId + "." + expiresAt;
        String signature = sign(payload);
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString((payload + "." + signature).getBytes(StandardCharsets.UTF_8));
    }

    public Long parseAndValidate(String state) {
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(state), StandardCharsets.UTF_8);
            String[] parts = decoded.split("\\.");
            if (parts.length != 3) {
                throw new IllegalArgumentException("Malformed OAuth state");
            }
            String businessIdStr = parts[0];
            String expiresAtStr = parts[1];
            String signature = parts[2];

            String expectedSignature = sign(businessIdStr + "." + expiresAtStr);
            if (!MessageDigest.isEqual(expectedSignature.getBytes(StandardCharsets.UTF_8),
                    signature.getBytes(StandardCharsets.UTF_8))) {
                throw new IllegalArgumentException("Invalid OAuth state signature");
            }

            long expiresAt = Long.parseLong(expiresAtStr);
            if (System.currentTimeMillis() / 1000 > expiresAt) {
                throw new IllegalArgumentException("Expired OAuth state");
            }

            return Long.parseLong(businessIdStr);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid OAuth state", e);
        }
    }

    private String sign(String payload) {
        return new HmacUtils(HmacAlgorithms.HMAC_SHA_256, secret).hmacHex(payload);
    }
}
