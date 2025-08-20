package com.example.cifixer.web;

import com.example.cifixer.core.InputValidator;
import com.example.cifixer.core.Task;
import com.example.cifixer.core.TaskQueue;
import com.example.cifixer.core.TaskType;
import com.example.cifixer.store.Build;
import com.example.cifixer.store.BuildRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

/**
 * REST controller for handling webhook notifications from CI systems.
 */
@RestController
@RequestMapping("/webhook")
public class WebhookController {
    
    private static final Logger logger = LoggerFactory.getLogger(WebhookController.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    private final WebhookValidator webhookValidator;
    private final InputValidator inputValidator;
    private final BuildRepository buildRepository;
    private final TaskQueue taskQueue;
    
    public WebhookController(WebhookValidator webhookValidator,
                           InputValidator inputValidator,
                           BuildRepository buildRepository, 
                           TaskQueue taskQueue) {
        this.webhookValidator = webhookValidator;
        this.inputValidator = inputValidator;
        this.buildRepository = buildRepository;
        this.taskQueue = taskQueue;
    }
    
    /**
     * Handles Jenkins build failure webhooks.
     *
     * @param payload The webhook payload from Jenkins
     * @param signature Optional HMAC signature for validation
     * @return Response with build ID and status
     */
    @PostMapping("/jenkins")
    public ResponseEntity<WebhookResponse> handleJenkinsFailure(
            @RequestBody JenkinsWebhookPayload payload,
            @RequestHeader(value = "X-Jenkins-Signature", required = false) String signature,
            HttpServletRequest request) {
        
        String correlationId = "webhook-" + System.currentTimeMillis();
        
        try {
            MDC.put("correlationId", correlationId);
            
            // Log the entire payload for debugging
            try {
                logger.info("Jenkins webhook payload: {}", objectMapper.writeValueAsString(payload));
            } catch (Exception e) {
                logger.warn("Failed to serialize Jenkins webhook payload: {}", e.getMessage());
            }
            
            // Check for null or empty payload
            if (payload == null) {
                logger.warn("Received null Jenkins webhook payload");
                return ResponseEntity.badRequest()
                    .body(WebhookResponse.error("Webhook payload is null"));
            }
            
            logger.info("Received Jenkins webhook: job={}, build={}, branch={}, commit={}, repoUrl={}",
                payload.getJob(), payload.getBuildNumber(), payload.getBranch(), payload.getCommitSha(), payload.getRepoUrl());
            
            // Log additional debugging information
            logger.debug("Full payload details: jobName={}, displayName={}, fullDisplayName={}, url={}",
                payload.getJobName(), payload.getDisplayName(), payload.getFullDisplayName(), payload.getUrl());
            
            if (payload.getBuild() != null) {
                logger.debug("Build details: number={}, status={}, result={}, url={}",
                    payload.getBuild().getNumber(), payload.getBuild().getStatus(),
                    payload.getBuild().getResult(), payload.getBuild().getUrl());
                
                if (payload.getBuild().getScm() != null) {
                    JenkinsWebhookPayload.BuildData.ScmData scm = payload.getBuild().getScm();
                    logger.debug("SCM details: branch={}, commit={}, commitId={}, message={}, author={}",
                        scm.getBranch(), scm.getCommit(), scm.getCommitId(),
                        scm.getMessage(), scm.getAuthor());
                }
                
                if (payload.getBuild().getArtifacts() != null) {
                    logger.debug("Artifacts count: {}", payload.getBuild().getArtifactsList().size());
                }
                
                if (payload.getBuild().getCauses() != null) {
                    logger.debug("Causes count: {}", payload.getBuild().getCauses().size());
                }
                
                if (payload.getBuild().getParameters() != null) {
                    logger.debug("Parameters count: {}", payload.getBuild().getParameters().size());
                }
            } else {
                logger.warn("Build data is null in webhook payload");
            }
            
            // Enhanced input validation
            logger.debug("Starting input validation for Jenkins webhook payload");
            InputValidator.ValidationResult inputResult = inputValidator.validateJenkinsPayload(payload);
            if (!inputResult.isValid()) {
                logger.warn("Input validation failed: {}", inputResult.getErrorMessage());
                return ResponseEntity.badRequest()
                    .body(WebhookResponse.error("Input validation failed: " + inputResult.getErrorMessage()));
            }
            logger.debug("Input validation successful");
            
            // Log request headers for debugging purposes
            logRequestHeaders(request);
            
            // Validate the webhook payload and signature
            // We don't need to read the raw payload again, as we already have the deserialized payload object
            webhookValidator.validateJenkinsPayload(payload, signature);
            
            // Check for duplicate builds
            if (buildRepository.existsByJobAndBuildNumber(payload.getJob(), payload.getBuildNumber())) {
                logger.warn("Duplicate build detected: job={}, buildNumber={}", 
                    payload.getJob(), payload.getBuildNumber());
                return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(WebhookResponse.error("Build already exists"));
            }
            
            // Create Build entity
            Build build = createBuildFromPayload(payload);
            build = buildRepository.save(build);
            
            // Add build ID to MDC for correlation
            MDC.put("buildId", String.valueOf(build.getId()));
            
            logger.info("Created build record: id={}, job={}, buildNumber={}", 
                build.getId(), build.getJob(), build.getBuildNumber());
            
            // Create initial PLAN task
            Task planTask = new Task(build, TaskType.PLAN, createTaskPayload(payload));
            taskQueue.enqueue(planTask);
            
            logger.info("Enqueued PLAN task for build: buildId={}, taskId={}", 
                build.getId(), planTask.getId());
            
            return ResponseEntity.ok(WebhookResponse.success(build.getId()));
            
        } catch (WebhookValidator.ValidationException e) {
            logger.warn("Webhook validation failed: {}", e.getMessage());
            return ResponseEntity.badRequest()
                .body(WebhookResponse.error("Validation failed: " + e.getMessage()));
            
        } catch (SecurityException e) {
            logger.warn("Webhook security validation failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(WebhookResponse.error("Security validation failed"));
            
        } catch (Exception e) {
            logger.error("Error processing Jenkins webhook", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(WebhookResponse.error("Internal server error"));
        } finally {
            MDC.clear();
        }
    }
    
    /**
     * Health check endpoint for webhook service.
     *
     * @return Simple health status
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        Map<String, String> status = new HashMap<>();
        status.put("status", "healthy");
        status.put("service", "webhook");
        return ResponseEntity.ok(status);
    }
    
    /**
     * Creates a Build entity from the Jenkins webhook payload.
     *
     * @param payload The webhook payload
     * @return The created Build entity
     */
    private Build createBuildFromPayload(JenkinsWebhookPayload payload) {
        Build build = new Build(
            payload.extractJobName(),
            payload.extractBuildNumber(),
            payload.extractBranchName(),
            payload.extractRepoUrl(),
            payload.extractCommitSha()
        );
        
        // Store the full payload as metadata
        Map<String, Object> payloadMap = new HashMap<>();
        payloadMap.put("buildLogs", payload.extractBuildLogs());
        
        // Add SCM data to metadata
        if (payload.getBuild() != null && payload.getBuild().getScm() != null) {
            JenkinsWebhookPayload.BuildData.ScmData scm = payload.getBuild().getScm();
            Map<String, Object> scmMap = new HashMap<>();
            scmMap.put("branch", scm.getBranch());
            scmMap.put("commit", scm.getCommit());
            scmMap.put("commitId", scm.getCommitId());
            scmMap.put("message", scm.getMessage());
            scmMap.put("commitMessage", scm.getCommitMessage());
            scmMap.put("author", scm.getAuthor());
            scmMap.put("authorName", scm.getAuthorName());
            scmMap.put("authorEmail", scm.getAuthorEmail());
            payloadMap.put("scm", scmMap);
        }
        
        build.setPayload(payloadMap);
        
        return build;
    }
    
    /**
     * Creates task payload from webhook data for the PLAN task.
     *
     * @param payload The webhook payload
     * @return Task payload map
     */
    private Map<String, Object> createTaskPayload(JenkinsWebhookPayload payload) {
        Map<String, Object> taskPayload = new HashMap<>();
        taskPayload.put("buildLogs", payload.extractBuildLogs());
        taskPayload.put("repoUrl", payload.extractRepoUrl());
        taskPayload.put("branch", payload.extractBranchName());
        taskPayload.put("commitSha", payload.extractCommitSha());
        
        // Add SCM data to task payload
        if (payload.getBuild() != null && payload.getBuild().getScm() != null) {
            JenkinsWebhookPayload.BuildData.ScmData scm = payload.getBuild().getScm();
            Map<String, Object> scmMap = new HashMap<>();
            scmMap.put("branch", scm.getBranch());
            scmMap.put("commit", scm.getCommit());
            scmMap.put("commitId", scm.getCommitId());
            scmMap.put("message", scm.getMessage());
            scmMap.put("commitMessage", scm.getCommitMessage());
            scmMap.put("author", scm.getAuthor());
            scmMap.put("authorName", scm.getAuthorName());
            scmMap.put("authorEmail", scm.getAuthorEmail());
            taskPayload.put("scm", scmMap);
        }
        
        return taskPayload;
    }
    
    /**
     * Logs the request headers for debugging purposes.
     *
     * @param request The HttpServletRequest object
     */
    private void logRequestHeaders(HttpServletRequest request) {
        try {
            // Log headers
            logger.info("=== Webhook Request Headers ===");
            Enumeration<String> headerNames = request.getHeaderNames();
            while (headerNames.hasMoreElements()) {
                String headerName = headerNames.nextElement();
                String headerValue = request.getHeader(headerName);
                logger.info("{}: {}", headerName, headerValue);
            }
            logger.info("=== End of Webhook Request Headers ===");
        } catch (Exception e) {
            logger.error("Error logging request headers", e);
        }
    }
}