package com.searchforge;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import javax.sql.DataSource;

@SpringBootTest
class SearchForgeApplicationTests {

    @MockitoBean
    private DataSource dataSource;

    @Test
    @DisplayName("Context loads successfully - proves Spring bean wiring without requiring live DB")
    void contextLoads() {
        // This test proves Spring Boot dependency injection and bean wiring only, not DB connectivity.
    }
}
