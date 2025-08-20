package com.example.cifixer;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * Integration test to verify Spring context loads successfully.
 */
@SpringBootTest(classes = MultiAgentCiFixerApplication.class)
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.flyway.enabled=false",
    "logging.level.org.springframework=WARN",
    "logging.level.org.hibernate=WARN"
})
public class SpringContextTest {

    @Test
    public void contextLoads() {
        // This test will pass if the Spring context loads successfully
        // No additional assertions needed - context loading is the test
    }
}