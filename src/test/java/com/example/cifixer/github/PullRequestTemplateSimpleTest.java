package com.example.cifixer.github;

import com.example.cifixer.store.Build;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Simple test for PullRequestTemplate to verify basic functionality.
 */
class PullRequestTemplateSimpleTest {
    
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
    void shouldGenerateTitle() {
        String title = prTemplate.generateTitle(testBuild);
        assertThat(title).isEqualTo("Fix: Jenkins build #123 (abc123d)");
    }
    
    @Test
    void shouldGenerateDescription() {
        String description = prTemplate.generateDescription(testBuild, "test plan", Arrays.asList("file1.java"), "test results");
        assertThat(description).contains("🤖 Automated CI Fix");
        assertThat(description).contains("spring-boot-app");
        assertThat(description).contains("#123");
    }
    
    @Test
    void shouldReturnStandardLabels() {
        String[] labels = prTemplate.getStandardLabels();
        assertThat(labels).containsExactly("ci-fix", "automated", "spring-boot");
    }
}