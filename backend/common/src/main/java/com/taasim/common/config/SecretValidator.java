package com.taasim.common.config;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Fails fast at startup if a production deployment is missing critical secrets.
 * Only active when SPRING_PROFILES_ACTIVE=prod.
 */
@Component
@Profile("prod")
public class SecretValidator {

    private static final String[] REQUIRED = {
        "JWT_SECRET",
        "POSTGRES_PASSWORD"
    };

    @PostConstruct
    public void validate() {
        for (String var : REQUIRED) {
            String v = System.getenv(var);
            if (v == null || v.isBlank() || v.startsWith("dev-only-")) {
                throw new IllegalStateException(
                    "❌ Missing or default value for required prod env var: " + var);
            }
        }
        System.out.println("✅ All required prod secrets present.");
    }
}
