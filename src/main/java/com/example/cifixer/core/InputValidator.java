package com.example.cifixer.core;

import com.example.cifixer.web.JenkinsWebhookPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Comprehensive input validation for webhook payloads, file paths, and other user inputs.
 */
@Component
public class InputValidator {
    
    private static final Logger logger = LoggerFactory.getLogger(InputValidator.class);
    
    // Validation patterns
    private static final Pattern VALID_JOB_NAME = Pattern.compile("^[a-zA-Z0-9._-]+$");
    private static final Pattern VALID_BRANCH_NAME = Pattern.compile("^[a-zA-Z0-9._/-]+$");
    private static final Pattern VALID_COMMIT_SHA = Pattern.compile("^[a-fA-F0-9]{7,40}$");
    private static final Pattern VALID_BUILD_NUMBER = Pattern.compile("^[1-9]\\d{0,8}$"); // 1-999999999
    
    // Allowed URL schemes for repository URLs
    private static final Set<String> ALLOWED_URL_SCHEMES = new HashSet<>(Arrays.asList(
        "https", "http", "git", "ssh"
    ));
    
    // Blocked domains/IPs for security
    private static final Set<String> BLOCKED_DOMAINS = new HashSet<>(Arrays.asList(
        "localhost", "127.0.0.1", "0.0.0.0", "::1", "169.254.169.254"
    ));
    
    // Maximum lengths for various fields
    private static final int MAX_JOB_NAME_LENGTH = 100;
    private static final int MAX_BRANCH_NAME_LENGTH = 200;
    private static final int MAX_REPO_URL_LENGTH = 500;
    private static final int MAX_LOG_SIZE = 1024 * 1024; // 1MB
    
    /**
     * Validates a Jenkins webhook payload comprehensively.
     */
    public ValidationResult validateJenkinsPayload(JenkinsWebhookPayload payload) {
        if (payload == null) {
            return ValidationResult.invalid("Webhook payload is null");
        }
        
        logger.debug("Validating Jenkins webhook payload");
        
        // Validate job name
        String jobName = payload.getJob();
        ValidationResult jobResult = validateJobName(jobName);
        if (!jobResult.isValid()) {
            return jobResult;
        }
        
        // Validate build number
        Integer buildNumber = payload.getBuildNumber();
        ValidationResult buildResult = validateBuildNumber(buildNumber);
        if (!buildResult.isValid()) {
            return buildResult;
        }
        
        // Validate branch name
        String branchName = payload.getBranch();
        ValidationResult branchResult = validateBranchName(branchName);
        if (!branchResult.isValid()) {
            return branchResult;
        }
        
        // Validate repository URL
        String repoUrl = payload.getRepoUrl();
        ValidationResult repoResult = validateRepositoryUrl(repoUrl);
        if (!repoResult.isValid()) {
            return repoResult;
        }
        
        // Validate commit SHA
        String commitSha = payload.getCommitSha();
        ValidationResult commitResult = validateCommitSha(commitSha);
        if (!commitResult.isValid()) {
            return commitResult;
        }
        
        // Validate build logs if present
        String buildLogs = payload.getBuildLogs();
        if (buildLogs != null) {
            ValidationResult logsResult = validateBuildLogs(buildLogs);
            if (!logsResult.isValid()) {
                return logsResult;
            }
        }
        
        logger.debug("Jenkins webhook payload validation successful");
        return ValidationResult.valid();
    }
    
    /**
     * Validates a job name.
     */
    private ValidationResult validateJobName(String jobName) {
        if (!StringUtils.hasText(jobName)) {
            return ValidationResult.invalid("Job name is required");
        }
        
        if (jobName.length() > MAX_JOB_NAME_LENGTH) {
            return ValidationResult.invalid("Job name too long: " + jobName.length() + " (max: " + MAX_JOB_NAME_LENGTH + ")");
        }
        
        if (!VALID_JOB_NAME.matcher(jobName).matches()) {
            return ValidationResult.invalid("Invalid job name format: " + jobName);
        }
        
        return ValidationResult.valid();
    }
    
    /**
     * Validates a build number.
     */
    private ValidationResult validateBuildNumber(Integer buildNumber) {
        if (buildNumber == null) {
            return ValidationResult.invalid("Build number is required");
        }
        
        if (buildNumber <= 0) {
            return ValidationResult.invalid("Build number must be positive: " + buildNumber);
        }
        
        if (!VALID_BUILD_NUMBER.matcher(buildNumber.toString()).matches()) {
            return ValidationResult.invalid("Invalid build number: " + buildNumber);
        }
        
        return ValidationResult.valid();
    }
    
    /**
     * Validates a branch name.
     */
    private ValidationResult validateBranchName(String branchName) {
        if (!StringUtils.hasText(branchName)) {
            return ValidationResult.invalid("Branch name is required");
        }
        
        if (branchName.length() > MAX_BRANCH_NAME_LENGTH) {
            return ValidationResult.invalid("Branch name too long: " + branchName.length() + " (max: " + MAX_BRANCH_NAME_LENGTH + ")");
        }
        
        if (!VALID_BRANCH_NAME.matcher(branchName).matches()) {
            return ValidationResult.invalid("Invalid branch name format: " + branchName);
        }
        
        // Check for dangerous branch names
        if (branchName.contains("..") || branchName.startsWith("/") || branchName.endsWith("/")) {
            return ValidationResult.invalid("Unsafe branch name: " + branchName);
        }
        
        return ValidationResult.valid();
    }
    
    /**
     * Validates a repository URL.
     */
    private ValidationResult validateRepositoryUrl(String repoUrl) {
        if (!StringUtils.hasText(repoUrl)) {
            return ValidationResult.invalid("Repository URL is required");
        }
        
        if (repoUrl.length() > MAX_REPO_URL_LENGTH) {
            return ValidationResult.invalid("Repository URL too long: " + repoUrl.length() + " (max: " + MAX_REPO_URL_LENGTH + ")");
        }
        
        try {
            URL url = new URL(repoUrl);
            
            // Check allowed schemes
            if (!ALLOWED_URL_SCHEMES.contains(url.getProtocol().toLowerCase())) {
                return ValidationResult.invalid("URL scheme not allowed: " + url.getProtocol());
            }
            
            // Check for blocked domains
            String host = url.getHost();
            if (host != null && BLOCKED_DOMAINS.contains(host.toLowerCase())) {
                return ValidationResult.invalid("Repository host not allowed: " + host);
            }
            
            // Check for private IP ranges (basic check)
            if (host != null && isPrivateIpAddress(host)) {
                return ValidationResult.invalid("Private IP addresses not allowed: " + host);
            }
            
        } catch (MalformedURLException e) {
            return ValidationResult.invalid("Invalid repository URL format: " + e.getMessage());
        }
        
        return ValidationResult.valid();
    }
    
    /**
     * Validates a commit SHA.
     */
    private ValidationResult validateCommitSha(String commitSha) {
        if (!StringUtils.hasText(commitSha)) {
            return ValidationResult.invalid("Commit SHA is required");
        }
        
        if (!VALID_COMMIT_SHA.matcher(commitSha).matches()) {
            return ValidationResult.invalid("Invalid commit SHA format: " + commitSha);
        }
        
        return ValidationResult.valid();
    }
    
    /**
     * Validates build logs.
     */
    private ValidationResult validateBuildLogs(String buildLogs) {
        if (buildLogs.length() > MAX_LOG_SIZE) {
            return ValidationResult.invalid("Build logs too large: " + buildLogs.length() + " bytes (max: " + MAX_LOG_SIZE + ")");
        }
        
        // Check for suspicious content in logs
        String lowerLogs = buildLogs.toLowerCase();
        if (lowerLogs.contains("password") || lowerLogs.contains("secret") || 
            lowerLogs.contains("token") || lowerLogs.contains("credential")) {
            logger.warn("Build logs contain potentially sensitive information");
        }
        
        return ValidationResult.valid();
    }
    
    /**
     * Validates a file path for safety and correctness.
     */
    public ValidationResult validateFilePath(String filePath) {
        if (!StringUtils.hasText(filePath)) {
            return ValidationResult.invalid("File path is required");
        }
        
        try {
            Path path = Paths.get(filePath).normalize();
            String normalizedPath = path.toString().replace('\\', '/');
            
            // Check for directory traversal
            if (normalizedPath.contains("..") || normalizedPath.startsWith("/") || 
                normalizedPath.contains("~") || normalizedPath.contains("$")) {
                return ValidationResult.invalid("Unsafe file path - directory traversal detected: " + filePath);
            }
            
            // Check for null bytes
            if (filePath.contains("\0")) {
                return ValidationResult.invalid("File path contains null bytes: " + filePath);
            }
            
            // Check path length
            if (normalizedPath.length() > 260) { // Windows MAX_PATH limit
                return ValidationResult.invalid("File path too long: " + normalizedPath.length() + " (max: 260)");
            }
            
            // Check for reserved names (Windows)
            String fileName = path.getFileName().toString().toLowerCase();
            if (isReservedFileName(fileName)) {
                return ValidationResult.invalid("Reserved file name: " + fileName);
            }
            
            return ValidationResult.valid();
            
        } catch (InvalidPathException e) {
            return ValidationResult.invalid("Invalid file path: " + e.getMessage());
        }
    }
    
    /**
     * Validates a working directory path.
     */
    public ValidationResult validateWorkingDirectory(String workingDir) {
        ValidationResult pathResult = validateFilePath(workingDir);
        if (!pathResult.isValid()) {
            return pathResult;
        }
        
        // Additional checks for working directories
        if (!workingDir.startsWith("/work/") && !workingDir.startsWith("work/")) {
            return ValidationResult.invalid("Working directory must be under /work/: " + workingDir);
        }
        
        return ValidationResult.valid();
    }
    
    /**
     * Validates a build ID for safety.
     */
    public ValidationResult validateBuildId(Long buildId) {
        if (buildId == null) {
            return ValidationResult.invalid("Build ID is required");
        }
        
        if (buildId <= 0) {
            return ValidationResult.invalid("Build ID must be positive: " + buildId);
        }
        
        if (buildId > Long.MAX_VALUE / 2) { // Reasonable upper bound
            return ValidationResult.invalid("Build ID too large: " + buildId);
        }
        
        return ValidationResult.valid();
    }
    
    /**
     * Validates an email address format.
     */
    public ValidationResult validateEmailAddress(String email) {
        if (!StringUtils.hasText(email)) {
            return ValidationResult.invalid("Email address is required");
        }
        
        // Basic email validation
        if (!email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
            return ValidationResult.invalid("Invalid email format: " + email);
        }
        
        if (email.length() > 254) { // RFC 5321 limit
            return ValidationResult.invalid("Email address too long: " + email.length() + " (max: 254)");
        }
        
        return ValidationResult.valid();
    }
    
    /**
     * Checks if a host is a private IP address.
     */
    private boolean isPrivateIpAddress(String host) {
        // Basic check for common private IP ranges
        return host.startsWith("10.") ||
               host.startsWith("192.168.") ||
               host.startsWith("172.16.") ||
               host.startsWith("172.17.") ||
               host.startsWith("172.18.") ||
               host.startsWith("172.19.") ||
               host.startsWith("172.2") ||
               host.startsWith("172.30.") ||
               host.startsWith("172.31.");
    }
    
    /**
     * Checks if a filename is reserved (Windows reserved names).
     */
    private boolean isReservedFileName(String fileName) {
        String[] reserved = {"con", "prn", "aux", "nul", "com1", "com2", "com3", "com4", 
                           "com5", "com6", "com7", "com8", "com9", "lpt1", "lpt2", "lpt3", 
                           "lpt4", "lpt5", "lpt6", "lpt7", "lpt8", "lpt9"};
        
        String baseName = fileName.contains(".") ? fileName.substring(0, fileName.indexOf('.')) : fileName;
        
        for (String res : reserved) {
            if (res.equals(baseName)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Result of input validation.
     */
    public static class ValidationResult {
        private final boolean valid;
        private final String errorMessage;
        
        private ValidationResult(boolean valid, String errorMessage) {
            this.valid = valid;
            this.errorMessage = errorMessage;
        }
        
        public static ValidationResult valid() {
            return new ValidationResult(true, null);
        }
        
        public static ValidationResult invalid(String errorMessage) {
            return new ValidationResult(false, errorMessage);
        }
        
        public boolean isValid() {
            return valid;
        }
        
        public String getErrorMessage() {
            return errorMessage;
        }
        
        @Override
        public String toString() {
            return valid ? "Valid" : "Invalid: " + errorMessage;
        }
    }
}