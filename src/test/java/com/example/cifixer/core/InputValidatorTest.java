package com.example.cifixer.core;

import com.example.cifixer.web.JenkinsWebhookPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InputValidatorTest {
    
    private InputValidator validator;
    
    @BeforeEach
    void setUp() {
        validator = new InputValidator();
    }
    
    @Test
    void shouldValidateValidJenkinsPayload() {
        // Given
        JenkinsWebhookPayload payload = createValidPayload();
        
        // When
        InputValidator.ValidationResult result = validator.validateJenkinsPayload(payload);
        
        // Then
        assertTrue(result.isValid());
    }
    
    @Test
    void shouldRejectNullPayload() {
        // When
        InputValidator.ValidationResult result = validator.validateJenkinsPayload(null);
        
        // Then
        assertFalse(result.isValid());
        assertEquals("Webhook payload is null", result.getErrorMessage());
    }
    
    @Test
    void shouldRejectInvalidJobName() {
        // Given
        JenkinsWebhookPayload payload = createValidPayload();
        payload.setJob("invalid job name with spaces!");
        
        // When
        InputValidator.ValidationResult result = validator.validateJenkinsPayload(payload);
        
        // Then
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("Invalid job name format"));
    }
    
    @Test
    void shouldRejectEmptyJobName() {
        // Given
        JenkinsWebhookPayload payload = createValidPayload();
        payload.setJob("");
        
        // When
        InputValidator.ValidationResult result = validator.validateJenkinsPayload(payload);
        
        // Then
        assertFalse(result.isValid());
        assertEquals("Job name is required", result.getErrorMessage());
    }
    
    @Test
    void shouldRejectTooLongJobName() {
        // Given
        JenkinsWebhookPayload payload = createValidPayload();
        payload.setJob("a".repeat(101)); // Exceeds MAX_JOB_NAME_LENGTH
        
        // When
        InputValidator.ValidationResult result = validator.validateJenkinsPayload(payload);
        
        // Then
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("Job name too long"));
    }
    
    @Test
    void shouldRejectNullBuildNumber() {
        // Given
        JenkinsWebhookPayload payload = createValidPayload();
        payload.setBuildNumber(null);
        
        // When
        InputValidator.ValidationResult result = validator.validateJenkinsPayload(payload);
        
        // Then
        assertFalse(result.isValid());
        assertEquals("Build number is required", result.getErrorMessage());
    }
    
    @Test
    void shouldRejectNegativeBuildNumber() {
        // Given
        JenkinsWebhookPayload payload = createValidPayload();
        payload.setBuildNumber(-1);
        
        // When
        InputValidator.ValidationResult result = validator.validateJenkinsPayload(payload);
        
        // Then
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("Build number must be positive"));
    }
    
    @Test
    void shouldRejectZeroBuildNumber() {
        // Given
        JenkinsWebhookPayload payload = createValidPayload();
        payload.setBuildNumber(0);
        
        // When
        InputValidator.ValidationResult result = validator.validateJenkinsPayload(payload);
        
        // Then
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("Build number must be positive"));
    }
    
    @Test
    void shouldRejectInvalidBranchName() {
        // Given
        JenkinsWebhookPayload payload = createValidPayload();
        payload.setBranch("../malicious-branch");
        
        // When
        InputValidator.ValidationResult result = validator.validateJenkinsPayload(payload);
        
        // Then
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("Unsafe branch name"));
    }
    
    @Test
    void shouldRejectTooLongBranchName() {
        // Given
        JenkinsWebhookPayload payload = createValidPayload();
        payload.setBranch("a".repeat(201)); // Exceeds MAX_BRANCH_NAME_LENGTH
        
        // When
        InputValidator.ValidationResult result = validator.validateJenkinsPayload(payload);
        
        // Then
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("Branch name too long"));
    }
    
    @Test
    void shouldRejectInvalidRepositoryUrl() {
        // Given
        JenkinsWebhookPayload payload = createValidPayload();
        payload.setRepoUrl("ftp://invalid-scheme.com/repo.git");
        
        // When
        InputValidator.ValidationResult result = validator.validateJenkinsPayload(payload);
        
        // Then
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("URL scheme not allowed"));
    }
    
    @Test
    void shouldRejectMalformedRepositoryUrl() {
        // Given
        JenkinsWebhookPayload payload = createValidPayload();
        payload.setRepoUrl("not-a-valid-url");
        
        // When
        InputValidator.ValidationResult result = validator.validateJenkinsPayload(payload);
        
        // Then
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("Invalid repository URL format"));
    }
    
    @Test
    void shouldRejectBlockedDomains() {
        // Given
        JenkinsWebhookPayload payload = createValidPayload();
        payload.setRepoUrl("https://localhost/repo.git");
        
        // When
        InputValidator.ValidationResult result = validator.validateJenkinsPayload(payload);
        
        // Then
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("Repository host not allowed"));
    }
    
    @Test
    void shouldRejectPrivateIpAddresses() {
        // Given
        JenkinsWebhookPayload payload = createValidPayload();
        payload.setRepoUrl("https://192.168.1.1/repo.git");
        
        // When
        InputValidator.ValidationResult result = validator.validateJenkinsPayload(payload);
        
        // Then
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("Private IP addresses not allowed"));
    }
    
    @Test
    void shouldRejectInvalidCommitSha() {
        // Given
        JenkinsWebhookPayload payload = createValidPayload();
        payload.setCommitSha("invalid-sha");
        
        // When
        InputValidator.ValidationResult result = validator.validateJenkinsPayload(payload);
        
        // Then
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("Invalid commit SHA format"));
    }
    
    @Test
    void shouldRejectTooShortCommitSha() {
        // Given
        JenkinsWebhookPayload payload = createValidPayload();
        payload.setCommitSha("abc123"); // Too short
        
        // When
        InputValidator.ValidationResult result = validator.validateJenkinsPayload(payload);
        
        // Then
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("Invalid commit SHA format"));
    }
    
    @Test
    void shouldAcceptValidCommitShaVariations() {
        // Test 7-character SHA
        JenkinsWebhookPayload payload1 = createValidPayload();
        payload1.setCommitSha("abc1234");
        assertTrue(validator.validateJenkinsPayload(payload1).isValid());
        
        // Test 40-character SHA
        JenkinsWebhookPayload payload2 = createValidPayload();
        payload2.setCommitSha("a1b2c3d4e5f6789012345678901234567890abcd");
        assertTrue(validator.validateJenkinsPayload(payload2).isValid());
    }
    
    @Test
    void shouldRejectOversizedBuildLogs() {
        // Given
        JenkinsWebhookPayload payload = createValidPayload();
        payload.setBuildLogs("x".repeat(1024 * 1024 + 1)); // Exceeds 1MB limit
        
        // When
        InputValidator.ValidationResult result = validator.validateJenkinsPayload(payload);
        
        // Then
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("Build logs too large"));
    }
    
    @Test
    void shouldValidateFilePathSuccessfully() {
        // When
        InputValidator.ValidationResult result = validator.validateFilePath("src/main/java/Test.java");
        
        // Then
        assertTrue(result.isValid());
    }
    
    @Test
    void shouldRejectFilePathWithDirectoryTraversal() {
        // When
        InputValidator.ValidationResult result = validator.validateFilePath("../../../etc/passwd");
        
        // Then
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("directory traversal detected"));
    }
    
    @Test
    void shouldRejectFilePathWithNullBytes() {
        // When
        InputValidator.ValidationResult result = validator.validateFilePath("test\0file.java");
        
        // Then
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("null bytes"));
    }
    
    @Test
    void shouldRejectTooLongFilePath() {
        // Given
        String longPath = "a".repeat(261); // Exceeds Windows MAX_PATH
        
        // When
        InputValidator.ValidationResult result = validator.validateFilePath(longPath);
        
        // Then
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("File path too long"));
    }
    
    @Test
    void shouldRejectReservedFileNames() {
        // When
        InputValidator.ValidationResult result = validator.validateFilePath("con.txt");
        
        // Then
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("Reserved file name"));
    }
    
    @Test
    void shouldValidateWorkingDirectorySuccessfully() {
        // When
        InputValidator.ValidationResult result = validator.validateWorkingDirectory("work/build-123");
        
        // Then
        assertTrue(result.isValid());
    }
    
    @Test
    void shouldRejectWorkingDirectoryOutsideWorkPath() {
        // When
        InputValidator.ValidationResult result = validator.validateWorkingDirectory("tmp/malicious");
        
        // Then
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("Working directory must be under /work/"));
    }
    
    @Test
    void shouldValidateBuildIdSuccessfully() {
        // When
        InputValidator.ValidationResult result = validator.validateBuildId(123L);
        
        // Then
        assertTrue(result.isValid());
    }
    
    @Test
    void shouldRejectNullBuildId() {
        // When
        InputValidator.ValidationResult result = validator.validateBuildId(null);
        
        // Then
        assertFalse(result.isValid());
        assertEquals("Build ID is required", result.getErrorMessage());
    }
    
    @Test
    void shouldRejectNegativeBuildId() {
        // When
        InputValidator.ValidationResult result = validator.validateBuildId(-1L);
        
        // Then
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("Build ID must be positive"));
    }
    
    @Test
    void shouldRejectExcessivelyLargeBuildId() {
        // When
        InputValidator.ValidationResult result = validator.validateBuildId(Long.MAX_VALUE);
        
        // Then
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("Build ID too large"));
    }
    
    @Test
    void shouldValidateEmailAddressSuccessfully() {
        // When
        InputValidator.ValidationResult result = validator.validateEmailAddress("test@example.com");
        
        // Then
        assertTrue(result.isValid());
    }
    
    @Test
    void shouldRejectInvalidEmailFormat() {
        // When
        InputValidator.ValidationResult result = validator.validateEmailAddress("invalid-email");
        
        // Then
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("Invalid email format"));
    }
    
    @Test
    void shouldRejectTooLongEmailAddress() {
        // Given
        String longEmail = "a".repeat(250) + "@example.com"; // Exceeds RFC limit
        
        // When
        InputValidator.ValidationResult result = validator.validateEmailAddress(longEmail);
        
        // Then
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("Email address too long"));
    }
    
    @Test
    void shouldAcceptValidUrlSchemes() {
        String[] validUrls = {
            "https://github.com/user/repo.git",
            "http://gitlab.com/user/repo.git",
            "git://github.com/user/repo.git",
            "ssh://git@github.com/user/repo.git"
        };
        
        for (String url : validUrls) {
            JenkinsWebhookPayload payload = createValidPayload();
            payload.setRepoUrl(url);
            
            InputValidator.ValidationResult result = validator.validateJenkinsPayload(payload);
            assertTrue(result.isValid(), "Should accept URL: " + url);
        }
    }
    
    @Test
    void shouldAcceptValidBranchNames() {
        String[] validBranches = {
            "main",
            "feature/user-auth",
            "bugfix/fix-123",
            "release/v1.0.0",
            "hotfix/security-patch"
        };
        
        for (String branch : validBranches) {
            JenkinsWebhookPayload payload = createValidPayload();
            payload.setBranch(branch);
            
            InputValidator.ValidationResult result = validator.validateJenkinsPayload(payload);
            assertTrue(result.isValid(), "Should accept branch: " + branch);
        }
    }
    
    private JenkinsWebhookPayload createValidPayload() {
        JenkinsWebhookPayload payload = new JenkinsWebhookPayload();
        payload.setJob("test-job");
        payload.setBuildNumber(123);
        payload.setBranch("main");
        payload.setRepoUrl("https://github.com/test/repo.git");
        payload.setCommitSha("abc1234567890def");
        payload.setBuildLogs("Build failed with compilation errors");
        return payload;
    }
}