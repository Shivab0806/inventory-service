package com.example.inventoryservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class InventoryServiceApplicationTests {

    @Test
    void contextLoads() {
        // Verifies the Spring context wires up correctly (JPA, Liquibase-off/H2, MVC, etc.)
    }
}
