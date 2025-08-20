package com.example.cifixer.web;

import com.example.cifixer.core.SecretManager;
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
import java.time.Instant;
import java.util.Arrays;

/**
 * Validates webhook payloads and signatures for security.
 * Supports multiple signature algorithms and provides comprehensive validation.
 */
@Component
public class WebhookValidator {
    
    private static final Logger logger = LoggerFactory.getLogger(WebhookValidator.class);
    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final String HMAC_SHA1 = "HmacSHA1";
    
    // Supported signature algorithms
    private static final String[] SUPPORTED_ALGORITHMS = {HMAC_SHA256, HMAC_SHA1};
    
    // Maximum allowed timestamp difference (5 minutes)
    private static final long MAX_TIMESTAMP_DIFF_SECONDS = 300;
    
    @Value("${security.webhook.signature.validation.enabled:false}")
    private boolean signatureValidationEnabled;
    
    @Value("${security.webhook.signature.algorithm:HmacSHA256}")
    private String signatureAlgorithm;
    
    private final ObjectMapper objectMapper;
    private final SecretManager secretManager;
    
    public WebhookValidator(ObjectMapper objectMapper, SecretManager secretManager) {
        this.objectMapper = objectMapper;
        this.secretManager = secretManager;
    }
    
    /**
     * Validates Jenkins webhook payload and signature.
     *
     * @param payload The webhook payload
     * @param signature The signature header (optional)
     * @param timestamp The timestamp header (optional)
     * @throws ValidationException if validation fails
     */
    public void validateJenkinsPayload(JenkinsWebhookPayload payload, String signature, String timestamp) {
        validateJenkinsPayload(payload, signature);
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
        String jenkinsSecret = secretManager.getJenkinsWebhookSecret();
        if (signatureValidationEnabled && StringUtils.hasText(jenkinsSecret)) {
            validateSignature(payload, signature, jenkinsSecret, "jenkins");
        } else if (signatureValidationEnabled) {
            logger.warn("Signature validation is enabled but no Jenkins secret is configured");
        }
    }
    
    /**
     * Validates GitHub webhook payload and signature.
     *
     * @param payload The webhook payload (as raw string)
     * @param signature The signature header
     * @param timestamp The timestamp header (optional)
     * @throws ValidationException if validation fails
     */
    public void validateGithubWebhook(String payload, String signature, String timestamp) {
        String githubSecret = secretManager.getGithubWebhookSecret();
        
        if (signatureValidationEnabled && StringUtils.hasText(githubSecret)) {
            validateRawSignature(payload, signature, githubSecret, "github");
            
            // Validate timestamp if provided
            if (StringUtils.hasText(timestamp)) {
                validateTimestamp(timestamp);
            }
        } else if (signatureValidationEnabled) {
            logger.warn("Signature validation is enabled but no GitHub secret is configured");
        }
    }
    
    /**
     * Validates HMAC signature for webhook payload.
     *
     * @param payload The webhook payload
     * @param signature The signature header
     * @param secret The webhook secret
     * @param source The source system (for logging)
     * @throws SecurityException if signature validation fails
     */
    private void validateSignature(JenkinsWebhookPayload payload, String signature, String secret, String source) {
        if (signature == null || signature.trim().isEmpty()) {
            throw new SecurityException("Signature header is required when validation is enabled");
        }
        
        try {
            // Convert payload to JSON string for signature calculation
            String payloadJson = objectMapper.writeValueAsString(payload);
            validateRawSignature(payloadJson, signature, secret, source);
            
        } catch (Exception e) {
            logger.error("Error validating {} webhook signature: {}", source, secretManager.redactSecrets(e.getMessage()));
            throw new SecurityException("Signature validation failed");
        }
    }
    
    /**
     * Validates HMAC signature for raw payload string.
     *
     * @param payload The raw payload string
     * @param signature The signature header
     * @param secret The webhook secret
     * @param source The source system (for logging)
     * @throws SecurityException if signature validation fails
     */
    private void validateRawSignature(String payload, String signature, String secret, String source) {
        if (signature == null || signature.trim().isEmpty()) {
            throw new SecurityException("Signature header is required when validation is enabled");
        }
        
        try {
            // Parse signature header to extract algorithm and signature
            SignatureInfo sigInfo = parseSignatureHeader(signature);
            
            // Calculate expected signature using the detected algorithm
            String expectedSignature = calculateHmac(payload, secret, sigInfo.algorithm);
            
            // Perform constant-time comparison
            if (!constantTimeEquals(expectedSignature, sigInfo.signature)) {
                logger.warn("Invalid {} webhook signature - expected algorithm: {}", 
                    source, sigInfo.algorithm);
                throw new SecurityException("Invalid webhook signature");
            }
            
            logger.debug("{} webhook signature validation successful using {}", source, sigInfo.algorithm);
            
        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Error validating {} webhook signature: {}", source, e.getMessage());
            throw new SecurityException("Signature validation failed");
        }
    }
    
    /**
     * Validates timestamp to prevent replay attacks.
     *
     * @param timestamp The timestamp header
     * @throws SecurityException if timestamp is invalid or too old
     */
    private void validateTimestamp(String timestamp) {
        try {
            long providedTimestamp = Long.parseLong(timestamp);
            long currentTimestamp = Instant.now().getEpochSecond();
            long diff = Math.abs(currentTimestamp - providedTimestamp);
            
            if (diff > MAX_TIMESTAMP_DIFF_SECONDS) {
                throw new SecurityException("Webhook timestamp is too old or too far in the future");
            }
            
            logger.debug("Webhook timestamp validation successful");
            
        } catch (NumberFormatException e) {
            throw new SecurityException("Invalid timestamp format");
        }
    }
    
    /**
     * Parses signature header to extract algorithm and signature value.
     *
     * @param signatureHeader The signature header value
     * @return SignatureInfo containing algorithm and signature
     */
    private SignatureInfo parseSignatureHeader(String signatureHeader) {
        // Handle different signature formats:
        // GitHub: "sha256=abc123..."
        // Jenkins: "sha256=abc123..." or just "abc123..."
        
        if (signatureHeader.contains("=")) {
            String[] parts = signatureHeader.split("=", 2);
            String algorithm = mapAlgorithmName(parts[0].toLowerCase());
            return new SignatureInfo(algorithm, parts[1]);
        } else {
            // Default to configured algorithm if no prefix
            return new SignatureInfo(signatureAlgorithm, signatureHeader);
        }
    }
    
    /**
     * Maps algorithm names to Java crypto algorithm names.
     *
     * @param algorithmName The algorithm name from header
     * @return Java crypto algorithm name
     */
    private String mapAlgorithmName(String algorithmName) {
        switch (algorithmName) {
            case "sha256":
                return HMAC_SHA256;
            case "sha1":
                return HMAC_SHA1;
            default:
                return signatureAlgorithm; // Use configured default
        }
    }
    
    /**
     * Performs constant-time string comparison to prevent timing attacks.
     *
     * @param expected The expected signature
     * @param provided The provided signature
     * @return true if signatures match
     */
    private boolean constantTimeEquals(String expected, String provided) {
        if (expected == null || provided == null) {
            return false;
        }
        
        byte[] expectedBytes = expected.getBytes(StandardCharsets.UTF_8);
        byte[] providedBytes = provided.getBytes(StandardCharsets.UTF_8);
        
        return MessageDigest.isEqual(expectedBytes, providedBytes);
    }
    
    /**
     * Calculates HMAC signature for the given payload using specified algorithm.
     *
     * @param payload The payload to sign
     * @param secret The secret key
     * @param algorithm The HMAC algorithm to use
     * @return The hex-encoded signature
     */
    private String calculateHmac(String payload, String secret, String algorithm) 
            throws NoSuchAlgorithmException, InvalidKeyException {
        
        // Validate algorithm is supported
        if (!Arrays.asList(SUPPORTED_ALGORITHMS).contains(algorithm)) {
            throw new IllegalArgumentException("Unsupported algorithm: " + algorithm);
        }
        
        Mac mac = Mac.getInstance(algorithm);
        SecretKeySpec secretKeySpec = new SecretKeySpec(
            secret.getBytes(StandardCharsets.UTF_8), algorithm);
        mac.init(secretKeySpec);
        
        byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        
        return bytesToHex(hash);
    }
    
    /**
     * Converts byte array to hex string.
     *
     * @param bytes The byte array
     * @return Hex string representation
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
    
    /**
     * Helper class to hold signature information.
     */
    private static class SignatureInfo {
        final String algorithm;
        final String signature;
        
        SignatureInfo(String algorithm, String signature) {
            this.algorithm = algorithm;
            this.signature = signature;
        }
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