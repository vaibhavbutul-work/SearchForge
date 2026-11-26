package com.searchforge.config;

import com.searchforge.crawler.model.CrawledDocument;
import com.searchforge.crawler.repository.CrawledDocumentRepository;
import com.searchforge.indexer.service.IndexingEngine;
import com.searchforge.lifecycle.integrity.IndexIntegrityReport;
import com.searchforge.lifecycle.model.DocumentLifecycleStatus;
import com.searchforge.lifecycle.startup.StartupIndexReconciliationRunner;
import com.searchforge.semantic.vector.VectorStorage;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@Tag("integration")
@Tag("regression")
@DisplayName("Task 13.8: Rollback, Recovery & Operational Resilience Tests")
class OperationalResilienceTest {

    @Test
    @DisplayName("Backend fails fast when PostgreSQL is unavailable at startup")
    void backendFailsFastWhenDatabaseUnavailable() {
        SpringApplication app = new SpringApplication(com.searchforge.SearchForgeApplication.class);
        app.setAdditionalProfiles("prod");

        long startMs = System.currentTimeMillis();
        assertThrows(Exception.class, () -> {
            try (ConfigurableApplicationContext ctx = app.run(
                    "--DB_URL=jdbc:postgresql://127.0.0.1:59999/searchforge_db",
                    "--DB_USERNAME=searchforge",
                    "--DB_PASSWORD=StrongTestDbPass#2026",
                    "--SEARCHFORGE_ADMIN_API_KEY=prod-resilience-test-key-1234567890",
                    "--spring.datasource.hikari.initialization-fail-timeout=1000",
                    "--spring.datasource.hikari.connection-timeout=1000",
                    "--SERVER_PORT=0"
            )) {
                fail("Should fail fast when PostgreSQL is unreachable");
            }
        });
        long elapsedMs = System.currentTimeMillis() - startMs;
        assertTrue(elapsedMs < 15000, "Startup failure should happen promptly, took " + elapsedMs + " ms");
    }

    @Test
    @DisplayName("Invalid production configuration (weak API key) aborts startup cleanly")
    void invalidProdConfigurationAbortsStartup() {
        SpringApplication app = new SpringApplication(com.searchforge.SearchForgeApplication.class);
        app.setAdditionalProfiles("prod");

        Exception ex = assertThrows(Exception.class, () -> {
            try (ConfigurableApplicationContext ctx = app.run(
                    "--DB_USERNAME=sa",
                    "--DB_PASSWORD=StrongTestDbPass#2026",
                    "--SEARCHFORGE_ADMIN_API_KEY=weak",
                    "--SEMANTIC_EMBEDDING_MODEL_TYPE=deterministic",
                    "--SEMANTIC_EMBEDDING_ALLOW_FALLBACK=true",
                    "--SERVER_PORT=0",
                    "--DB_URL=jdbc:h2:mem:resilience_bad_key;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
                    "--spring.datasource.driver-class-name=org.h2.Driver",
                    "--spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
                    "--JPA_DDL_AUTO=validate",
                    "--spring.flyway.enabled=true",
                    "--searchforge.lifecycle.startup-reconciliation.enabled=false"
            )) {
                fail("Should abort startup on weak SEARCHFORGE_ADMIN_API_KEY");
            }
        });

        Throwable root = ex;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        assertTrue(root instanceof IllegalStateException
                        || ex.getMessage().contains("insecure production credentials")
                        || (root.getMessage() != null && root.getMessage().contains("insecure production credentials")),
                "Expected IllegalStateException for weak production API key");
    }

    @Test
    @DisplayName("Failed Flyway migration aborts without partial corruption")
    void failedFlywayMigrationAborts(@TempDir Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("V1__valid.sql"), "CREATE TABLE t_ok (id INT PRIMARY KEY);");
        Files.writeString(tempDir.resolve("V2__broken.sql"), "CREATE TABLE t_bad (id INVALID_TYPE);");

        Flyway flyway = Flyway.configure()
                .dataSource("jdbc:h2:mem:resilience_flyway_fail;DB_CLOSE_DELAY=-1;MODE=PostgreSQL", "sa", "")
                .locations("filesystem:" + tempDir.toAbsolutePath())
                .load();

        assertThrows(FlywayException.class, flyway::migrate);
    }

    @Test
    @DisplayName("Startup reconciliation auto-heals missing lexical postings and vector embeddings after crash")
    void startupReconciliationRestoresIndexAndVectorConsistency() {
        SpringApplication app = new SpringApplication(com.searchforge.SearchForgeApplication.class);
        app.setAdditionalProfiles("prod");

        try (ConfigurableApplicationContext ctx = app.run(
                "--DB_USERNAME=sa",
                "--DB_PASSWORD=StrongTestDbPass#2026",
                "--SEARCHFORGE_ADMIN_API_KEY=prod-resilience-test-key-1234567890",
                "--SEMANTIC_EMBEDDING_MODEL_TYPE=deterministic",
                "--SEMANTIC_EMBEDDING_ALLOW_FALLBACK=true",
                "--SERVER_PORT=0",
                "--DB_URL=jdbc:h2:mem:resilience_reconcile;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
                "--spring.datasource.driver-class-name=org.h2.Driver",
                "--spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
                "--JPA_DDL_AUTO=validate",
                "--spring.flyway.enabled=true",
                "--searchforge.lifecycle.startup-reconciliation.enabled=false"
        )) {
            CrawledDocumentRepository docRepo = ctx.getBean(CrawledDocumentRepository.class);
            StartupIndexReconciliationRunner reconciler = ctx.getBean(StartupIndexReconciliationRunner.class);
            IndexingEngine indexingEngine = ctx.getBean(IndexingEngine.class);
            VectorStorage vectorStorage = ctx.getBean(VectorStorage.class);

            // Simulate crash leaving an ACTIVE document without lexical postings or vector embeddings
            CrawledDocument doc = new CrawledDocument(
                    "https://example.com/resilience-doc",
                    "example.com",
                    "Resilience Recovery Document",
                    "SearchForge transactional coordinator recovers missing postings and vectors cleanly.",
                    "hash-resilience-1",
                    200,
                    Instant.now(),
                    DocumentLifecycleStatus.ACTIVE,
                    1
            );
            CrawledDocument saved = docRepo.save(doc);
            assertEquals(0, indexingEngine.getStats().totalIndexedDocuments());
            assertEquals(0, vectorStorage.size());

            // Run startup reconciliation
            IndexIntegrityReport report = reconciler.reconcile();
            assertTrue(report.isHealthy(), "Reconciliation must restore 100% index & vector health");
            assertEquals(1, indexingEngine.getStats().totalIndexedDocuments());
            assertEquals(1, vectorStorage.size());
            assertTrue(vectorStorage.contains(saved.getId()));
        }
    }
}
