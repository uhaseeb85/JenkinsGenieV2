package com.example.cifixer.git;

import com.example.cifixer.core.Agent;
import com.example.cifixer.core.Task;
import com.example.cifixer.core.TaskResult;
import com.example.cifixer.util.BuildTool;
import com.example.cifixer.util.SpringProjectAnalyzer;
import com.example.cifixer.util.SpringProjectContext;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * Agent responsible for Git operations and Spring project handling.
 * Manages repository cloning, working directory setup, branch creation,
 * and Maven/Gradle project structure validation.
 */
@Component
public class RepoAgent implements Agent<Map<String, Object>> {
    
    private static final Logger logger = LoggerFactory.getLogger(RepoAgent.class);
    
    private final SpringProjectAnalyzer springProjectAnalyzer;
    
    @Value("${cifixer.working-directory:/tmp/cifixer}")
    private String baseWorkingDirectory;
    
    @Value("${cifixer.git.username:#{null}}")
    private String gitUsername;
    
    @Value("${cifixer.git.token:#{null}}")
    private String gitToken;
    
    public RepoAgent(SpringProjectAnalyzer springProjectAnalyzer) {
        this.springProjectAnalyzer = springProjectAnalyzer;
    }
    
    @Override
    public TaskResult handle(Task task, Map<String, Object> payload) {
        logger.info("Processing REPO task for build: {}", task.getBuild().getId());
        
        try {
            RepoPayload repoPayload = extractRepoPayload(payload);
            
            // Set build ID from task (not from payload)
            repoPayload.setBuildId(task.getBuild().getId());
            
            // Create working directory for this build
            String workingDir = createWorkingDirectory(repoPayload.getBuildId());
            repoPayload.setWorkingDirectory(workingDir);
            
            // Clone or update repository
            Git git = cloneOrUpdateRepository(repoPayload);
            
            // Validate Spring project structure
            SpringProjectContext projectContext = validateSpringProjectStructure(workingDir);
            
            // Create fix branch
            String fixBranch = createFixBranch(git, repoPayload.getBuildId());
            
            logger.info("Successfully prepared repository for build: {} in directory: {}", 
                task.getBuild().getId(), workingDir);
            
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("workingDirectory", workingDir);
            metadata.put("fixBranch", fixBranch);
            metadata.put("buildTool", projectContext.getBuildTool().name());
            metadata.put("springBootVersion", projectContext.getSpringBootVersion());
            metadata.put("mavenModules", projectContext.getMavenModules());
            
            return TaskResult.success("Repository prepared successfully", metadata);
            
        } catch (Exception e) {
            logger.error("Failed to process REPO task for build: {}", 
                task.getBuild().getId(), e);
            return TaskResult.failure("Failed to prepare repository: " + e.getMessage());
        }
    }
    
    /**
     * Creates a working directory for the specific build ID.
     */
    private String createWorkingDirectory(Long buildId) throws IOException {
        String workingDir = baseWorkingDirectory != null ? baseWorkingDirectory : "/tmp/cifixer";
        Path workingPath = Paths.get(workingDir, "build-" + buildId);
        
        // Clean up existing directory if it exists
        if (Files.exists(workingPath)) {
            logger.info("Cleaning up existing working directory: {}", workingPath);
            deleteDirectoryRecursively(workingPath.toFile());
        }
        
        Files.createDirectories(workingPath);
        logger.info("Created working directory: {}", workingPath);
        
        return workingPath.toString();
    }
    
    /**
     * Clones the repository or updates it if it already exists.
     */
    private Git cloneOrUpdateRepository(RepoPayload payload) throws GitAPIException, IOException {
        String workingDir = payload.getWorkingDirectory();
        String repoUrl = payload.getRepoUrl();
        
        logger.info("Cloning repository {} to {}", repoUrl, workingDir);
        
        // Setup credentials if available
        UsernamePasswordCredentialsProvider credentialsProvider = null;
        if (gitUsername != null && gitToken != null) {
            credentialsProvider = new UsernamePasswordCredentialsProvider(gitUsername, gitToken);
        } else if (payload.getCredentials() != null) {
            String username = payload.getCredentials().get("username");
            String token = payload.getCredentials().get("token");
            if (username != null && token != null) {
                credentialsProvider = new UsernamePasswordCredentialsProvider(username, token);
            }
        }
        
        // Clone repository
        Git git = Git.cloneRepository()
                .setURI(repoUrl)
                .setDirectory(new File(workingDir))
                .setBranch(payload.getBranch())
                .setCredentialsProvider(credentialsProvider)
                .call();
        
        // Checkout specific commit if provided
        if (payload.getCommitSha() != null && !payload.getCommitSha().isEmpty()) {
            logger.info("Checking out commit: {}", payload.getCommitSha());
            git.checkout()
                .setName(payload.getCommitSha())
                .call();
        }
        
        logger.info("Successfully cloned repository to: {}", workingDir);
        return git;
    }
    
    /**
     * Validates that the cloned repository is a valid Spring project.
     */
    private SpringProjectContext validateSpringProjectStructure(String workingDir) throws IOException {
        logger.info("Validating Spring project structure in: {}", workingDir);
        
        // Check for Maven or Gradle build files
        File pomFile = new File(workingDir, "pom.xml");
        File gradleFile = new File(workingDir, "build.gradle");
        File gradleKtsFile = new File(workingDir, "build.gradle.kts");
        
        if (!pomFile.exists() && !gradleFile.exists() && !gradleKtsFile.exists()) {
            throw new IllegalStateException("No Maven (pom.xml) or Gradle (build.gradle) build file found in repository");
        }
        
        // Analyze Spring project context
        SpringProjectContext context = springProjectAnalyzer.analyzeProject(workingDir);
        
        // Validate that it's actually a Spring project
        if (context.getSpringBootVersion() == null || context.getSpringBootVersion().isEmpty()) {
            logger.warn("No Spring Boot version detected, but proceeding with Java project analysis");
        }
        
        logger.info("Validated Spring project - Build Tool: {}, Spring Boot Version: {}, Modules: {}", 
            context.getBuildTool(), context.getSpringBootVersion(), context.getMavenModules().size());
        
        return context;
    }
    
    /**
     * Creates a fix branch with the naming pattern "ci-fix/{buildId}".
     */
    private String createFixBranch(Git git, Long buildId) throws GitAPIException {
        String branchName = "ci-fix/" + buildId;
        
        logger.info("Creating fix branch: {}", branchName);
        
        // Create and checkout the fix branch
        git.checkout()
            .setCreateBranch(true)
            .setName(branchName)
            .call();
        
        logger.info("Successfully created and checked out branch: {}", branchName);
        return branchName;
    }
    
    /**
     * Commits changes to the repository with a descriptive message.
     */
    public void commitChanges(String workingDir, String message, String authorName, String authorEmail) 
            throws GitAPIException, IOException {
        
        Repository repository = new FileRepositoryBuilder()
                .setGitDir(new File(workingDir, ".git"))
                .build();
        
        try (Git git = new Git(repository)) {
            // Add all changes
            git.add()
                .addFilepattern(".")
                .call();
            
            // Commit changes
            git.commit()
                .setMessage(message)
                .setAuthor(authorName, authorEmail)
                .call();
            
            logger.info("Successfully committed changes: {}", message);
        }
    }
    
    /**
     * Pushes the current branch to the remote repository.
     */
    public void pushBranch(String workingDir, String branchName) throws GitAPIException, IOException {
        Repository repository = new FileRepositoryBuilder()
                .setGitDir(new File(workingDir, ".git"))
                .build();
        
        try (Git git = new Git(repository)) {
            // Setup credentials if available
            UsernamePasswordCredentialsProvider credentialsProvider = null;
            if (gitUsername != null && gitToken != null) {
                credentialsProvider = new UsernamePasswordCredentialsProvider(gitUsername, gitToken);
            }
            
            // Push branch
            git.push()
                .setRemote("origin")
                .setRefSpecs(new org.eclipse.jgit.transport.RefSpec("refs/heads/" + branchName + ":refs/heads/" + branchName))
                .setCredentialsProvider(credentialsProvider)
                .call();
            
            logger.info("Successfully pushed branch: {}", branchName);
        }
    }
    
    /**
     * Extracts RepoPayload from the generic payload map.
     */
    private RepoPayload extractRepoPayload(Map<String, Object> payload) {
        logger.info("Extracting RepoPayload from payload with keys: {}", payload.keySet());
        RepoPayload repoPayload = new RepoPayload();
        
        String repoUrl = (String) payload.get("repoUrl");
        logger.info("Extracted repoUrl from payload: {}", repoUrl);
        repoPayload.setRepoUrl(repoUrl);
        repoPayload.setBranch((String) payload.get("branch"));
        repoPayload.setCommitSha((String) payload.get("commitSha"));
        
        Object buildIdObj = payload.get("buildId");
        if (buildIdObj instanceof Number) {
            repoPayload.setBuildId(((Number) buildIdObj).longValue());
        }
        
        @SuppressWarnings("unchecked")
        Map<String, String> credentials = (Map<String, String>) payload.get("credentials");
        repoPayload.setCredentials(credentials);
        
        return repoPayload;
    }
    
    /**
     * Recursively deletes a directory and all its contents.
     */
    private void deleteDirectoryRecursively(File directory) {
        if (directory.exists()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteDirectoryRecursively(file);
                    } else {
                        file.delete();
                    }
                }
            }
            directory.delete();
        }
    }
    
    /**
     * Gets the working directory path for a specific build ID.
     */
    public String getWorkingDirectory(Long buildId) {
        String workingDir = baseWorkingDirectory != null ? baseWorkingDirectory : "/tmp/cifixer";
        return Paths.get(workingDir, "build-" + buildId).toString();
    }
    
    /**
     * Checks if a working directory exists for a build ID.
     */
    public boolean workingDirectoryExists(Long buildId) {
        String workingDir = baseWorkingDirectory != null ? baseWorkingDirectory : "/tmp/cifixer";
        Path workingPath = Paths.get(workingDir, "build-" + buildId);
        return Files.exists(workingPath) && Files.isDirectory(workingPath);
    }
    
    /**
     * Cleans up the working directory for a build ID.
     */
    public void cleanupWorkingDirectory(Long buildId) {
        String workingDir = baseWorkingDirectory != null ? baseWorkingDirectory : "/tmp/cifixer";
        Path workingPath = Paths.get(workingDir, "build-" + buildId);
        if (Files.exists(workingPath)) {
            logger.info("Cleaning up working directory for build: {}", buildId);
            deleteDirectoryRecursively(workingPath.toFile());
        }
    }
}