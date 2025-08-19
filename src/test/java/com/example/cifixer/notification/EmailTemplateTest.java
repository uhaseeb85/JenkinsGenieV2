package com.example.cifixer.notification;

import com.example.cifixer.store.Build;
import com.example.cifixer.store.NotificationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

class EmailTemplateTest {
    
    private EmailTemplate emailTemplate;
    private Build testBuild;
    
    @BeforeEach
    void setUp() {
        emailTemplate = new EmailTemplate();
        
        testBuild = new Build();
        testBuild.setId(123L);
        testBuild.setJob("my-spring-app");
        testBuild.setBuildNumber(456);
        testBuild.setBranch("feature/user-service");
        testBuild.setCommitSha("abc123def456789012345678901234567890abcd");
        testBuild.setCreatedAt(LocalDateTime.of(2024, 1, 15, 10, 30, 0));
    }
    
    @Test
    void shouldGenerateSuccessSubject() {
        String subject = emailTemplate.generateSubject(testBuild, NotificationType.SUCCESS, 789);
        
        assertThat(subject).contains("CI Fix: Build #456 fixed - PR #789 created (abc123d)");
    }
    
    @Test
    void shouldGenerateFailureSubject() {
        String subject = emailTemplate.generateSubject(testBuild, NotificationType.FAILURE, null);
        
        assertThat(subject).contains("CI Fix: Build #456 failed to fix (abc123d)");
    }
    
    @Test
    void shouldGenerateManualInterventionSubject() {
        String subject = emailTemplate.generateSubject(testBuild, NotificationType.MANUAL_INTERVENTION, null);
        
        assertThat(subject).contains("CI Fix: Build #456 requires manual intervention (abc123d)");
    }
    
    @Test
    void shouldGenerateSuccessContent() {
        String prUrl = "https://github.com/owner/repo/pull/789";
        String planSummary = "Fixed missing @Repository annotation";
        String validationResults = "COMPILE: ✅ PASSED\nTEST: ✅ PASSED";
        
        String content = emailTemplate.generateSuccessContent(
                testBuild, prUrl, 789, planSummary, 
                Arrays.asList("src/main/java/UserRepository.java", "pom.xml"), 
                validationResults
        );
        
        assertThat(content).contains("CI Build Fixed Successfully");
        assertThat(content).contains("my-spring-app");
        assertThat(content).contains("Build #456");
        assertThat(content).contains("feature/user-service");
        assertThat(content).contains("abc123d");
        assertThat(content).contains("2024-01-15 10:30:00");
        assertThat(content).contains(prUrl);
        assertThat(content).contains("PR #789");
        assertThat(content).contains("Fixed missing @Repository annotation");
        assertThat(content).contains("<code>src/main/java/UserRepository.java</code>");
        assertThat(content).contains("<code>pom.xml</code>");
        assertThat(content).contains("COMPILE: ✅ PASSED");
        assertThat(content).contains("TEST: ✅ PASSED");
    }
    
    @Test
    void shouldGenerateSuccessContentWithEmptyFiles() {
        String content = emailTemplate.generateSuccessContent(
                testBuild, "https://github.com/owner/repo/pull/789", 789, 
                "Plan summary", Collections.emptyList(), "Validation results"
        );
        
        assertThat(content).contains("No files were patched");
    }
    
    @Test
    void shouldGenerateSuccessContentWithNullValues() {
        String content = emailTemplate.generateSuccessContent(
                testBuild, "https://github.com/owner/repo/pull/789", 789, 
                null, null, null
        );
        
        assertThat(content).contains("No plan summary available");
        assertThat(content).contains("No files were patched");
        assertThat(content).contains("No validation results available");
    }
    
    @Test
    void shouldGenerateFailureContent() {
        String errorMessage = "Maven compilation failed: cannot find symbol UserRepository";
        String planSummary = "Attempted to add @Repository annotation";
        
        String content = emailTemplate.generateFailureContent(testBuild, errorMessage, planSummary);
        
        assertThat(content).contains("CI Build Fix Failed");
        assertThat(content).contains("my-spring-app");
        assertThat(content).contains("Build #456");
        assertThat(content).contains("Maven compilation failed: cannot find symbol UserRepository");
        assertThat(content).contains("Attempted to add @Repository annotation");
    }
    
    @Test
    void shouldGenerateFailureContentWithNullValues() {
        String content = emailTemplate.generateFailureContent(testBuild, null, null);
        
        assertThat(content).contains("Unknown error occurred");
        assertThat(content).contains("No plan was generated");
    }
    
    @Test
    void shouldGenerateManualInterventionContent() {
        String errorMessage = "Complex dependency conflict requires manual resolution";
        String planSummary = "Multiple Spring Boot version conflicts detected";
        
        String content = emailTemplate.generateManualInterventionContent(testBuild, errorMessage, planSummary);
        
        assertThat(content).contains("Manual Intervention Required");
        assertThat(content).contains("my-spring-app");
        assertThat(content).contains("Build #456");
        assertThat(content).contains("Complex dependency conflict requires manual resolution");
        assertThat(content).contains("Multiple Spring Boot version conflicts detected");
    }
    
    @Test
    void shouldGenerateManualInterventionContentWithNullValues() {
        String content = emailTemplate.generateManualInterventionContent(testBuild, null, null);
        
        assertThat(content).contains("Automated fix attempts exhausted");
        assertThat(content).contains("No plan was generated");
    }
    
    @Test
    void shouldGenerateValidHtmlContent() {
        String content = emailTemplate.generateSuccessContent(
                testBuild, "https://github.com/owner/repo/pull/789", 789, 
                "Plan summary", Arrays.asList("test.java"), "Validation results"
        );
        
        // Check for basic HTML structure
        assertThat(content).contains("<!DOCTYPE html>");
        assertThat(content).contains("<html>");
        assertThat(content).contains("<head>");
        assertThat(content).contains("<body>");
        assertThat(content).contains("</html>");
        
        // Check for CSS styling
        assertThat(content).contains("<style>");
        assertThat(content).contains("font-family: Arial, sans-serif");
        
        // Check for proper HTML escaping in dynamic content
        assertThat(content).doesNotContain("<script>");
    }
}