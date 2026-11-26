package com.searchforge.config;

import com.searchforge.security.SecurityProperties;
import com.searchforge.security.SecurityStartupValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.mock.env.MockEnvironment;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@Tag("integration")
@Tag("regression")
@DisplayName("Task 13.2: Production Configuration Profile & Fail-Fast Validation Tests")
class ProductionConfigurationProfileTest {

    @Test
    @DisplayName("Production profile loads expected settings when all mandatory secrets are provided")
    void prodProfileLoadsWithValidSecrets() {
        SpringApplication app = new SpringApplication(com.searchforge.SearchForgeApplication.class);
        app.setAdditionalProfiles("prod");

        try (ConfigurableApplicationContext ctx = app.run(
                "--DB_USERNAME=sf_prod_user",
                "--DB_PASSWORD=StrongProdDbPass#2026",
                "--SEARCHFORGE_ADMIN_API_KEY=prod-super-secure-random-key-987654321",
                "--CORS_ALLOWED_ORIGINS=https://search.example.com,https://admin.example.com",
                "--SEARCHFORGE_MODEL_PATH=/opt/models/custom.onnx",
                "--SEMANTIC_EMBEDDING_ALLOW_FALLBACK=true",
                "--SERVER_PORT=0",
                "--DB_URL=jdbc:h2:mem:prod_profile_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
                "--spring.datasource.driver-class-name=org.h2.Driver",
                "--spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
                "--JPA_DDL_AUTO=create-drop",
                "--searchforge.lifecycle.startup-reconciliation.enabled=false"
        )) {
            SecurityProperties securityProps = ctx.getBean(SecurityProperties.class);
            assertTrue(securityProps.isEnabled(), "Security must be enabled in prod");
            assertTrue(securityProps.isStrictSecrets(), "Strict secrets must be enabled in prod");
            assertEquals("prod-super-secure-random-key-987654321", securityProps.getAdminApiKey());

            // Verify secret masking in toString()
            String rendered = securityProps.toString();
            assertFalse(rendered.contains("prod-super-secure-random-key-987654321"),
                    "Raw admin API key must never appear in SecurityProperties.toString()");
            assertTrue(rendered.contains("pr****21"), "Masked key format should appear");

            WebConfig webConfig = ctx.getBean(WebConfig.class);
            assertArrayEquals(
                    new String[]{"https://search.example.com", "https://admin.example.com"},
                    webConfig.getAllowedOrigins()
            );

            assertEquals("/opt/models/custom.onnx",
                    ctx.getEnvironment().getProperty("semantic.embedding.onnx-model-path"));
        }
    }

    @Test
    @DisplayName("Production profile fails startup when mandatory DB_PASSWORD is missing")
    void prodProfileFailsWithoutDbPassword() {
        SpringApplication app = new SpringApplication(com.searchforge.SearchForgeApplication.class);
        app.setAdditionalProfiles("prod");

        Map<String, Object> props = new HashMap<>();
        props.put("DB_USERNAME", "sf_prod_user");
        props.put("SEARCHFORGE_ADMIN_API_KEY", "prod-super-secure-random-key-987654321");
        props.put("SERVER_PORT", "0");
        props.put("searchforge.lifecycle.startup-reconciliation.enabled", "false");
        app.setDefaultProperties(props);

        assertThrows(Exception.class, () -> {
            try (ConfigurableApplicationContext ctx = app.run()) {
                fail("Should not start without DB_PASSWORD");
            }
        });
    }

    @Test
    @DisplayName("SecurityStartupValidator aborts startup in prod profile when admin key is weak or default")
    void prodProfileAbortsOnWeakOrDefaultAdminKey() {
        MockEnvironment prodEnv = new MockEnvironment().withProperty("spring.profiles.active", "prod");
        prodEnv.setActiveProfiles("prod");

        SecurityProperties weakProps = new SecurityProperties();
        weakProps.setEnabled(true);
        weakProps.setStrictSecrets(true);
        weakProps.setAdminApiKey("short-key");

        SecurityStartupValidator validator = new SecurityStartupValidator(weakProps, prodEnv);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                validator.onApplicationEvent(new ApplicationReadyEvent(
                        new SpringApplication(Object.class), new String[0], null, Duration.ZERO))
        );
        assertTrue(ex.getMessage().contains("Startup aborted due to insecure production credentials"));
    }

    @Test
    @DisplayName("Dev profile starts cleanly with H2 in-memory database")
    void devProfileStartsCleanly() {
        SpringApplication app = new SpringApplication(com.searchforge.SearchForgeApplication.class);
        app.setAdditionalProfiles("dev");

        Map<String, Object> props = new HashMap<>();
        props.put("SERVER_PORT", "0");
        props.put("searchforge.lifecycle.startup-reconciliation.enabled", "false");
        app.setDefaultProperties(props);

        try (ConfigurableApplicationContext ctx = app.run()) {
            assertTrue(ctx.isActive());
            assertEquals("org.h2.Driver",
                    ctx.getEnvironment().getProperty("spring.datasource.driver-class-name"));
        }
    }
}
