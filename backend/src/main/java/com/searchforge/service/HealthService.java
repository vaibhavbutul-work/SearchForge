package com.searchforge.service;

import com.searchforge.dto.HealthResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(HealthService.class);

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
        String overallStatus = HealthResponse.STATUS_UP.equals(dbStatus) 
            ? HealthResponse.STATUS_UP 
            : HealthResponse.STATUS_DOWN;

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
                return HealthResponse.STATUS_UP;
            }
            log.warn("Database connection is invalid or socket validation timed out");
            return HealthResponse.STATUS_DOWN;
        } catch (Exception e) {
            // In SLF4J, passing Throwable as the final argument automatically logs the stack trace
            log.error("Database health check failed", e);
            return HealthResponse.STATUS_DOWN;
        }
    }
}
