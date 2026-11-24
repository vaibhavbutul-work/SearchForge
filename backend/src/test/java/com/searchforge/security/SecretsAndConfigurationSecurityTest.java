package com.searchforge.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.mock.env.MockEnvironment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

@DisplayName("Task 11.7 — Secrets & Configuration Security Test Suite")
class SecretsAndConfigurationSecurityTest {

    @Nested
    @DisplayName("SecurityProperties Secret Masking Tests")
    class SecretMaskingTests {

        @Test
        @DisplayName("Should mask secrets properly without leaking raw credential bytes")
        void shouldMaskSecretsProperly() {
            assertEquals("[EMPTY]", SecurityProperties.maskSecret(null));
            assertEquals("[EMPTY]", SecurityProperties.maskSecret(""));
            assertEquals("[EMPTY]", SecurityProperties.maskSecret("   "));
            assertEquals("****", SecurityProperties.maskSecret("a"));
            assertEquals("****", SecurityProperties.maskSecret("abc"));
            assertEquals("****", SecurityProperties.maskSecret("1234"));

            String masked = SecurityProperties.maskSecret("sf-admin-secret-key-prod");
            assertEquals("sf****od", masked);
            assertFalse(masked.contains("secret-key"), "Masked output must not reveal internal secret content");
        }

        @Test
        @DisplayName("SecurityProperties.toString() must never contain raw admin or search API keys")
        void toStringMustMaskSensitiveApiKeys() {
            SecurityProperties properties = new SecurityProperties();
            properties.setAdminApiKey("super-confidential-production-key-999");
            properties.setSearchApiKey("private-search-access-token-111");

            String output = properties.toString();

            assertFalse(output.contains("super-confidential-production-key-999"), "Raw admin key leaked in toString()");
            assertFalse(output.contains("private-search-access-token-111"), "Raw search key leaked in toString()");
            assertTrue(output.contains("su****99"));
            assertTrue(output.contains("pr****11"));
        }
    }

    @Nested
    @DisplayName("SecurityStartupValidator Pre-Flight Tests")
    class StartupValidatorTests {

        @Test
        @DisplayName("Should validate strong non-default API key successfully")
        void shouldPassOnStrongKey() {
            SecurityProperties properties = new SecurityProperties();
            properties.setAdminApiKey("c9B#x7!k2$vL9@mP4^qR1*zT8"); // 24 chars, strong random
            MockEnvironment env = new MockEnvironment();

            SecurityStartupValidator validator = new SecurityStartupValidator(properties, env);
            SecurityStartupValidator.ValidationResult result = validator.validate();

            assertTrue(result.isSecure());
            assertFalse(result.isDefaultKey());
            assertFalse(result.isTooShort());
        }

        @Test
        @DisplayName("Should flag known default keys as insecure")
        void shouldFlagKnownDefaultKeys() {
            SecurityProperties properties = new SecurityProperties();
            MockEnvironment env = new MockEnvironment();

            for (String defaultKey : SecurityStartupValidator.KNOWN_DEFAULT_KEYS) {
                properties.setAdminApiKey(defaultKey);
                SecurityStartupValidator validator = new SecurityStartupValidator(properties, env);
                SecurityStartupValidator.ValidationResult result = validator.validate();

                assertFalse(result.isSecure(), "Key '" + defaultKey + "' must be flagged insecure");
                assertTrue(result.isDefaultKey(), "Key '" + defaultKey + "' must be recognized as default");
            }
        }

        @Test
        @DisplayName("Should flag keys shorter than minimum length of 16 characters")
        void shouldFlagShortKeys() {
            SecurityProperties properties = new SecurityProperties();
            properties.setAdminApiKey("short-key-123"); // 13 chars
            MockEnvironment env = new MockEnvironment();

            SecurityStartupValidator validator = new SecurityStartupValidator(properties, env);
            SecurityStartupValidator.ValidationResult result = validator.validate();

            assertFalse(result.isSecure());
            assertTrue(result.isTooShort());
        }

        @Test
        @DisplayName("Should abort startup with IllegalStateException when strictSecrets is enabled with default key")
        void shouldAbortStartupInStrictMode() {
            SecurityProperties properties = new SecurityProperties();
            properties.setAdminApiKey("sf-admin-secret-key-prod");
            properties.setStrictSecrets(true); // Enforce strict mode
            MockEnvironment env = new MockEnvironment();

            SecurityStartupValidator validator = new SecurityStartupValidator(properties, env);

            ApplicationReadyEvent mockEvent = mock(ApplicationReadyEvent.class);
            assertThrows(IllegalStateException.class, () -> validator.onApplicationEvent(mockEvent));
        }

        @Test
        @DisplayName("Should abort startup with IllegalStateException when in 'prod' profile with default key")
        void shouldAbortStartupInProdProfile() {
            SecurityProperties properties = new SecurityProperties();
            properties.setAdminApiKey("sf-admin-secret-key-prod");
            MockEnvironment env = new MockEnvironment();
            env.setActiveProfiles("prod"); // Active prod profile

            SecurityStartupValidator validator = new SecurityStartupValidator(properties, env);

            ApplicationReadyEvent mockEvent = mock(ApplicationReadyEvent.class);
            assertThrows(IllegalStateException.class, () -> validator.onApplicationEvent(mockEvent));
        }

        @Test
        @DisplayName("Should permit non-strict dev startup with warning but without throwing exception")
        void shouldPermitDevStartupWithWarning() {
            SecurityProperties properties = new SecurityProperties();
            properties.setAdminApiKey("sf-admin-secret-key-dev");
            properties.setStrictSecrets(false);
            MockEnvironment env = new MockEnvironment();
            env.setActiveProfiles("dev");

            SecurityStartupValidator validator = new SecurityStartupValidator(properties, env);

            ApplicationReadyEvent mockEvent = mock(ApplicationReadyEvent.class);
            assertDoesNotThrow(() -> validator.onApplicationEvent(mockEvent));
        }
    }

    @Nested
    @DisplayName("Repository Environment & Gitignore Audit Tests")
    class GitignoreAndEnvAuditTests {

        @Test
        @DisplayName("Root .gitignore must exclude .env, .env.*, *.key, and *.pem files")
        void gitignoreMustExcludeSensitiveFiles() throws IOException {
            Path gitignorePath = Paths.get("../.gitignore").toAbsolutePath().normalize();
            if (!Files.exists(gitignorePath)) {
                gitignorePath = Paths.get(".gitignore").toAbsolutePath().normalize();
            }

            assertTrue(Files.exists(gitignorePath), ".gitignore must exist in project root");
            List<String> lines = Files.readAllLines(gitignorePath);
            String content = String.join("\n", lines);

            assertTrue(content.contains(".env"), ".gitignore must contain .env rule");
            assertTrue(content.contains(".env.*"), ".gitignore must contain .env.* rule");
            assertTrue(content.contains("*.key"), ".gitignore must contain *.key rule");
            assertTrue(content.contains("*.pem"), ".gitignore must contain *.pem rule");
        }

        @Test
        @DisplayName(".env.example must only contain placeholder dummy secrets")
        void envExampleMustNotContainRealSecrets() throws IOException {
            Path envExamplePath = Paths.get("../.env.example").toAbsolutePath().normalize();
            if (!Files.exists(envExamplePath)) {
                envExamplePath = Paths.get(".env.example").toAbsolutePath().normalize();
            }

            assertTrue(Files.exists(envExamplePath), ".env.example must exist in project root");
            String content = Files.readString(envExamplePath);

            assertTrue(content.contains("replace_with_your_secure_password"));
            assertTrue(content.contains("replace_with_your_admin_api_key"));
            assertFalse(content.contains("password123"), "Sample passwords must not look like real keys");
        }
    }
}
