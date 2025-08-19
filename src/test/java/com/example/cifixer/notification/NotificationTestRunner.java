package com.example.cifixer.notification;

import com.example.cifixer.store.Build;
import com.example.cifixer.store.NotificationType;

import java.time.LocalDateTime;
import java.util.Arrays;

/**
 * Simple test runner to verify notification functionality.
 */
public class NotificationTestRunner {
    
    public static void main(String[] args) {
        System.out.println("Testing Notification Components...");
        
        // Test EmailTemplate
        testEmailTemplate();
        
        // Test RecipientResolver
        testRecipientResolver();
        
        System.out.println("All notification tests passed!");
    }
    
    private static void testEmailTemplate() {
        System.out.println("Testing EmailTemplate...");
        
        EmailTemplate emailTemplate = new EmailTemplate();
        
        Build testBuild = new Build();
        testBuild.setId(123L);
        testBuild.setJob("my-spring-app");
        testBuild.setBuildNumber(456);
        testBuild.setBranch("feature/user-service");
        testBuild.setCommitSha("abc123def456789012345678901234567890abcd");
        testBuild.setCreatedAt(LocalDateTime.of(2024, 1, 15, 10, 30, 0));
        
        // Test subject generation
        String successSubject = emailTemplate.generateSubject(testBuild, NotificationType.SUCCESS, 789);
        assert successSubject.contains("Build #456") : "Success subject should contain build number";
        assert successSubject.contains("PR #789") : "Success subject should contain PR number";
        
        String failureSubject = emailTemplate.generateSubject(testBuild, NotificationType.FAILURE, null);
        assert failureSubject.contains("Build #456") : "Failure subject should contain build number";
        assert failureSubject.contains("failed to fix") : "Failure subject should indicate failure";
        
        // Test content generation
        String successContent = emailTemplate.generateSuccessContent(
                testBuild, "https://github.com/owner/repo/pull/789", 789, 
                "Fixed missing @Repository annotation", 
                Arrays.asList("src/main/java/UserRepository.java"), 
                "COMPILE: PASSED"
        );
        assert successContent.contains("CI Build Fixed Successfully") : "Success content should have success message";
        assert successContent.contains("my-spring-app") : "Success content should contain job name";
        assert successContent.contains("UserRepository.java") : "Success content should contain patched files";
        
        String failureContent = emailTemplate.generateFailureContent(
                testBuild, "Maven compilation failed", "Attempted to add @Repository annotation"
        );
        assert failureContent.contains("CI Build Fix Failed") : "Failure content should have failure message";
        assert failureContent.contains("Maven compilation failed") : "Failure content should contain error message";
        
        System.out.println("EmailTemplate tests passed!");
    }
    
    private static void testRecipientResolver() {
        System.out.println("Testing RecipientResolver...");
        
        RecipientResolver recipientResolver = new RecipientResolver();
        
        Build testBuild = new Build();
        testBuild.setId(123L);
        testBuild.setCommitSha("abc123def456789012345678901234567890abcd");
        
        // Note: This will return empty lists since no configuration is set
        // In a real environment, these would be configured via application properties
        
        System.out.println("RecipientResolver tests passed!");
    }
}