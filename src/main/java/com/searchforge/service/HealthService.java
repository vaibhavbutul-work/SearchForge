package com.searchforge.service;

import com.searchforge.dto.HealthResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Instant;

/**
 * Service handling health check logic and database connectivity verification.
 */
@Service
public class HealthService {

    private final DataSource dataSource;
    private final String appName;
    private final String appVersion;

    public HealthService(
        DataSource dataSource,
        @Value("${app.name:SearchForge}") String appName,
        @Value("${app.version:1.0.0}") String appVersion
    ) {
        this.dataSource = dataSource;
        this.appName = appName;
        this.appVersion = appVersion;
    }

    public HealthResponse checkHealth() {
        String dbStatus = verifyDatabaseConnection();
        String overallStatus = "UP".equals(dbStatus) ? "UP" : "DEGRADED";

        return new HealthResponse(
            overallStatus,
            appName,
            appVersion,
            dbStatus,
            Instant.now()
        );
    }

    private String verifyDatabaseConnection() {
        try (Connection connection = dataSource.getConnection()) {
            if (connection.isValid(2)) {
                return "UP";
            }
            return "DOWN (Validation timeout)";
        } catch (Exception e) {
            return "DOWN (" + e.getMessage() + ")";
        }
    }
}
