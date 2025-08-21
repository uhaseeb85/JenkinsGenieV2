package com.example.cifixer.agents;

import com.example.cifixer.core.Agent;
import com.example.cifixer.core.Task;
import com.example.cifixer.core.TaskResult;
import com.example.cifixer.core.TaskStatus;
import com.example.cifixer.github.*;
import com.example.cifixer.store.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Agent responsible for creating GitHub pull requests with generated fixes.
 * Handles branch pushing, PR creation with Spring-specific templates, and error handling.
 */
@Component
public class PrAgent implements Agent<PrPayload> {
    
    private static final Logger logger = LoggerFactory.getLogger(PrAgent.class);
    
    @Autowired
    private GitHubClient gitHubClient;
    
    @Autowired
    private PullRequestTemplate prTemplate;
    
    @Autowired
    private PullRequestRepository pullRequestRepository;
    
    @Autowired
    private PatchRepository patchRepository;
    
    @Autowired
    private ValidationRepository validationRepository;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Value("${github.token}")
    private String githubToken;
    
    @Override
    public TaskResult handle(Task task, PrPayload payload) {
        logger.info("PrAgent: Starting PR creation for build {}", task.getBuild().getId());
        logger.debug("PrAgent: Payload - repo: {}, branch: {}, baseBranch: {}, patchedFiles: {}", 
                    payload.getRepoUrl(), payload.getBranchName(), payload.getBaseBranch(), payload.getPatchedFiles().size());
        
        try {
            Build build = task.getBuild();
            
            // Check if PR already exists
            if (pullRequestRepository.existsByBuildId(build.getId())) {
                logger.warn("PrAgent: PR already exists for build {}", build.getId());
                return TaskResult.success("Pull request already exists for this build");
            }
            
            // Parse repository information
            logger.debug("PrAgent: Parsing repository URL: {}", payload.getRepoUrl());
            RepositoryInfo repoInfo = parseRepositoryUrl(payload.getRepoUrl());
            logger.info("PrAgent: Parsed repository - owner: {}, name: {}", repoInfo.getOwner(), repoInfo.getName());
            
            // Push branch to remote
            logger.info("PrAgent: Pushing branch {} to remote", payload.getBranchName());
            pushBranchToRemote(payload, repoInfo, build);
            logger.info("PrAgent: ✅ Branch push completed successfully");
            
            // Gather additional context for PR description
            String planSummary = payload.getPlanSummary();
            List<String> patchedFiles = payload.getPatchedFiles();
            String validationResults = gatherValidationResults(build.getId());
            logger.debug("PrAgent: Gathered context - planSummary length: {}, patchedFiles: {}", 
                        planSummary != null ? planSummary.length() : 0, patchedFiles.size());
            
            // Create PR request
            String title = prTemplate.generateTitle(build);
            String description = prTemplate.generateDescription(build, planSummary, patchedFiles, validationResults);
            
            GitHubCreatePullRequestRequest prRequest = new GitHubCreatePullRequestRequest(
                    title, description, payload.getBranchName(), payload.getBaseBranch()
            );
            
            logger.info("PrAgent: Creating PR for build {} with title: {}", build.getId(), title);
            logger.info("PrAgent: PR details - repository: {}/{}, branch: {}, base: {}",
                    repoInfo.getOwner(), repoInfo.getName(), payload.getBranchName(), payload.getBaseBranch());
            logger.debug("PrAgent: PR description: {}", description);
            
            // Create PR via GitHub API
            logger.info("PrAgent: Calling GitHub API to create pull request");
            GitHubPullRequestResponse prResponse = gitHubClient.createPullRequest(
                    repoInfo.getOwner(), repoInfo.getName(), prRequest
            );
            
            logger.info("PrAgent: ✅ PR created successfully - number: {}, url: {}", prResponse.getNumber(), prResponse.getHtmlUrl());
            logger.debug("PrAgent: PR creation response: {}", objectMapper.writeValueAsString(prResponse));
            
            // Add labels
            try {
                logger.debug("PrAgent: Adding standard labels to PR #{}", prResponse.getNumber());
                gitHubClient.addLabels(repoInfo.getOwner(), repoInfo.getName(), 
                        prResponse.getNumber(), prTemplate.getStandardLabels());
                logger.info("PrAgent: ✅ Labels added successfully to PR #{}", prResponse.getNumber());
            } catch (GitHubApiException e) {
                logger.warn("PrAgent: ⚠️ Failed to add labels to PR #{}: {}", prResponse.getNumber(), e.getMessage());
                // Continue - labels are not critical
            }
            
            // Save PR information
            logger.info("PrAgent: Saving PR information to database");
            PullRequest pullRequest = new PullRequest(build, payload.getBranchName());
            pullRequest.setPrNumber(prResponse.getNumber());
            pullRequest.setPrUrl(prResponse.getHtmlUrl());
            pullRequest.setStatus(PullRequestStatus.CREATED);
            pullRequestRepository.save(pullRequest);
            
            logger.info("PrAgent: ✅ Successfully created PR #{} for build {}: {}", 
                    prResponse.getNumber(), build.getId(), prResponse.getHtmlUrl());
            
            // Prepare result metadata
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("prNumber", prResponse.getNumber());
            metadata.put("prUrl", prResponse.getHtmlUrl());
            metadata.put("branchName", payload.getBranchName());
            
            return TaskResult.success("Pull request created successfully", metadata);
            
        } catch (GitHubApiException e) {
            logger.error("PrAgent: ❌ GitHub API error while creating PR for build {}: {}", 
                    task.getBuild().getId(), e.getMessage());
            return TaskResult.failure("GitHub API error: " + e.getMessage());
            
        } catch (GitAPIException e) {
            logger.error("PrAgent: ❌ Git operation failed while pushing branch for build {}: {}", 
                    task.getBuild().getId(), e.getMessage());
            return TaskResult.failure("Git operation failed: " + e.getMessage());
            
        } catch (Exception e) {
            logger.error("PrAgent: ❌ Unexpected error while creating PR for build {}: {}", 
                    task.getBuild().getId(), e.getMessage(), e);
            return TaskResult.failure("Unexpected error: " + e.getMessage());
        }
    }
    
    /**
     * Pushes the fix branch to the remote repository.
     */
    private void pushBranchToRemote(PrPayload payload, RepositoryInfo repoInfo, Build build) throws GitAPIException, java.io.IOException {
        String workingDir = "/work/" + build.getId();
        File gitDir = new File(workingDir);
        
        logger.debug("PrAgent: Checking working directory: {}", workingDir);
        if (!gitDir.exists()) {
            logger.error("PrAgent: Working directory does not exist: {}", workingDir);
            throw new IllegalStateException("Working directory does not exist: " + workingDir);
        }
        
        logger.info("PrAgent: Pushing branch {} to remote for build {}", payload.getBranchName(), build.getId());
        
        try (Git git = Git.open(gitDir)) {
            String refSpec = "refs/heads/" + payload.getBranchName() + ":refs/heads/" + payload.getBranchName();
            logger.debug("PrAgent: Using refSpec: {}", refSpec);
            
            // Push the branch to remote
            git.push()
                    .setRemote("origin")
                    .setRefSpecs(new RefSpec(refSpec))
                    .setCredentialsProvider(new UsernamePasswordCredentialsProvider("git", githubToken))
                    .call();
            
            logger.info("PrAgent: ✅ Successfully pushed branch {} for build {}", payload.getBranchName(), build.getId());
        } catch (GitAPIException e) {
            logger.error("PrAgent: ❌ Failed to push branch {} for build {}: {}", payload.getBranchName(), build.getId(), e.getMessage());
            throw e;
        }
    }
    
    /**
     * Parses a GitHub repository URL to extract owner and repository name.
     */
    private RepositoryInfo parseRepositoryUrl(String repoUrl) {
        try {
            URI uri = URI.create(repoUrl);
            String path = uri.getPath();
            
            // Remove leading slash and .git suffix if present
            if (path.startsWith("/")) {
                path = path.substring(1);
            }
            if (path.endsWith(".git")) {
                path = path.substring(0, path.length() - 4);
            }
            
            String[] parts = path.split("/");
            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid repository URL format: " + repoUrl);
            }
            
            return new RepositoryInfo(parts[0], parts[1]);
            
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse repository URL: " + repoUrl, e);
        }
    }
    
    /**
     * Gathers validation results for inclusion in the PR description.
     */
    private String gatherValidationResults(Long buildId) {
        try {
            List<Validation> validations = validationRepository.findByBuildIdOrderByCreatedAtDesc(buildId);
            
            if (validations.isEmpty()) {
                return "No validation results available.";
            }
            
            StringBuilder results = new StringBuilder();
            for (Validation validation : validations) {
                results.append(validation.getValidationType()).append(": ");
                if (validation.getExitCode() == 0) {
                    results.append("✅ PASSED\n");
                } else {
                    results.append("❌ FAILED (exit code: ").append(validation.getExitCode()).append(")\n");
                }
            }
            
            return results.toString().trim();
            
        } catch (Exception e) {
            logger.warn("Failed to gather validation results for build {}: {}", buildId, e.getMessage());
            return "Failed to gather validation results.";
        }
    }
    
    /**
     * Simple data class for repository information.
     */
    private static class RepositoryInfo {
        private final String owner;
        private final String name;
        
        public RepositoryInfo(String owner, String name) {
            this.owner = owner;
            this.name = name;
        }
        
        public String getOwner() {
            return owner;
        }
        
        public String getName() {
            return name;
        }
    }
}