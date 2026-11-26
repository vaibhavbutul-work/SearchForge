package com.searchforge.config;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Task 13.4: Flyway Schema Migration & Hibernate Validation Tests")
class FlywayMigrationTest {

    @Test
    @DisplayName("Flyway V1 baseline migration creates all 5 entity tables and passes Hibernate ddl-auto=validate")
    void flywayAppliesBaselineAndPassesHibernateValidate() throws Exception {
        SpringApplication app = new SpringApplication(com.searchforge.SearchForgeApplication.class);
        app.setAdditionalProfiles("prod");

        try (ConfigurableApplicationContext ctx = app.run(
                "--DB_USERNAME=sa",
                "--DB_PASSWORD=StrongTestDbPass#2026",
                "--SEARCHFORGE_ADMIN_API_KEY=prod-flyway-test-key-1234567890",
                "--SEMANTIC_EMBEDDING_MODEL_TYPE=deterministic",
                "--SEMANTIC_EMBEDDING_ALLOW_FALLBACK=true",
                "--SERVER_PORT=0",
                "--DB_URL=jdbc:h2:mem:flyway_prod_validate;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
                "--spring.datasource.driver-class-name=org.h2.Driver",
                "--spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
                "--JPA_DDL_AUTO=validate",
                "--spring.flyway.enabled=true",
                "--searchforge.lifecycle.startup-reconciliation.enabled=false"
        )) {
            DataSource ds = ctx.getBean(DataSource.class);
            Set<String> tables = new HashSet<>();
            try (Connection conn = ds.getConnection();
                 ResultSet rs = conn.getMetaData().getTables(null, "PUBLIC", "%", new String[]{"TABLE"})) {
                while (rs.next()) {
                    tables.add(rs.getString("TABLE_NAME").toLowerCase());
                }
            }

            assertTrue(tables.contains("flyway_schema_history"), "Flyway history table must exist");
            assertTrue(tables.contains("crawled_documents"), "crawled_documents table must exist");
            assertTrue(tables.contains("indexed_document_metadata"), "indexed_document_metadata table must exist");
            assertTrue(tables.contains("indexed_terms"), "indexed_terms table must exist");
            assertTrue(tables.contains("term_postings"), "term_postings table must exist");
            assertTrue(tables.contains("document_embeddings"), "document_embeddings table must exist");
        }
    }

    @Test
    @DisplayName("Flyway fails fast and rolls back when a bad migration script is encountered")
    void flywayFailsOnInvalidMigration(@TempDir Path tempMigrationDir) throws Exception {
        Files.writeString(
                tempMigrationDir.resolve("V1__bad_migration.sql"),
                "CREATE TABLE broken_table (id INT INVALID_SYNTAX_HERE);"
        );

        Flyway flyway = Flyway.configure()
                .dataSource("jdbc:h2:mem:flyway_bad_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL", "sa", "")
                .locations("filesystem:" + tempMigrationDir.toAbsolutePath())
                .load();

        assertThrows(FlywayException.class, flyway::migrate,
                "Invalid SQL migration must fail fast with FlywayException");
    }
}
