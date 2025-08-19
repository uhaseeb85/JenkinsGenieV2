package com.example.cifixer.web;

import com.example.cifixer.core.Task;
import com.example.cifixer.core.TaskQueue;
import com.example.cifixer.core.TaskType;
import com.example.cifixer.store.Build;
import com.example.cifixer.store.BuildRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * REST controller for handling webhook notifications from CI systems.
 */
@RestController
@RequestMapping("/webhooks")
public class WebhookController {
    
    private static final Logger logger = LoggerFactory.getLogger(WebhookController.class);
    
    private final WebhookValidator webhookValidator;
    private final BuildRepository buildRepository;
    private final TaskQueue taskQueue;
    
    public WebhookController(WebhookValidator webhookValidator, 
                           BuildRepository buildRepository, 
                           TaskQueue taskQueue) {
        this.webhookValidator = webhookValidator;
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
            @RequestHeader(value = "X-Jenkins-Signature", required = false) String signature) {
        
        logger.info("Received Jenkins webhook: job={}, build={}, branch={}, commit={}", 
            payload.getJob(), payload.getBuildNumber(), payload.getBranch(), payload.getCommitSha());
        
        try {
            // Validate the webhook payload and signature
            webhookValidator.validateJenkinsPayload(payload, signature);
            
            // Create Build entity
            Build build = createBuildFromPayload(payload);
            build = buildRepository.save(build);
            
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
            payload.getJob(),
            payload.getBuildNumber(),
            payload.getBranch(),
            payload.getRepoUrl(),
            payload.getCommitSha()
        );
        
        // Store the full payload as metadata
        Map<String, Object> payloadMap = new HashMap<>();
        payloadMap.put("buildLogs", payload.getBuildLogs());
        payloadMap.put("metadata", payload.getMetadata());
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
        taskPayload.put("buildLogs", payload.getBuildLogs());
        taskPayload.put("repoUrl", payload.getRepoUrl());
        taskPayload.put("branch", payload.getBranch());
        taskPayload.put("commitSha", payload.getCommitSha());
        
        if (payload.getMetadata() != null) {
            taskPayload.put("metadata", payload.getMetadata());
        }
        
        return taskPayload;
    }
}