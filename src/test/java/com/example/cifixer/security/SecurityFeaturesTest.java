package com.example.cifixer.security;

import com.example.cifixer.core.SecretManager;
import com.example.cifixer.core.SslConfiguration;
import com.example.cifixer.web.WebhookValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for security features implemented in task 14.
 * Tests SecretManager, SslConfiguration, and WebhookValidator functionality.
 */
class SecurityFeaturesTest {
    
    @Test
    void shouldCreateSecretManagerAndRedactTokens() {
        // Given
        SecretManager secretManager = new SecretManager();
        ReflectionTestUtils.setField(secretManager, "githubToken", "ghp_1234567890abcdef");
        
        // When
        String redacted = secretManager.redactToken("ghp_1234567890abcdef");
        boolean configured = secretManager.isGithubTokenConfigured();
        
        // Then
        assertThat(redacted).isEqualTo("ghp_****");
        assertThat(configured).isTrue();
    }
    
    @Test
    void shouldCreateSslConfigurationAndHttpClients() {
        // Given
        SecretManager secretManager = new SecretManager();
        SslConfiguration sslConfiguration = new SslConfiguration(secretManager);
        
        // Set required fields
        ReflectionTestUtils.setField(sslConfiguration, "sslVerificationEnabled", true);
        ReflectionTestUtils.setField(sslConfiguration, "trustStorePath", "");
        ReflectionTestUtils.setField(sslConfiguration, "trustStorePassword", "");
        ReflectionTestUtils.setField(sslConfiguration, "githubConnectTimeout", 10000);
        ReflectionTestUtils.setField(sslConfiguration, "githubReadTimeout", 30000);
        ReflectionTestUtils.setField(sslConfiguration, "llmConnectTimeout", 5000);
        ReflectionTestUtils.setField(sslConfiguration, "llmReadTimeout", 120000);
        
        // When
        OkHttpClient githubClient = sslConfiguration.githubHttpClient();
        OkHttpClient llmClient = sslConfiguration.llmHttpClient();
        boolean validConfig = sslConfiguration.validateSslConfiguration();
        
        // Then
        assertThat(githubClient).isNotNull();
        assertThat(llmClient).isNotNull();
        assertThat(validConfig).isTrue();
        assertThat(githubClient.connectTimeoutMillis()).isEqualTo(10000);
        assertThat(llmClient.connectTimeoutMillis()).isEqualTo(5000);
    }
    
    @Test
    void shouldCreateWebhookValidatorWithSecretManager() {
        // Given
        ObjectMapper objectMapper = new ObjectMapper();
        SecretManager secretManager = new SecretManager();
        
        // When
        WebhookValidator validator = new WebhookValidator(objectMapper, secretManager);
        
        // Then
        assertThat(validator).isNotNull();
    }
    
    @Test
    void shouldRedactSecretsInText() {
        // Given
        SecretManager secretManager = new SecretManager();
        String textWithSecrets = "GitHub token: ghp_1234567890abcdef\nPassword = secret123";
        
        // When
        String redacted = secretManager.redactSecrets(textWithSecrets);
        
        // Then
        assertThat(redacted).contains("token: ****");
        assertThat(redacted).contains("Password: ****");
        assertThat(redacted).doesNotContain("ghp_1234567890abcdef");
        assertThat(redacted).doesNotContain("secret123");
    }
    

}