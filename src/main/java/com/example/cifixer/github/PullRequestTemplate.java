package com.example.cifixer.github;

import com.example.cifixer.store.Build;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Service for generating Spring-specific pull request templates.
 * Creates standardized PR titles and descriptions for automated fixes.
 */
@Component
public class PullRequestTemplate {
    
    /**
     * Generates a PR title for a build fix.
     *
     * @param build The build being fixed
     * @return Formatted PR title
     */
    public String generateTitle(Build build) {
        String shortSha = build.getCommitSha().substring(0, Math.min(7, build.getCommitSha().length()));
        return String.format("Fix: Jenkins build #%d (%s)", build.getBuildNumber(), shortSha);
    }
    
    /**
     * Generates a comprehensive PR description with plan summary and diff details.
     *
     * @param build The build being fixed
     * @param planSummary Summary of the fix plan
     * @param patchedFiles List of files that were modified
     * @param validationResults Results from build validation
     * @return Formatted PR description
     */
    public String generateDescription(Build build, String planSummary, List<String> patchedFiles, String validationResults) {
        StringBuilder description = new StringBuilder();
        
        // Header
        description.append("## 🤖 Automated CI Fix\n\n");
        description.append("This pull request was automatically generated to fix Jenkins build failures.\n\n");
        
        // Build Information
        description.append("### 📋 Build Information\n");
        description.append("- **Job**: `").append(build.getJob()).append("`\n");
        description.append("- **Build Number**: #").append(build.getBuildNumber()).append("\n");
        description.append("- **Branch**: `").append(build.getBranch()).append("`\n");
        description.append("- **Commit**: `").append(build.getCommitSha()).append("`\n");
        description.append("- **Repository**: ").append(build.getRepoUrl()).append("\n\n");
        
        // Plan Summary
        if (planSummary != null && !planSummary.trim().isEmpty()) {
            description.append("### 🔧 Fix Plan\n");
            description.append(planSummary).append("\n\n");
        }
        
        // Modified Files
        if (patchedFiles != null && !patchedFiles.isEmpty()) {
            description.append("### 📝 Modified Files\n");
            for (String file : patchedFiles) {
                description.append("- `").append(file).append("`\n");
            }
            description.append("\n");
        }
        
        // Validation Results
        if (validationResults != null && !validationResults.trim().isEmpty()) {
            description.append("### ✅ Validation Results\n");
            description.append("```\n");
            description.append(validationResults);
            description.append("\n```\n\n");
        }
        
        // Review Checklist
        description.append("### 📋 Review Checklist\n");
        description.append("- [ ] Review the automated changes for correctness\n");
        description.append("- [ ] Verify Spring Boot application starts successfully\n");
        description.append("- [ ] Check that all tests pass\n");
        description.append("- [ ] Ensure no unintended side effects\n");
        description.append("- [ ] Validate Spring configuration changes\n");
        description.append("- [ ] Review Maven/Gradle dependency modifications\n\n");
        
        // Footer
        description.append("### ℹ️ About This Fix\n");
        description.append("This fix was generated using AI analysis of the build failure logs. ");
        description.append("The system identified Spring-specific issues and applied targeted patches ");
        description.append("to resolve compilation errors, dependency conflicts, or configuration problems.\n\n");
        description.append("**Please review carefully before merging.**\n");
        
        return description.toString();
    }
    
    /**
     * Gets the standard labels to apply to automated fix PRs.
     *
     * @return Array of label names
     */
    public String[] getStandardLabels() {
        return new String[]{"ci-fix", "automated", "spring-boot"};
    }
}