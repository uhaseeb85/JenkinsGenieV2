package com.example.cifixer.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Validates webhook payloads and signatures for security.
 */
@Component
public class WebhookValidator {
    
    private static final Logger logger = LoggerFactory.getLogger(WebhookValidator.class);
    private static final String HMAC_SHA256 = "HmacSHA256";
    
    @Value("${webhook.jenkins.secret:}")
    private String jenkinsSecret;
    
    @Value("${webhook.signature.validation.enabled:false}")
    private boolean signatureValidationEnabled;
    
    private final ObjectMapper objectMapper;
    
    public WebhookValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
    
    /**
     * Validates Jenkins webhook payload and signature.
     *
     * @param payload The webhook payload
     * @param signature The signature header (optional)
     * @throws ValidationException if validation fails
     */
    public void validateJenkinsPayload(JenkinsWebhookPayload payload, String signature) {
        // Validate required fields
        if (payload.getJob() == null || payload.getJob().trim().isEmpty()) {
            throw new ValidationException("Job name is required");
        }
        
        if (payload.getBuildNumber() == null) {
            throw new ValidationException("Build number is required");
        }
        
        if (payload.getBranch() == null || payload.getBranch().trim().isEmpty()) {
            throw new ValidationException("Branch is required");
        }
        
        if (payload.getRepoUrl() == null || payload.getRepoUrl().trim().isEmpty()) {
            throw new ValidationException("Repository URL is required");
        }
        
        if (payload.getCommitSha() == null || payload.getCommitSha().trim().isEmpty()) {
            throw new ValidationException("Commit SHA is required");
        }
        
        // Validate signature if enabled and secret is configured
        if (signatureValidationEnabled && StringUtils.hasText(jenkinsSecret)) {
            validateSignature(payload, signature);
        } else if (signatureValidationEnabled) {
            logger.warn("Signature validation is enabled but no secret is configured");
        }
    }
    
    /**
     * Validates HMAC signature for webhook payload.
     *
     * @param payload The webhook payload
     * @param signature The signature header
     * @throws SecurityException if signature validation fails
     */
    private void validateSignature(JenkinsWebhookPayload payload, String signature) {
        if (signature == null || signature.trim().isEmpty()) {
            throw new SecurityException("Signature header is required when validation is enabled");
        }
        
        try {
            // Convert payload to JSON string for signature calculation
            String payloadJson = objectMapper.writeValueAsString(payload);
            String expectedSignature = calculateHmacSha256(payloadJson, jenkinsSecret);
            
            // Remove "sha256=" prefix if present
            String providedSignature = signature.startsWith("sha256=") ? 
                signature.substring(7) : signature;
            
            if (!MessageDigest.isEqual(
                    expectedSignature.getBytes(StandardCharsets.UTF_8),
                    providedSignature.getBytes(StandardCharsets.UTF_8))) {
                throw new SecurityException("Invalid webhook signature");
            }
            
            logger.debug("Webhook signature validation successful");
            
        } catch (Exception e) {
            logger.error("Error validating webhook signature", e);
            throw new SecurityException("Signature validation failed: " + e.getMessage());
        }
    }
    
    /**
     * Calculates HMAC-SHA256 signature for the given payload.
     *
     * @param payload The payload to sign
     * @param secret The secret key
     * @return The hex-encoded signature
     */
    private String calculateHmacSha256(String payload, String secret) 
            throws NoSuchAlgorithmException, InvalidKeyException {
        Mac mac = Mac.getInstance(HMAC_SHA256);
        SecretKeySpec secretKeySpec = new SecretKeySpec(
            secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
        mac.init(secretKeySpec);
        
        byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        
        // Convert to hex string
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
    
    /**
     * Exception thrown when webhook validation fails.
     */
    public static class ValidationException extends RuntimeException {
        public ValidationException(String message) {
            super(message);
        }
    }
}