package com.example.cifixer;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Simple test to verify basic compilation and Spring context loading.
 */
@SpringBootTest
@ActiveProfiles("test")
public class SimpleCompilationTest {

    @Test
    public void contextLoads() {
        // This test will pass if the Spring context loads successfully
        assertTrue(true);
    }

    @Test
    public void basicAssertions() {
        // Test basic JUnit functionality
        assertTrue(1 + 1 == 2);
        assertTrue("test".equals("test"));
    }
}