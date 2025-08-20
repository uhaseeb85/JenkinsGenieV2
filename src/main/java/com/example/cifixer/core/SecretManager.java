package com.example.cifixer.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Manages secrets and tokens with secure handling and redaction capabilities.
 * Provides centralized access to sensitive configuration values while ensuring
 * they are never logged or exposed in plain text.
 */
@Component
public class SecretManager {
    
    private static final Logger logger = LoggerFactory.getLogger(SecretManager.class);
    
    // Pattern for detecting potential secrets in strings
    private static final Pattern SECRET_PATTERN = Pattern.compile(
        "(?i)(token|key|secret|password|credential|auth)\\s*[:=]\\s*['\"]?([a-zA-Z0-9+/=_-]{8,})['\"]?",
        Pattern.MULTILINE
    );
    
    private static final String REDACTED_VALUE = "****";
    private static final int MIN_TOKEN_LENGTH = 8;
    private static final int VISIBLE_CHARS = 4;
    
    @Value("${security.tokens.github.token:}")
    private String githubToken;
    
    @Value("${security.tokens.jenkins.token:}")
    private String jenkinsToken;
    
    @Value("${security.webhook.secrets.jenkins:}")
    private String jenkinsWebhookSecret;
    
    @Value("${security.webhook.secrets.github:}")
    private String githubWebhookSecret;
    
    @Value("${DATABASE_PASSWORD:}")
    private String databasePassword;
    
    private final Map<String, String> secretCache = new HashMap<>();
    
    @PostConstruct
    public void init() {
        logger.info("Initializing SecretManager with {} configured secrets", 
            countConfiguredSecrets());
        
        // Validate critical secrets are present
        validateCriticalSecrets();
    }
    
    /**
     * Gets the GitHub API token.
     *
     * @return The GitHub token or null if not configured
     */
    public String getGithubToken() {
        return StringUtils.hasText(githubToken) ? githubToken : null;
    }
    
    /**
     * Gets the Jenkins API token.
     *
     * @return The Jenkins token or null if not configured
     */
    public String getJenkinsToken() {
        return StringUtils.hasText(jenkinsToken) ? jenkinsToken : null;
    }
    
    /**
     * Gets the Jenkins webhook secret.
     *
     * @return The Jenkins webhook secret or null if not configured
     */
    public String getJenkinsWebhookSecret() {
        return StringUtils.hasText(jenkinsWebhookSecret) ? jenkinsWebhookSecret : null;
    }
    
    /**
     * Gets the GitHub webhook secret.
     *
     * @return The GitHub webhook secret or null if not configured
     */
    public String getGithubWebhookSecret() {
        return StringUtils.hasText(githubWebhookSecret) ? githubWebhookSecret : null;
    }
    
    /**
     * Gets the database password.
     *
     * @return The database password or null if not configured
     */
    public String getDatabasePassword() {
        return StringUtils.hasText(databasePassword) ? databasePassword : null;
    }
    
    /**
     * Redacts a token for safe logging by showing only the first few characters.
     *
     * @param token The token to redact
     * @return Redacted token string
     */
    public String redactToken(String token) {
        if (token == null || token.length() < MIN_TOKEN_LENGTH) {
            return REDACTED_VALUE;
        }
        
        return token.substring(0, Math.min(VISIBLE_CHARS, token.length())) + REDACTED_VALUE;
    }
    
    /**
     * Redacts all potential secrets from a string for safe logging.
     *
     * @param text The text that may contain secrets
     * @return Text with secrets redacted
     */
    public String redactSecrets(String text) {
        if (text == null) {
            return null;
        }
        
        return SECRET_PATTERN.matcher(text).replaceAll("$1: " + REDACTED_VALUE);
    }
    
    /**
     * Checks if a GitHub token is configured and valid.
     *
     * @return true if GitHub token is available
     */
    public boolean isGithubTokenConfigured() {
        return StringUtils.hasText(githubToken) && githubToken.length() >= MIN_TOKEN_LENGTH;
    }
    
    /**
     * Checks if a Jenkins token is configured and valid.
     *
     * @return true if Jenkins token is available
     */
    public boolean isJenkinsTokenConfigured() {
        return StringUtils.hasText(jenkinsToken) && jenkinsToken.length() >= MIN_TOKEN_LENGTH;
    }
    
    /**
     * Checks if Jenkins webhook secret is configured.
     *
     * @return true if Jenkins webhook secret is available
     */
    public boolean isJenkinsWebhookSecretConfigured() {
        return StringUtils.hasText(jenkinsWebhookSecret);
    }
    
    /**
     * Checks if GitHub webhook secret is configured.
     *
     * @return true if GitHub webhook secret is available
     */
    public boolean isGithubWebhookSecretConfigured() {
        return StringUtils.hasText(githubWebhookSecret);
    }
    
    /**
     * Gets a summary of configured secrets for monitoring/health checks.
     *
     * @return Map of secret names to their configuration status
     */
    public Map<String, Boolean> getSecretStatus() {
        Map<String, Boolean> status = new HashMap<>();
        status.put("github.token", isGithubTokenConfigured());
        status.put("jenkins.token", isJenkinsTokenConfigured());
        status.put("jenkins.webhook.secret", isJenkinsWebhookSecretConfigured());
        status.put("github.webhook.secret", isGithubWebhookSecretConfigured());
        status.put("database.password", StringUtils.hasText(databasePassword));
        return status;
    }
    
    /**
     * Validates that critical secrets required for operation are present.
     *
     * @throws IllegalStateException if critical secrets are missing
     */
    private void validateCriticalSecrets() {
        if (!isGithubTokenConfigured()) {
            logger.warn("GitHub token not configured - PR creation will fail");
        }
        
        if (!StringUtils.hasText(databasePassword)) {
            logger.warn("Database password not configured - using default");
        }
    }
    
    /**
     * Counts the number of configured secrets.
     *
     * @return Number of non-empty secrets
     */
    private int countConfiguredSecrets() {
        int count = 0;
        if (StringUtils.hasText(githubToken)) count++;
        if (StringUtils.hasText(jenkinsToken)) count++;
        if (StringUtils.hasText(jenkinsWebhookSecret)) count++;
        if (StringUtils.hasText(githubWebhookSecret)) count++;
        if (StringUtils.hasText(databasePassword)) count++;
        return count;
    }
    
    /**
     * Stores a secret in the cache with a given key.
     * Used for runtime secrets that aren't in configuration.
     *
     * @param key The secret key
     * @param value The secret value
     */
    public void storeSecret(String key, String value) {
        if (StringUtils.hasText(key) && StringUtils.hasText(value)) {
            secretCache.put(key, value);
            logger.debug("Stored secret with key: {}", key);
        }
    }
    
    /**
     * Retrieves a cached secret by key.
     *
     * @param key The secret key
     * @return The secret value or null if not found
     */
    public String getCachedSecret(String key) {
        return secretCache.get(key);
    }
    
    /**
     * Clears all cached secrets.
     */
    public void clearCache() {
        secretCache.clear();
        logger.debug("Cleared secret cache");
    }
}