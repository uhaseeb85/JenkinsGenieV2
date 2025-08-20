package com.example.cifixer.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Simple unit tests for SecretManager functionality.
 */
public class SecretManagerSimpleTest {

    private SecretManager secretManager;

    @BeforeEach
    public void setUp() {
        secretManager = new SecretManager();
    }

    @Test
    public void testRedactToken() {
        String token = "ghp_1234567890abcdef";
        String redacted = secretManager.redactToken(token);
        
        assertNotNull(redacted);
        assertNotEquals(token, redacted);
        assertTrue(redacted.contains("****"));
        assertFalse(redacted.contains("1234567890abcdef"));
    }

    @Test
    public void testRedactShortToken() {
        String shortToken = "abc123";
        String redacted = secretManager.redactToken(shortToken);
        
        assertNotNull(redacted);
        assertEquals("****", redacted);
    }

    @Test
    public void testRedactNullToken() {
        String redacted = secretManager.redactToken(null);
        
        assertNotNull(redacted);
        assertEquals("****", redacted);
    }

    @Test
    public void testRedactEmptyToken() {
        String redacted = secretManager.redactToken("");
        
        assertNotNull(redacted);
        assertEquals("****", redacted);
    }

    @Test
    public void testRedactSecrets() {
        String text = "The token is ghp_1234567890abcdef and password is secret123";
        String redacted = secretManager.redactSecrets(text);
        
        assertNotNull(redacted);
        assertNotEquals(text, redacted);
        // Should not contain the original sensitive values
        assertFalse(redacted.contains("ghp_1234567890abcdef"));
    }

    @Test
    public void testRedactSecretsWithNullInput() {
        String redacted = secretManager.redactSecrets(null);
        assertNull(redacted);
    }

    @Test
    public void testRedactSecretsWithEmptyInput() {
        String redacted = secretManager.redactSecrets("");
        assertEquals("", redacted);
    }

    @Test
    public void testRedactSecretsWithNoSecrets() {
        String text = "This is a normal text without any secrets";
        String redacted = secretManager.redactSecrets(text);
        
        assertEquals(text, redacted); // Should remain unchanged
    }
}