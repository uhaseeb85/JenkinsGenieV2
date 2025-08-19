package com.example.cifixer.github;

import com.example.cifixer.store.Build;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PullRequestTemplateTest {
    
    private PullRequestTemplate prTemplate;
    private Build testBuild;
    
    @BeforeEach
    void setUp() {
        prTemplate = new PullRequestTemplate();
        
        testBuild = new Build("spring-boot-app", 123, "main", 
                "https://github.com/owner/repo.git", "abc123def456789");
        testBuild.setId(1L);
    }
    
    @Test
    void shouldGenerateTitleWithShortSha() {
        // When
        String title = prTemplate.generateTitle(testBuild);
        
        // Then
        assertThat(title).isEqualTo("Fix: Jenkins build #123 (abc123d)");
    }
    
    @Test
    void shouldGenerateTitleWithShortShaForShortCommit() {
        // Given
        testBuild.setCommitSha("abc");
        
        // When
        String title = prTemplate.generateTitle(testBuild);
        
        // Then
        assertThat(title).isEqualTo("Fix: Jenkins build #123 (abc)");
    }
    
    @Test
    void shouldGenerateComprehensiveDescription() {
        // Given
        String planSummary = "Fix missing @Repository annotation on UserRepository class";
        List<String> patchedFiles = Arrays.asList(
                "src/main/java/com/example/repository/UserRepository.java",
                "src/main/java/com/example/service/UserService.java",
                "pom.xml"
        );
        String validationResults = "COMPILE: ✅ PASSED\nTEST: ✅ PASSED";
        
        // When
        String description = prTemplate.generateDescription(testBuild, planSummary, patchedFiles, validationResults);
        
        // Then
        assertThat(description).contains("🤖 Automated CI Fix");
        assertThat(description).contains("spring-boot-app");
        assertThat(description).contains("#123");
        assertThat(description).contains("main");
        assertThat(description).contains("abc123def456789");
        assertThat(description).contains("https://github.com/owner/repo.git");
        assertThat(description).contains("Fix missing @Repository annotation");
        assertThat(description).contains("UserRepository.java");
        assertThat(description).contains("UserService.java");
        assertThat(description).contains("pom.xml");
        assertThat(description).contains("COMPILE: ✅ PASSED");
        assertThat(description).contains("TEST: ✅ PASSED");
        assertThat(description).contains("Review Checklist");
        assertThat(description).contains("Spring Boot application starts successfully");
        assertThat(description).contains("Spring configuration changes");
        assertThat(description).contains("Maven/Gradle dependency modifications");
    }
    
    @Test
    void shouldGenerateDescriptionWithoutOptionalFields() {
        // When
        String description = prTemplate.generateDescription(testBuild, null, null, null);
        
        // Then
        assertThat(description).contains("🤖 Automated CI Fix");
        assertThat(description).contains("Build Information");
        assertThat(description).contains("spring-boot-app");
        assertThat(description).contains("#123");
        assertThat(description).contains("Review Checklist");
        assertThat(description).contains("About This Fix");
        
        // Should not contain sections for missing data
        assertThat(description).doesNotContain("Fix Plan");
        assertThat(description).doesNotContain("Modified Files");
        assertThat(description).doesNotContain("Validation Results");
    }
    
    @Test
    void shouldGenerateDescriptionWithEmptyCollections() {
        // Given
        String planSummary = "";
        List<String> patchedFiles = Arrays.asList();
        String validationResults = "";
        
        // When
        String description = prTemplate.generateDescription(testBuild, planSummary, patchedFiles, validationResults);
        
        // Then
        assertThat(description).contains("🤖 Automated CI Fix");
        assertThat(description).contains("Build Information");
        assertThat(description).contains("Review Checklist");
        
        // Should not contain sections for empty data
        assertThat(description).doesNotContain("Fix Plan");
        assertThat(description).doesNotContain("Modified Files");
        assertThat(description).doesNotContain("Validation Results");
    }
    
    @Test
    void shouldIncludeSpringSpecificChecklistItems() {
        // When
        String description = prTemplate.generateDescription(testBuild, "test", Arrays.asList(), "test");
        
        // Then
        assertThat(description).contains("Spring Boot application starts successfully");
        assertThat(description).contains("Spring configuration changes");
        assertThat(description).contains("Maven/Gradle dependency modifications");
    }
    
    @Test
    void shouldReturnStandardLabels() {
        // When
        String[] labels = prTemplate.getStandardLabels();
        
        // Then
        assertThat(labels).containsExactly("ci-fix", "automated", "spring-boot");
    }
    
    @Test
    void shouldFormatValidationResultsInCodeBlock() {
        // Given
        String validationResults = "COMPILE: ✅ PASSED\nTEST: ❌ FAILED";
        
        // When
        String description = prTemplate.generateDescription(testBuild, "test", Arrays.asList(), validationResults);
        
        // Then
        assertThat(description).contains("```\nCOMPILE: ✅ PASSED\nTEST: ❌ FAILED\n```");
    }
    
    @Test
    void shouldIncludeAiGeneratedDisclaimer() {
        // When
        String description = prTemplate.generateDescription(testBuild, "test", Arrays.asList(), "test");
        
        // Then
        assertThat(description).contains("This fix was generated using AI analysis");
        assertThat(description).contains("Spring-specific issues");
        assertThat(description).contains("Please review carefully before merging");
    }
}