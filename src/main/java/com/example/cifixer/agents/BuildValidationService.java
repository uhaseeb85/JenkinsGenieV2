package com.example.cifixer.agents;

import com.example.cifixer.util.CommandExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;

/**
 * Service for validating code fixes by running Maven builds.
 * Executes 'mvn clean compile' to verify that applied patches compile successfully.
 */
@Service
public class BuildValidationService {
    
    private static final Logger logger = LoggerFactory.getLogger(BuildValidationService.class);
    
    @Autowired
    private CommandExecutor commandExecutor;
    
    /**
     * Validates a build by running 'mvn clean compile' in the specified directory.
     *
     * @param workingDirectory The directory containing the Maven project
     * @return BuildValidationResult containing success status and build information
     */
    public BuildValidationResult validateBuild(String workingDirectory) {
        logger.info("Running Maven build validation in: {}", workingDirectory);
        
        try {
            File workDir = new File(workingDirectory);
            if (!workDir.exists() || !workDir.isDirectory()) {
                logger.error("Working directory does not exist or is not a directory: {}", workingDirectory);
                return BuildValidationResult.builder()
                    .success(false)
                    .exitCode(-1)
                    .errorMessage("Working directory does not exist: " + workingDirectory)
                    .compilationErrors("Invalid working directory")
                    .build();
            }
            
            // Run maven clean compile (as requested)
            CommandExecutor.CommandResult result = commandExecutor.executeMaven(
                "clean compile", workDir
            );
            
            boolean success = result.getExitCode() == 0;
            
            if (success) {
                logger.info("Maven build validation PASSED for directory: {}", workingDirectory);
            } else {
                logger.warn("Maven build validation FAILED with exit code {} for directory: {}", 
                    result.getExitCode(), workingDirectory);
                logger.warn("Build stdout length: {}", result.getStdout() != null ? result.getStdout().length() : 0);
                logger.warn("Build stderr length: {}", result.getStderr() != null ? result.getStderr().length() : 0);
            }
            
            return BuildValidationResult.builder()
                .success(success)
                .exitCode(result.getExitCode())
                .buildLogs(result.getStdout())
                .errorMessage(success ? null : result.getStderr())
                .compilationErrors(extractCompilationErrors(result.getStderr()))
                .build();
                
        } catch (Exception e) {
            logger.error("Build validation failed with exception in directory: {}", workingDirectory, e);
            return BuildValidationResult.builder()
                .success(false)
                .exitCode(-1)
                .errorMessage("Validation exception: " + e.getMessage())
                .compilationErrors("Exception during build: " + e.getMessage())
                .build();
        }
    }
    
    /**
     * Extracts key compilation errors from Maven stderr output for LLM context.
     */
    private String extractCompilationErrors(String stderr) {
        if (stderr == null) return null;
        
        // Extract key compilation error details for LLM context
        StringBuilder errors = new StringBuilder();
        String[] lines = stderr.split("\n");
        
        for (String line : lines) {
            if (line.contains("[ERROR]") && 
                (line.contains("cannot find symbol") || 
                 line.contains("package does not exist") ||
                 line.contains("class") ||
                 line.contains("method") ||
                 line.contains("Compilation failure"))) {
                errors.append(line.trim()).append("\n");
            }
        }
        
        String extractedErrors = errors.length() > 0 ? errors.toString() : stderr;
        
        // Limit size to prevent payload overflow
        if (extractedErrors != null && extractedErrors.length() > 2000) {
            return extractedErrors.substring(0, 2000) + "...[truncated]";
        }
        
        return extractedErrors;
    }
}
