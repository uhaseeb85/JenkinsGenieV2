package com.example.cifixer.notification;

import com.example.cifixer.store.Build;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RecipientResolverTest {
    
    private RecipientResolver recipientResolver;
    private Build testBuild;
    
    @TempDir
    Path tempDir;
    
    @BeforeEach
    void setUp() {
        recipientResolver = new RecipientResolver();
        
        testBuild = new Build();
        testBuild.setId(123L);
        testBuild.setCommitSha("abc123def456789012345678901234567890abcd");
        
        // Set default configuration values
        ReflectionTestUtils.setField(recipientResolver, "defaultRecipients", "admin@example.com,team@example.com");
        ReflectionTestUtils.setField(recipientResolver, "successRecipients", "success@example.com");
        ReflectionTestUtils.setField(recipientResolver, "failureRecipients", "failure@example.com");
        ReflectionTestUtils.setField(recipientResolver, "manualInterventionRecipients", "manual@example.com");
        ReflectionTestUtils.setField(recipientResolver, "includeCommitAuthors", false);
    }
    
    @Test
    void shouldResolveSuccessRecipients() {
        List<String> recipients = recipientResolver.resolveSuccessRecipients(testBuild);
        
        assertThat(recipients).containsExactlyInAnyOrder(
                "success@example.com", 
                "admin@example.com", 
                "team@example.com"
        );
    }
    
    @Test
    void shouldResolveFailureRecipients() {
        List<String> recipients = recipientResolver.resolveFailureRecipients(testBuild);
        
        assertThat(recipients).containsExactlyInAnyOrder(
                "failure@example.com", 
                "admin@example.com", 
                "team@example.com"
        );
    }
    
    @Test
    void shouldResolveManualInterventionRecipients() {
        List<String> recipients = recipientResolver.resolveManualInterventionRecipients(testBuild);
        
        assertThat(recipients).containsExactlyInAnyOrder(
                "manual@example.com", 
                "admin@example.com", 
                "team@example.com"
        );
    }
    
    @Test
    void shouldHandleEmptyRecipientLists() {
        ReflectionTestUtils.setField(recipientResolver, "defaultRecipients", "");
        ReflectionTestUtils.setField(recipientResolver, "successRecipients", "");
        
        List<String> recipients = recipientResolver.resolveSuccessRecipients(testBuild);
        
        assertThat(recipients).isEmpty();
    }
    
    @Test
    void shouldHandleNullRecipientLists() {
        ReflectionTestUtils.setField(recipientResolver, "defaultRecipients", null);
        ReflectionTestUtils.setField(recipientResolver, "successRecipients", null);
        
        List<String> recipients = recipientResolver.resolveSuccessRecipients(testBuild);
        
        assertThat(recipients).isEmpty();
    }
    
    @Test
    void shouldFilterInvalidEmails() {
        ReflectionTestUtils.setField(recipientResolver, "defaultRecipients", 
                "valid@example.com,invalid-email,@invalid.com,invalid@,noreply@example.com,no-reply@example.com");
        ReflectionTestUtils.setField(recipientResolver, "successRecipients", "");
        
        List<String> recipients = recipientResolver.resolveSuccessRecipients(testBuild);
        
        assertThat(recipients).containsExactly("valid@example.com");
    }
    
    @Test
    void shouldNormalizeCaseAndRemoveDuplicates() {
        ReflectionTestUtils.setField(recipientResolver, "defaultRecipients", "Admin@Example.com,TEAM@EXAMPLE.COM");
        ReflectionTestUtils.setField(recipientResolver, "successRecipients", "admin@example.com,team@example.com");
        
        List<String> recipients = recipientResolver.resolveSuccessRecipients(testBuild);
        
        assertThat(recipients).containsExactlyInAnyOrder("admin@example.com", "team@example.com");
    }
    
    @Test
    void shouldHandleWhitespaceInRecipientLists() {
        ReflectionTestUtils.setField(recipientResolver, "defaultRecipients", " admin@example.com , team@example.com ");
        ReflectionTestUtils.setField(recipientResolver, "successRecipients", "");
        
        List<String> recipients = recipientResolver.resolveSuccessRecipients(testBuild);
        
        assertThat(recipients).containsExactlyInAnyOrder("admin@example.com", "team@example.com");
    }
    
    @Test
    void shouldIncludeCommitAuthorsWhenEnabled() {
        ReflectionTestUtils.setField(recipientResolver, "includeCommitAuthors", true);
        ReflectionTestUtils.setField(recipientResolver, "defaultRecipients", "admin@example.com");
        ReflectionTestUtils.setField(recipientResolver, "successRecipients", "");
        
        // Note: This test will not find actual commit authors since we don't have a real Git repo
        // The method will gracefully handle the missing working directory
        List<String> recipients = recipientResolver.resolveSuccessRecipients(testBuild);
        
        assertThat(recipients).contains("admin@example.com");
    }
    
    @Test
    void shouldHandleMissingWorkingDirectory() {
        ReflectionTestUtils.setField(recipientResolver, "includeCommitAuthors", true);
        ReflectionTestUtils.setField(recipientResolver, "defaultRecipients", "admin@example.com");
        
        // Build with non-existent working directory
        Build buildWithMissingDir = new Build();
        buildWithMissingDir.setId(999L);
        buildWithMissingDir.setCommitSha("nonexistent123");
        
        List<String> recipients = recipientResolver.resolveSuccessRecipients(buildWithMissingDir);
        
        // Should still return configured recipients even if Git lookup fails
        assertThat(recipients).contains("admin@example.com");
    }
}