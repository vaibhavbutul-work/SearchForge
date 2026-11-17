package com.searchforge.service;

import com.searchforge.dto.HealthResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HealthServiceTest {

    @Mock
    private DataSource dataSource;

    @Mock
    private Connection connection;

    private HealthService healthService;

    @BeforeEach
    void setUp() {
        healthService = new HealthService(dataSource, "SearchForge", "1.0.0");
    }

    @Test
    @DisplayName("checkHealth() returns UP when connection.isValid(2) returns true and returns connection to pool")
    void checkHealth_shouldReturnUp_whenDatabaseConnectionIsValid() throws Exception {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(2)).thenReturn(true);

        HealthResponse response = healthService.checkHealth();

        assertEquals(HealthResponse.STATUS_UP, response.status());
        assertEquals(HealthResponse.STATUS_UP, response.database());
        assertEquals("SearchForge", response.service());
        assertEquals("1.0.0", response.version());
        assertTrue(response.isUp());
        assertNotNull(response.timestamp());

        // Verifies try-with-resources closed the connection, returning it to HikariCP pool
        verify(connection).close();
    }

    @Test
    @DisplayName("checkHealth() returns DOWN when connection.isValid(2) returns false and returns connection to pool")
    void checkHealth_shouldReturnDown_whenDatabaseConnectionIsInvalid() throws Exception {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(2)).thenReturn(false);

        HealthResponse response = healthService.checkHealth();

        assertEquals(HealthResponse.STATUS_DOWN, response.status());
        assertEquals(HealthResponse.STATUS_DOWN, response.database());
        assertFalse(response.isUp());

        // Verifies connection is still safely returned to pool on invalid state
        verify(connection).close();
    }

    @Test
    @DisplayName("checkHealth() returns DOWN and leaks no exception details when getConnection() throws SQLException")
    void checkHealth_shouldReturnDown_andNotLeakExceptionDetails_whenGetConnectionThrowsException() throws Exception {
        String sensitiveError = "FATAL: password authentication failed for user searchforge";
        when(dataSource.getConnection()).thenThrow(new SQLException(sensitiveError));

        HealthResponse response = healthService.checkHealth();

        assertEquals(HealthResponse.STATUS_DOWN, response.status());
        assertEquals(HealthResponse.STATUS_DOWN, response.database());
        assertFalse(response.isUp());

        // Assert HealthResponse string representation does not leak any exception text
        String responseString = response.toString();
        assertFalse(responseString.contains("FATAL"), "HealthResponse.toString() leaked 'FATAL'");
        assertFalse(responseString.contains("password"), "HealthResponse.toString() leaked 'password'");
        assertFalse(responseString.contains("SQLException"), "HealthResponse.toString() leaked 'SQLException'");
        assertFalse(responseString.contains(sensitiveError), "HealthResponse.toString() leaked raw exception message");

        // Assert fields strictly contain safe status constants
        assertEquals("DOWN", response.status());
        assertEquals("DOWN", response.database());
    }
}
