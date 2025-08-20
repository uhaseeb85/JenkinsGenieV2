package com.example.cifixer.web;

import com.example.cifixer.core.SecretManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Security-focused unit tests for WebhookValidator.
 * Tests signature validation, payload validation, and security features.
 */
@ExtendWith(MockitoExtension.class)
class WebhookValidatorTest {
    
    @Mock
    private SecretManager secretManager;
    
    private WebhookValidator webhookValidator;
    private ObjectMapper objectMapper;
    
    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        webhookValidator = new WebhookValidator(objectMapper, secretManager);
    }
    
    @Test
    void shouldValidateValidJenkinsPayload() {
        // Given
        JenkinsWebhookPayload payload = createValidPayload();
        
        // When & Then - should not throw
        webhookValidator.validateJenkinsPayload(payload, null);
    }
    
    @Test
    void shouldRejectPayloadWithMissingJob() {
        // Given
        JenkinsWebhookPayload payload = createValidPayload();
        payload.setJob(null);
        
        // When & Then
        assertThatThrownBy(() -> webhookValidator.validateJenkinsPayload(payload, null))
            .isInstanceOf(WebhookValidator.ValidationException.class)
            .hasMessageContaining("Job name is required");
    }
    
    @Test
    void shouldRejectPayloadWithEmptyJob() {
        // Given
        JenkinsWebhookPayload payload = createValidPayload();
        payload.setJob("   ");
        
        // When & Then
        assertThatThrownBy(() -> webhookValidator.validateJenkinsPayload(payload, null))
            .isInstanceOf(WebhookValidator.ValidationException.class)
            .hasMessageContaining("Job name is required");
    }
    
    @Test
    void shouldRejectPayloadWithMissingBuildNumber() {
        // Given
        JenkinsWebhookPayload payload = createValidPayload();
        payload.setBuildNumber(null);
        
        // When & Then
        assertThatThrownBy(() -> webhookValidator.validateJenkinsPayload(payload, null))
            .isInstanceOf(WebhookValidator.ValidationException.class)
            .hasMessageContaining("Build number is required");
    }
    
    @Test
    void shouldRejectPayloadWithMissingBranch() {
        // Given
        JenkinsWebhookPayload payload = createValidPayload();
        payload.setBranch(null);
        
        // When & Then
        assertThatThrownBy(() -> webhookValidator.validateJenkinsPayload(payload, null))
            .isInstanceOf(WebhookValidator.ValidationException.class)
            .hasMessageContaining("Branch is required");
    }
    
    @Test
    void shouldRejectPayloadWithMissingRepoUrl() {
        // Given
        JenkinsWebhookPayload payload = createValidPayload();
        payload.setRepoUrl(null);
        
        // When & Then
        assertThatThrownBy(() -> webhookValidator.validateJenkinsPayload(payload, null))
            .isInstanceOf(WebhookValidator.ValidationException.class)
            .hasMessageContaining("Repository URL is required");
    }
    
    @Test
    void shouldRejectPayloadWithMissingCommitSha() {
        // Given
        JenkinsWebhookPayload payload = createValidPayload();
        payload.setCommitSha(null);
        
        // When & Then
        assertThatThrownBy(() -> webhookValidator.validateJenkinsPayload(payload, null))
            .isInstanceOf(WebhookValidator.ValidationException.class)
            .hasMessageContaining("Commit SHA is required");
    }
    
    @Test
    void shouldValidateCorrectHmacSha256Signature() throws Exception {
        // Given
        enableSignatureValidation();
        String secret = "webhook_secret_123";
        when(secretManager.getJenkinsWebhookSecret()).thenReturn(secret);
        
        JenkinsWebhookPayload payload = createValidPayload();
        String payloadJson = objectMapper.writeValueAsString(payload);
        String signature = "sha256=" + calculateHmacSha256(payloadJson, secret);
        
        // When & Then - should not throw
        webhookValidator.validateJenkinsPayload(payload, signature);
    }
    
    @Test
    void shouldRejectIncorrectHmacSha256Signature() throws Exception {
        // Given
        enableSignatureValidation();
        String secret = "webhook_secret_123";
        when(secretManager.getJenkinsWebhookSecret()).thenReturn(secret);
        
        JenkinsWebhookPayload payload = createValidPayload();
        String signature = "sha256=invalid_signature";
        
        // When & Then
        assertThatThrownBy(() -> webhookValidator.validateJenkinsPayload(payload, signature))
            .isInstanceOf(SecurityException.class)
            .hasMessageContaining("Invalid webhook signature");
    }
    
    @Test
    void shouldRejectMissingSignatureWhenValidationEnabled() {
        // Given
        enableSignatureValidation();
        when(secretManager.getJenkinsWebhookSecret()).thenReturn("secret");
        
        JenkinsWebhookPayload payload = createValidPayload();
        
        // When & Then
        assertThatThrownBy(() -> webhookValidator.validateJenkinsPayload(payload, null))
            .isInstanceOf(SecurityException.class)
            .hasMessageContaining("Signature header is required");
    }
    
    @Test
    void shouldRejectEmptySignatureWhenValidationEnabled() {
        // Given
        enableSignatureValidation();
        when(secretManager.getJenkinsWebhookSecret()).thenReturn("secret");
        
        JenkinsWebhookPayload payload = createValidPayload();
        
        // When & Then
        assertThatThrownBy(() -> webhookValidator.validateJenkinsPayload(payload, ""))
            .isInstanceOf(SecurityException.class)
            .hasMessageContaining("Signature header is required");
    }
    
    @Test
    void shouldSkipSignatureValidationWhenDisabled() {
        // Given
        disableSignatureValidation();
        JenkinsWebhookPayload payload = createValidPayload();
        
        // When & Then - should not throw even with invalid signature
        webhookValidator.validateJenkinsPayload(payload, "invalid_signature");
    }
    
    @Test
    void shouldSkipSignatureValidationWhenNoSecretConfigured() {
        // Given
        enableSignatureValidation();
        when(secretManager.getJenkinsWebhookSecret()).thenReturn(null);
        
        JenkinsWebhookPayload payload = createValidPayload();
        
        // When & Then - should not throw even with invalid signature
        webhookValidator.validateJenkinsPayload(payload, "invalid_signature");
    }
    
    @Test
    void shouldHandleSignatureWithoutPrefix() throws Exception {
        // Given
        enableSignatureValidation();
        String secret = "webhook_secret_123";
        when(secretManager.getJenkinsWebhookSecret()).thenReturn(secret);
        
        JenkinsWebhookPayload payload = createValidPayload();
        String payloadJson = objectMapper.writeValueAsString(payload);
        String signature = calculateHmacSha256(payloadJson, secret); // No "sha256=" prefix
        
        // When & Then - should not throw
        webhookValidator.validateJenkinsPayload(payload, signature);
    }
    
    @Test
    void shouldValidateGithubWebhookWithCorrectSignature() throws Exception {
        // Given
        enableSignatureValidation();
        String secret = "github_secret_123";
        when(secretManager.getGithubWebhookSecret()).thenReturn(secret);
        
        String payload = "{\"action\":\"opened\",\"number\":1}";
        String signature = "sha256=" + calculateHmacSha256(payload, secret);
        
        // When & Then - should not throw
        webhookValidator.validateGithubWebhook(payload, signature, null);
    }
    
    @Test
    void shouldRejectGithubWebhookWithIncorrectSignature() {
        // Given
        enableSignatureValidation();
        when(secretManager.getGithubWebhookSecret()).thenReturn("secret");
        
        String payload = "{\"action\":\"opened\",\"number\":1}";
        String signature = "sha256=invalid_signature";
        
        // When & Then
        assertThatThrownBy(() -> webhookValidator.validateGithubWebhook(payload, signature, null))
            .isInstanceOf(SecurityException.class)
            .hasMessageContaining("Invalid webhook signature");
    }
    
    @Test
    void shouldValidateTimestampWithinAllowedRange() {
        // Given
        enableSignatureValidation();
        when(secretManager.getGithubWebhookSecret()).thenReturn("secret");
        
        String payload = "{\"action\":\"opened\"}";
        String signature = "sha256=dummy"; // Will fail signature check, but timestamp should be validated first
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000); // Current time
        
        // When & Then - Should fail on signature, not timestamp
        assertThatThrownBy(() -> webhookValidator.validateGithubWebhook(payload, signature, timestamp))
            .isInstanceOf(SecurityException.class)
            .hasMessageContaining("Invalid webhook signature");
    }
    
    @Test
    void shouldRejectOldTimestamp() {
        // Given
        enableSignatureValidation();
        when(secretManager.getGithubWebhookSecret()).thenReturn("secret");
        
        String payload = "{\"action\":\"opened\"}";
        String signature = "sha256=dummy";
        String timestamp = String.valueOf((System.currentTimeMillis() / 1000) - 400); // 400 seconds ago
        
        // When & Then
        assertThatThrownBy(() -> webhookValidator.validateGithubWebhook(payload, signature, timestamp))
            .isInstanceOf(SecurityException.class)
            .hasMessageContaining("timestamp is too old");
    }
    
    @Test
    void shouldRejectFutureTimestamp() {
        // Given
        enableSignatureValidation();
        when(secretManager.getGithubWebhookSecret()).thenReturn("secret");
        
        String payload = "{\"action\":\"opened\"}";
        String signature = "sha256=dummy";
        String timestamp = String.valueOf((System.currentTimeMillis() / 1000) + 400); // 400 seconds in future
        
        // When & Then
        assertThatThrownBy(() -> webhookValidator.validateGithubWebhook(payload, signature, timestamp))
            .isInstanceOf(SecurityException.class)
            .hasMessageContaining("timestamp is too old");
    }
    
    @Test
    void shouldRejectInvalidTimestampFormat() {
        // Given
        enableSignatureValidation();
        when(secretManager.getGithubWebhookSecret()).thenReturn("secret");
        
        String payload = "{\"action\":\"opened\"}";
        String signature = "sha256=dummy";
        String timestamp = "invalid_timestamp";
        
        // When & Then
        assertThatThrownBy(() -> webhookValidator.validateGithubWebhook(payload, signature, timestamp))
            .isInstanceOf(SecurityException.class)
            .hasMessageContaining("Invalid timestamp format");
    }
    
    private JenkinsWebhookPayload createValidPayload() {
        JenkinsWebhookPayload payload = new JenkinsWebhookPayload();
        payload.setJob("test-job");
        payload.setBuildNumber(123);
        payload.setBranch("main");
        payload.setRepoUrl("https://github.com/example/repo.git");
        payload.setCommitSha("abc123def456");
        payload.setBuildLogs("Build failed with errors");
        return payload;
    }
    
    private void enableSignatureValidation() {
        ReflectionTestUtils.setField(webhookValidator, "signatureValidationEnabled", true);
    }
    
    private void disableSignatureValidation() {
        ReflectionTestUtils.setField(webhookValidator, "signatureValidationEnabled", false);
    }
    
    private String calculateHmacSha256(String payload, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(secretKeySpec);
        
        byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        
        return hexString.toString();
    }
}