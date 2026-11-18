package com.searchforge;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class SearchForgeApplicationTests {

    @Test
    @DisplayName("Context loads successfully - proves Spring bean wiring with in-memory test database")
    void contextLoads() {
        // This test proves Spring Boot dependency injection, JPA entities, and bean wiring load cleanly.
    }
}
