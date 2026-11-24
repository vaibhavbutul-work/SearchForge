package com.searchforge.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;

/**
 * Validates security credentials and configuration during application startup.
 *
 * Detects known insecure default secrets, weak API keys, and missing credentials.
 * In strict or production mode, aborts startup to prevent deployment with compromised secrets.
 */
@Component
public class SecurityStartupValidator implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger log = LoggerFactory.getLogger(SecurityStartupValidator.class);

    public static final int MIN_KEY_LENGTH = 16;

    public static final Set<String> KNOWN_DEFAULT_KEYS = Set.of(
            "sf-admin-secret-key-prod",
            "sf-admin-secret-key-dev",
            "sf-test-admin-key",
            "admin",
            "password",
            "secret",
            "12345678",
            "searchforge",
            "default",
            "changeme"
    );

    private final SecurityProperties properties;
    private final Environment environment;

    public SecurityStartupValidator(SecurityProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    public record ValidationResult(
            boolean isSecure,
            boolean isDefaultKey,
            boolean isTooShort,
            String message
    ) {}

    /**
     * Evaluates security configuration without throwing exceptions.
     */
    public ValidationResult validate() {
        if (!properties.isEnabled()) {
            return new ValidationResult(true, false, false, "Security filters are disabled.");
        }

        String adminKey = properties.getAdminApiKey();

        if (adminKey == null || adminKey.isBlank()) {
            return new ValidationResult(false, true, true, "Administrative API key is null or empty.");
        }

        String trimmed = adminKey.trim();
        boolean isDefault = KNOWN_DEFAULT_KEYS.contains(trimmed);
        boolean isShort = trimmed.length() < MIN_KEY_LENGTH;

        if (isDefault) {
            return new ValidationResult(
                    false,
                    true,
                    isShort,
                    "Administrative API key is using a known default/sample credential: '" + SecurityProperties.maskSecret(trimmed) + "'"
            );
        }

        if (isShort) {
            return new ValidationResult(
                    false,
                    false,
                    true,
                    "Administrative API key length (" + trimmed.length() + " chars) is below recommended minimum of " + MIN_KEY_LENGTH + " chars."
            );
        }

        return new ValidationResult(true, false, false, "Security credentials and API keys are strong.");
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (!properties.isEnabled()) {
            log.info("SearchForge API security is globally disabled (searchforge.security.enabled=false).");
            return;
        }

        ValidationResult result = validate();
        boolean isProd = isProductionProfile();

        if (!result.isSecure()) {
            String warningBanner = String.format("""
                    
                    ================================================================================
                    [SECURITY WARNING] INSECURE CONFIGURATION DETECTED:
                    %s
                    Remediation: Set the SEARCHFORGE_ADMIN_API_KEY environment variable with a strong,
                                 random secret (minimum %d characters) before deploying.
                    ================================================================================
                    """, result.message(), MIN_KEY_LENGTH);

            if (isProd || properties.isStrictSecrets()) {
                log.error("{}", warningBanner);
                throw new IllegalStateException("Startup aborted due to insecure production credentials: " + result.message());
            } else {
                log.warn("{}", warningBanner);
            }
        } else {
            log.info("Security credentials pre-flight check passed: Administrative API key configured safely.");
        }
    }

    private boolean isProductionProfile() {
        if (environment == null) {
            return false;
        }
        return Arrays.asList(environment.getActiveProfiles()).contains("prod");
    }
}
