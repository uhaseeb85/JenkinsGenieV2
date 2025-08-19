package com.example.cifixer.agents;

import com.example.cifixer.core.Agent;
import com.example.cifixer.core.Task;
import com.example.cifixer.core.TaskResult;
import com.example.cifixer.core.TaskStatus;
import com.example.cifixer.store.Validation;
import com.example.cifixer.store.ValidationRepository;
import com.example.cifixer.store.ValidationType;
import com.example.cifixer.util.BuildTool;
import com.example.cifixer.util.CommandExecutor;
import com.example.cifixer.util.SpringProjectAnalyzer;
import com.example.cifixer.util.SpringProjectContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Agent responsible for validating Maven/Gradle builds and Spring Boot tests.
 * Executes compilation and test validation with proper classpath handling.
 */
@Component
public class ValidatorAgent implements Agent<ValidatePayload> {
    
    private static final Logger logger = LoggerFactory.getLogger(ValidatorAgent.class);
    
    // Patterns for extracting error information from build output
    private static final Pattern MAVEN_ERROR_PATTERN = Pattern.compile("\\[ERROR\\].*");
    private static final Pattern GRADLE_ERROR_PATTERN = Pattern.compile("FAILURE:.*|.*FAILED.*");
    private static final Pattern SPRING_CONTEXT_ERROR_PATTERN = Pattern.compile(".*NoSuchBeanDefinitionException.*|.*BeanCreationException.*");
    
    @Autowired
    private ValidationRepository validationRepository;
    
    @Autowired
    private CommandExecutor commandExecutor;
    
    @Autowired
    private SpringProjectAnalyzer springProjectAnalyzer;
    
    @Override
    public TaskResult handle(Task task, ValidatePayload payload) {
        logger.info("Starting validation for build {} with payload: {}", task.getBuild().getId(), payload);
        
        try {
            File workingDir = new File(payload.getWorkingDirectory());
            if (!workingDir.exists() || !workingDir.isDirectory()) {
                return TaskResult.failure("Working directory does not exist: " + payload.getWorkingDirectory());
            }
            
            // Analyze Spring project to determine build tool and context
            SpringProjectContext springContext = springProjectAnalyzer.analyzeProject(workingDir.getAbsolutePath());
            BuildTool buildTool = springContext.getBuildTool();
            
            logger.info("Detected build tool: {} for Spring Boot version: {}", buildTool, springContext.getSpringBootVersion());
            
            // Perform compilation validation first
            ValidationResult compileResult = performCompilationValidation(task, workingDir, buildTool);
            if (!compileResult.isSuccessful()) {
                return handleValidationFailure(task, compileResult, springContext);
            }
            
            // If compilation succeeds, perform test validation
            ValidationResult testResult = performTestValidation(task, workingDir, buildTool);
            if (!testResult.isSuccessful()) {
                return handleValidationFailure(task, testResult, springContext);
            }
            
            logger.info("All validations passed for build {}", task.getBuild().getId());
            
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("compilationSuccessful", true);
            metadata.put("testsSuccessful", true);
            metadata.put("buildTool", buildTool.name());
            metadata.put("springBootVersion", springContext.getSpringBootVersion());
            
            return TaskResult.success("Validation completed successfully - compilation and tests passed", metadata);
            
        } catch (Exception e) {
            logger.error("Error during validation for build {}: {}", task.getBuild().getId(), e.getMessage(), e);
            return TaskResult.failure("Validation failed due to error: " + e.getMessage());
        }
    }
    
    /**
     * Perform compilation validation using Maven or Gradle.
     */
    private ValidationResult performCompilationValidation(Task task, File workingDir, BuildTool buildTool) {
        logger.info("Performing compilation validation using {}", buildTool);
        
        CommandExecutor.CommandResult result;
        
        if (buildTool == BuildTool.MAVEN) {
            result = commandExecutor.executeMaven("clean compile -DskipTests", workingDir);
        } else {
            result = commandExecutor.executeGradle("clean compileJava -x test", workingDir);
        }
        
        // Store validation result
        Validation validation = new Validation(task.getBuild(), ValidationType.COMPILE, result.getExitCode(), 
                                             result.getStdout(), result.getStderr());
        validationRepository.save(validation);
        
        return new ValidationResult(ValidationType.COMPILE, result.isSuccessful(), result.getExitCode(), 
                                  result.getStdout(), result.getStderr());
    }
    
    /**
     * Perform test validation using Maven or Gradle with Spring Boot test execution.
     */
    private ValidationResult performTestValidation(Task task, File workingDir, BuildTool buildTool) {
        logger.info("Performing test validation using {}", buildTool);
        
        CommandExecutor.CommandResult result;
        
        if (buildTool == BuildTool.MAVEN) {
            // Use Spring Boot Maven plugin for proper classpath handling
            result = commandExecutor.executeMaven("test -Dspring.profiles.active=test", workingDir);
        } else {
            // Use Gradle test task with Spring Boot plugin
            result = commandExecutor.executeGradle("test --info", workingDir);
        }
        
        // Store validation result
        Validation validation = new Validation(task.getBuild(), ValidationType.TEST, result.getExitCode(), 
                                             result.getStdout(), result.getStderr());
        validationRepository.save(validation);
        
        return new ValidationResult(ValidationType.TEST, result.isSuccessful(), result.getExitCode(), 
                                  result.getStdout(), result.getStderr());
    }
    
    /**
     * Handle validation failure by extracting error context and determining retry strategy.
     */
    private TaskResult handleValidationFailure(Task task, ValidationResult validationResult, SpringProjectContext springContext) {
        logger.warn("Validation failed for build {} - type: {}, exitCode: {}", 
                   task.getBuild().getId(), validationResult.getType(), validationResult.getExitCode());
        
        // Extract error information for enhanced context
        String errorContext = extractErrorContext(validationResult, springContext);
        
        // Determine if this is a retryable failure
        boolean isRetryable = isRetryableFailure(validationResult);
        
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("validationType", validationResult.getType().name());
        metadata.put("exitCode", validationResult.getExitCode());
        metadata.put("errorContext", errorContext);
        metadata.put("retryable", isRetryable);
        metadata.put("buildTool", springContext.getBuildTool().name());
        
        if (isRetryable && task.getAttempt() < task.getMaxAttempts()) {
            logger.info("Validation failure is retryable, will retry with enhanced context");
            return TaskResult.retry("Validation failed but retryable: " + errorContext);
        } else {
            logger.info("Validation failure requires manual intervention");
            return TaskResult.failure("Validation failed: " + errorContext, metadata);
        }
    }
    
    /**
     * Extract meaningful error context from validation output for retry attempts.
     */
    private String extractErrorContext(ValidationResult validationResult, SpringProjectContext springContext) {
        StringBuilder context = new StringBuilder();
        
        String stderr = validationResult.getStderr();
        String stdout = validationResult.getStdout();
        
        // Extract Maven/Gradle specific errors
        if (springContext.getBuildTool() == BuildTool.MAVEN) {
            extractMavenErrors(stderr, stdout, context);
        } else {
            extractGradleErrors(stderr, stdout, context);
        }
        
        // Extract Spring-specific errors
        extractSpringErrors(stderr, stdout, context);
        
        if (context.length() == 0) {
            context.append("Unknown ").append(validationResult.getType().name().toLowerCase()).append(" failure");
        }
        
        return context.toString();
    }
    
    /**
     * Extract Maven-specific error information.
     */
    private void extractMavenErrors(String stderr, String stdout, StringBuilder context) {
        String combinedOutput = stderr + "\n" + stdout;
        Matcher matcher = MAVEN_ERROR_PATTERN.matcher(combinedOutput);
        
        int errorCount = 0;
        while (matcher.find() && errorCount < 3) {
            String error = matcher.group().trim();
            if (!error.isEmpty() && !error.contains("BUILD FAILURE")) {
                if (context.length() > 0) context.append("; ");
                context.append(error);
                errorCount++;
            }
        }
    }
    
    /**
     * Extract Gradle-specific error information.
     */
    private void extractGradleErrors(String stderr, String stdout, StringBuilder context) {
        String combinedOutput = stderr + "\n" + stdout;
        Matcher matcher = GRADLE_ERROR_PATTERN.matcher(combinedOutput);
        
        int errorCount = 0;
        while (matcher.find() && errorCount < 3) {
            String error = matcher.group().trim();
            if (!error.isEmpty()) {
                if (context.length() > 0) context.append("; ");
                context.append(error);
                errorCount++;
            }
        }
    }
    
    /**
     * Extract Spring-specific error information.
     */
    private void extractSpringErrors(String stderr, String stdout, StringBuilder context) {
        String combinedOutput = stderr + "\n" + stdout;
        Matcher matcher = SPRING_CONTEXT_ERROR_PATTERN.matcher(combinedOutput);
        
        while (matcher.find()) {
            String error = matcher.group().trim();
            if (!error.isEmpty()) {
                if (context.length() > 0) context.append("; ");
                context.append("Spring Context Error: " + error);
                break; // Only include first Spring error to avoid overwhelming context
            }
        }
    }
    
    /**
     * Determine if a validation failure is retryable based on error patterns.
     */
    private boolean isRetryableFailure(ValidationResult validationResult) {
        String stderr = validationResult.getStderr();
        String stdout = validationResult.getStdout();
        String combinedOutput = stderr + "\n" + stdout;
        
        // Non-retryable failures (syntax errors, missing dependencies)
        if (combinedOutput.contains("cannot find symbol") ||
            combinedOutput.contains("package does not exist") ||
            combinedOutput.contains("incompatible types") ||
            combinedOutput.contains("method does not override")) {
            return false;
        }
        
        // Retryable failures (transient issues, configuration problems)
        if (combinedOutput.contains("Connection refused") ||
            combinedOutput.contains("timeout") ||
            combinedOutput.contains("NoSuchBeanDefinitionException") ||
            combinedOutput.contains("BeanCreationException")) {
            return true;
        }
        
        // Default to retryable for unknown failures
        return true;
    }
    
    /**
     * Internal class to represent validation results.
     */
    private static class ValidationResult {
        private final ValidationType type;
        private final boolean successful;
        private final int exitCode;
        private final String stdout;
        private final String stderr;
        
        public ValidationResult(ValidationType type, boolean successful, int exitCode, String stdout, String stderr) {
            this.type = type;
            this.successful = successful;
            this.exitCode = exitCode;
            this.stdout = stdout;
            this.stderr = stderr;
        }
        
        public ValidationType getType() {
            return type;
        }
        
        public boolean isSuccessful() {
            return successful;
        }
        
        public int getExitCode() {
            return exitCode;
        }
        
        public String getStdout() {
            return stdout;
        }
        
        public String getStderr() {
            return stderr;
        }
    }
}