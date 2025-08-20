package com.example.cifixer.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Security-focused unit tests for SecretManager.
 * Tests secret handling, redaction, and validation functionality.
 */
class SecretManagerTest {
    
    private SecretManager secretManager;
    
    @BeforeEach
    void setUp() {
        secretManager = new SecretManager();
    }
    
    @Test
    void shouldRedactTokenCorrectly() {
        // Given
        String token = "ghp_1234567890abcdef";
        
        // When
        String redacted = secretManager.redactToken(token);
        
        // Then
        assertThat(redacted).isEqualTo("ghp_****");
        assertThat(redacted).doesNotContain("1234567890abcdef");
    }
    
    @Test
    void shouldRedactShortTokens() {
        // Given
        String shortToken = "abc123";
        
        // When
        String redacted = secretManager.redactToken(shortToken);
        
        // Then
        assertThat(redacted).isEqualTo("****");
    }
    
    @Test
    void shouldHandleNullToken() {
        // When
        String redacted = secretManager.redactToken(null);
        
        // Then
        assertThat(redacted).isEqualTo("****");
    }
    
    @Test
    void shouldRedactSecretsInText() {
        // Given
        String text = "GitHub token: ghp_1234567890abcdef\nPassword = secret123\nAPI_KEY=\"abc123def456\"";
        
        // When
        String redacted = secretManager.redactSecrets(text);
        
        // Then
        assertThat(redacted).contains("token: ****");
        assertThat(redacted).contains("Password: ****");
        assertThat(redacted).contains("API_KEY: ****");
        assertThat(redacted).doesNotContain("ghp_1234567890abcdef");
        assertThat(redacted).doesNotContain("secret123");
        assertThat(redacted).doesNotContain("abc123def456");
    }
    
    @Test
    void shouldHandleNullTextInRedaction() {
        // When
        String redacted = secretManager.redactSecrets(null);
        
        // Then
        assertThat(redacted).isNull();
    }
    
    @Test
    void shouldDetectConfiguredGitHubToken() {
        // Given
        ReflectionTestUtils.setField(secretManager, "githubToken", "ghp_validtoken123");
        
        // When
        boolean configured = secretManager.isGithubTokenConfigured();
        
        // Then
        assertThat(configured).isTrue();
    }
    
    @Test
    void shouldDetectMissingGitHubToken() {
        // Given
        ReflectionTestUtils.setField(secretManager, "githubToken", "");
        
        // When
        boolean configured = secretManager.isGithubTokenConfigured();
        
        // Then
        assertThat(configured).isFalse();
    }
    
    @Test
    void shouldDetectShortGitHubToken() {
        // Given
        ReflectionTestUtils.setField(secretManager, "githubToken", "short");
        
        // When
        boolean configured = secretManager.isGithubTokenConfigured();
        
        // Then
        assertThat(configured).isFalse();
    }
    
    @Test
    void shouldReturnGitHubTokenWhenConfigured() {
        // Given
        String token = "ghp_validtoken123";
        ReflectionTestUtils.setField(secretManager, "githubToken", token);
        
        // When
        String retrieved = secretManager.getGithubToken();
        
        // Then
        assertThat(retrieved).isEqualTo(token);
    }
    
    @Test
    void shouldReturnNullWhenGitHubTokenNotConfigured() {
        // Given
        ReflectionTestUtils.setField(secretManager, "githubToken", "");
        
        // When
        String retrieved = secretManager.getGithubToken();
        
        // Then
        assertThat(retrieved).isNull();
    }
    
    @Test
    void shouldDetectConfiguredJenkinsWebhookSecret() {
        // Given
        ReflectionTestUtils.setField(secretManager, "jenkinsWebhookSecret", "webhook_secret_123");
        
        // When
        boolean configured = secretManager.isJenkinsWebhookSecretConfigured();
        
        // Then
        assertThat(configured).isTrue();
    }
    
    @Test
    void shouldDetectMissingJenkinsWebhookSecret() {
        // Given
        ReflectionTestUtils.setField(secretManager, "jenkinsWebhookSecret", "");
        
        // When
        boolean configured = secretManager.isJenkinsWebhookSecretConfigured();
        
        // Then
        assertThat(configured).isFalse();
    }
    
    @Test
    void shouldProvideSecretStatus() {
        // Given
        ReflectionTestUtils.setField(secretManager, "githubToken", "ghp_validtoken123");
        ReflectionTestUtils.setField(secretManager, "jenkinsToken", "");
        ReflectionTestUtils.setField(secretManager, "jenkinsWebhookSecret", "webhook_secret");
        ReflectionTestUtils.setField(secretManager, "githubWebhookSecret", "");
        ReflectionTestUtils.setField(secretManager, "databasePassword", "dbpass123");
        
        // When
        Map<String, Boolean> status = secretManager.getSecretStatus();
        
        // Then
        assertThat(status).containsEntry("github.token", true);
        assertThat(status).containsEntry("jenkins.token", false);
        assertThat(status).containsEntry("jenkins.webhook.secret", true);
        assertThat(status).containsEntry("github.webhook.secret", false);
        assertThat(status).containsEntry("database.password", true);
    }
    
    @Test
    void shouldStoreCachedSecret() {
        // Given
        String key = "runtime.secret";
        String value = "secret_value_123";
        
        // When
        secretManager.storeSecret(key, value);
        String retrieved = secretManager.getCachedSecret(key);
        
        // Then
        assertThat(retrieved).isEqualTo(value);
    }
    
    @Test
    void shouldNotStoreCachedSecretWithEmptyKey() {
        // Given
        String key = "";
        String value = "secret_value_123";
        
        // When
        secretManager.storeSecret(key, value);
        String retrieved = secretManager.getCachedSecret(key);
        
        // Then
        assertThat(retrieved).isNull();
    }
    
    @Test
    void shouldNotStoreCachedSecretWithEmptyValue() {
        // Given
        String key = "runtime.secret";
        String value = "";
        
        // When
        secretManager.storeSecret(key, value);
        String retrieved = secretManager.getCachedSecret(key);
        
        // Then
        assertThat(retrieved).isNull();
    }
    
    @Test
    void shouldClearCachedSecrets() {
        // Given
        secretManager.storeSecret("secret1", "value1");
        secretManager.storeSecret("secret2", "value2");
        
        // When
        secretManager.clearCache();
        
        // Then
        assertThat(secretManager.getCachedSecret("secret1")).isNull();
        assertThat(secretManager.getCachedSecret("secret2")).isNull();
    }
    
    @Test
    void shouldReturnNullForNonExistentCachedSecret() {
        // When
        String retrieved = secretManager.getCachedSecret("nonexistent");
        
        // Then
        assertThat(retrieved).isNull();
    }
}