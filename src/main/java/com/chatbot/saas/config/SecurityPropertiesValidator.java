package com.chatbot.saas.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Fails startup under the "prod" profile if any security-critical setting was left at its
 * insecure development default or blank - these all have permissive fallbacks in
 * application.yml so local dev works out of the box, but that means a missing env var in
 * production would otherwise fail silently instead of loudly.
 */
@Component
@Profile("prod")
public class SecurityPropertiesValidator {

    private static final String INSECURE_ENCRYPTION_KEY = "0123456789abcdef0123456789abcdef";
    private static final String INSECURE_WEBHOOK_TOKEN = "verify_token";
    private static final String INSECURE_ADMIN_PASSWORD = "change_me_before_deploying";

    @Value("${encryption.secret-key:}")
    private String encryptionSecretKey;

    @Value("${webhook.verify-token:}")
    private String webhookVerifyToken;

    @Value("${oauth.state-secret:}")
    private String oauthStateSecret;

    @Value("${admin.password:}")
    private String adminPassword;

    @Value("${cors.allowed-origins:}")
    private String corsAllowedOrigins;

    @PostConstruct
    public void validate() {
        List<String> problems = new ArrayList<>();

        if (encryptionSecretKey.isBlank() || encryptionSecretKey.equals(INSECURE_ENCRYPTION_KEY)) {
            problems.add("ENCRYPTION_SECRET_KEY must be set to a unique 16/24/32-byte value");
        }
        if (webhookVerifyToken.isBlank() || webhookVerifyToken.equals(INSECURE_WEBHOOK_TOKEN)) {
            problems.add("WEBHOOK_VERIFY_TOKEN must be set to a unique value");
        }
        if (oauthStateSecret.isBlank() || oauthStateSecret.equals(INSECURE_ENCRYPTION_KEY)) {
            problems.add("OAUTH_STATE_SECRET must be set to a unique value, distinct from ENCRYPTION_SECRET_KEY");
        }
        if (adminPassword.isBlank() || adminPassword.equals(INSECURE_ADMIN_PASSWORD)) {
            problems.add("ADMIN_PASSWORD must be set to a strong, unique value");
        }
        if ("*".equals(corsAllowedOrigins.trim())) {
            problems.add("CORS_ALLOWED_ORIGINS must not be '*' in production");
        }

        if (!problems.isEmpty()) {
            throw new IllegalStateException(
                    "Refusing to start with insecure production configuration:\n - "
                            + String.join("\n - ", problems));
        }
    }
}
