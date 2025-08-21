package com.example.cifixer.core;

import com.example.cifixer.agents.*;
import com.example.cifixer.git.RepoAgent;
import com.example.cifixer.store.Build;
import com.example.cifixer.store.BuildRepository;
import com.example.cifixer.store.BuildStatus;
import com.example.cifixer.store.NotificationType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Central orchestrator that manages task processing and agent dispatch.
 * Runs scheduled processing to handle queued tasks.
 */
@Service
public class Orchestrator {
    
    private static final Logger logger = LoggerFactory.getLogger(Orchestrator.class);
    
    private final TaskQueue taskQueue;
    private final BuildRepository buildRepository;
    private final RetryHandler retryHandler;
    
    @Autowired
    private NotificationAgent notificationAgent;
    
    @Autowired
    private PlannerAgent plannerAgent;
    
    @Autowired
    private RepoAgent repoAgent;
    
    @Autowired
    private RetrieverAgent retrieverAgent;
    
    @Autowired
    private CodeFixAgent codeFixAgent;
    
    @Autowired
    private ValidatorAgent validatorAgent;
    
    @Autowired
    private PrAgent prAgent;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Value("${orchestrator.processing.enabled:true}")
    private boolean processingEnabled;
    
    @Value("${orchestrator.max.concurrent.tasks:5}")
    private int maxConcurrentTasks;
    
    // Track currently processing tasks to avoid overloading
    private final Map<String, Integer> activeTaskCounts = new HashMap<>();
    
    public Orchestrator(TaskQueue taskQueue, BuildRepository buildRepository, RetryHandler retryHandler) {
        this.taskQueue = taskQueue;
        this.buildRepository = buildRepository;
        this.retryHandler = retryHandler;
        
        // Initialize counters for each task type
        for (TaskType taskType : TaskType.values()) {
            activeTaskCounts.put(taskType.name(), 0);
        }
    }
    
    /**
     * Main processing loop that runs every second to dispatch tasks to agents.
     * Uses fixed delay to ensure previous execution completes before starting next.
     */
    @Scheduled(fixedDelay = 1000)
    public void processTasks() {
        if (!processingEnabled) {
            return;
        }
        
        logger.debug("Starting task processing cycle");
        
        try {
            // Process each task type
            for (TaskType taskType : TaskType.values()) {
                processTaskType(taskType);
            }
        } catch (Exception e) {
            logger.error("Error during task processing cycle", e);
        }
    }
    
    /**
     * Process tasks of a specific type.
     *
     * @param taskType The type of tasks to process
     */
    private void processTaskType(TaskType taskType) {
        String agentType = taskType.name();
        
        // Check if we can process more tasks of this type
        int activeCount = activeTaskCounts.getOrDefault(agentType, 0);
        if (activeCount >= maxConcurrentTasks) {
            logger.debug("Max concurrent tasks reached for type: {} ({})", agentType, activeCount);
            return;
        }
        
        // Try to dequeue a task
        Optional<Task> taskOpt = taskQueue.dequeue(agentType);
        if (taskOpt.isPresent()) {
            Task task = taskOpt.get();
            
            // Increment active task count
            activeTaskCounts.put(agentType, activeCount + 1);
            
            // Dispatch task to appropriate agent (placeholder for now)
            dispatchTask(task);
        }
    }
    
    /**
     * Dispatches a task to the appropriate agent for processing.
     * This is a placeholder implementation - actual agents will be implemented in later tasks.
     *
     * @param task The task to dispatch
     */
    private void dispatchTask(Task task) {
        String correlationId = generateCorrelationId(task);
        
        try {
            MDC.put("correlationId", correlationId);
            MDC.put("taskId", String.valueOf(task.getId()));
            MDC.put("buildId", String.valueOf(task.getBuild().getId()));
            MDC.put("taskType", task.getType().name());
            
            logger.info("Dispatching task: id={}, type={}, buildId={}, attempt={}", 
                task.getId(), task.getType(), task.getBuild().getId(), task.getAttempt());
            
            // Placeholder: In future tasks, this will call actual agent implementations
            switch (task.getType()) {
                case PLAN:
                    handlePlanTask(task);
                    break;
                case REPO:
                    handleRepoTask(task);
                    break;
                case RETRIEVE:
                    handleRetrieveTask(task);
                    break;
                case PATCH:
                    handlePatchTask(task);
                    break;
                case VALIDATE:
                    handleValidateTask(task);
                    break;
                case CREATE_PR:
                    handleCreatePrTask(task);
                    break;
                case NOTIFY:
                    handleNotifyTask(task);
                    break;
                default:
                    logger.warn("Unknown task type: {}", task.getType());
                    taskQueue.updateStatus(task.getId(), TaskStatus.FAILED, 
                        "Unknown task type: " + task.getType());
            }
        } catch (Exception e) {
            logger.error("Error dispatching task: id={}, type={}, attempt={}", 
                task.getId(), task.getType(), task.getAttempt(), e);
            retryHandler.handleTaskFailure(task, e);
        } finally {
            // Decrement active task count
            String agentType = task.getType().name();
            int currentCount = activeTaskCounts.getOrDefault(agentType, 0);
            activeTaskCounts.put(agentType, Math.max(0, currentCount - 1));
            MDC.clear();
        }
    }
    
    /**
     * Handles PLAN task by calling the PlannerAgent.
     */
    private void handlePlanTask(Task task) {
        logger.info("Processing PLAN task: id={}", task.getId());
        
        try {
            // Convert task payload to the format expected by PlannerAgent
            Map<String, Object> payload = task.getPayload();
            if (payload == null) {
                payload = new HashMap<>();
            }
            
            logger.debug("Calling PlannerAgent with payload: {}", payload.keySet());
            
            // Call the actual PlannerAgent
            TaskResult result = plannerAgent.handle(task, payload);
            
            logger.info("PlannerAgent result: status={}, message={}", 
                result.getStatus(), result.getMessage());
            
            if (result.getStatus() == TaskStatus.COMPLETED) {
                taskQueue.updateStatus(task.getId(), TaskStatus.COMPLETED, result.getMessage());
                // Create next task in the pipeline (REPO) with preserved payload
                Map<String, Object> nextTaskPayload = preserveEssentialPayload(task, result.getMetadata());
                createNextTask(task.getBuild(), TaskType.REPO, nextTaskPayload);
            } else {
                taskQueue.updateStatus(task.getId(), result.getStatus(), result.getMessage());
            }
            
        } catch (Exception e) {
            logger.error("Error in PlannerAgent for task: {}", task.getId(), e);
            taskQueue.updateStatus(task.getId(), TaskStatus.FAILED, 
                "PlannerAgent failed: " + e.getMessage());
        }
    }
    
    /**
     * Handles REPO task by calling the RepoAgent.
     */
    private void handleRepoTask(Task task) {
        logger.info("Processing REPO task: id={}", task.getId());
        
        try {
            // Get task payload as Map<String, Object>
            Map<String, Object> payload = task.getPayload();
            if (payload == null) {
                payload = new HashMap<>();
            }
            
            logger.debug("Calling RepoAgent for repository operations with payload keys: {}", payload.keySet());
            
            // Call the actual RepoAgent
            TaskResult result = repoAgent.handle(task, payload);
            
            logger.info("RepoAgent result: status={}, message={}", 
                result.getStatus(), result.getMessage());
            
            if (result.getStatus() == TaskStatus.COMPLETED) {
                taskQueue.updateStatus(task.getId(), TaskStatus.COMPLETED, result.getMessage());
                
                // Create next task in the pipeline (RETRIEVE)
                Map<String, Object> nextTaskPayload = preserveEssentialPayload(task, result.getMetadata());
                createNextTask(task.getBuild(), TaskType.RETRIEVE, nextTaskPayload);
            } else {
                taskQueue.updateStatus(task.getId(), result.getStatus(), result.getMessage());
            }
            
        } catch (Exception e) {
            logger.error("Error in RepoAgent for task: {}", task.getId(), e);
            taskQueue.updateStatus(task.getId(), TaskStatus.FAILED, 
                "RepoAgent failed: " + e.getMessage());
        }
    }
    
    /**
     * Handles RETRIEVE task by calling the RetrieverAgent.
     */
    private void handleRetrieveTask(Task task) {
        logger.info("Processing RETRIEVE task: id={}", task.getId());
        
        try {
            // Convert task payload
            Map<String, Object> payload = task.getPayload();
            if (payload == null) {
                payload = new HashMap<>();
            }
            
            logger.debug("Calling RetrieverAgent with payload: {}", payload.keySet());
            
            // Call the actual RetrieverAgent
            TaskResult result = retrieverAgent.handle(task, payload);
            
            logger.info("RetrieverAgent result: status={}, message={}", 
                result.getStatus(), result.getMessage());
            
            if (result.getStatus() == TaskStatus.COMPLETED) {
                taskQueue.updateStatus(task.getId(), TaskStatus.COMPLETED, result.getMessage());
                // Create next task in the pipeline (PATCH)
                Map<String, Object> nextTaskPayload = preserveEssentialPayload(task, result.getMetadata());
                createNextTask(task.getBuild(), TaskType.PATCH, nextTaskPayload);
            } else {
                taskQueue.updateStatus(task.getId(), result.getStatus(), result.getMessage());
            }
            
        } catch (Exception e) {
            logger.error("Error in RetrieverAgent for task: {}", task.getId(), e);
            taskQueue.updateStatus(task.getId(), TaskStatus.FAILED, 
                "RetrieverAgent failed: " + e.getMessage());
        }
    }
    
    /**
     * Handles PATCH task by calling the CodeFixAgent.
     */
    private void handlePatchTask(Task task) {
        logger.info("Processing PATCH task: id={}", task.getId());
        
        try {
            // Get task payload as Map<String, Object>
            Map<String, Object> payload = task.getPayload();
            if (payload == null) {
                payload = new HashMap<>();
            }
            
            logger.debug("Calling CodeFixAgent with payload containing keys: {}", payload.keySet());
            
            // Call the actual CodeFixAgent
            TaskResult result = codeFixAgent.handle(task, payload);
            
            logger.info("CodeFixAgent result: status={}, message={}", 
                result.getStatus(), result.getMessage());
            
            if (result.getStatus() == TaskStatus.COMPLETED) {
                taskQueue.updateStatus(task.getId(), TaskStatus.COMPLETED, result.getMessage());
                // SKIP VALIDATION - Go directly to PR creation after patch is applied
                logger.info("Skipping validation - proceeding directly to PR creation for build: {}", task.getBuild().getId());
                Map<String, Object> nextTaskPayload = preserveEssentialPayload(task, result.getMetadata());
                createNextTask(task.getBuild(), TaskType.CREATE_PR, nextTaskPayload);
            } else {
                taskQueue.updateStatus(task.getId(), result.getStatus(), result.getMessage());
            }
            
        } catch (Exception e) {
            logger.error("Error in CodeFixAgent for task: {}", task.getId(), e);
            taskQueue.updateStatus(task.getId(), TaskStatus.FAILED, 
                "CodeFixAgent failed: " + e.getMessage());
        }
    }
    
    /**
     * Handles VALIDATE task by calling the ValidatorAgent.
     */
    private void handleValidateTask(Task task) {
        logger.info("Processing VALIDATE task: id={}", task.getId());
        
        try {
            // Convert task payload to ValidatePayload
            Map<String, Object> payloadMap = task.getPayload();
            if (payloadMap == null) {
                payloadMap = new HashMap<>();
            }
            
            ValidatePayload payload = objectMapper.convertValue(payloadMap, ValidatePayload.class);
            
            logger.debug("Calling ValidatorAgent for build validation");
            
            // Call the actual ValidatorAgent
            TaskResult result = validatorAgent.handle(task, payload);
            
            logger.info("ValidatorAgent result: status={}, message={}", 
                result.getStatus(), result.getMessage());
            
            if (result.getStatus() == TaskStatus.COMPLETED) {
                taskQueue.updateStatus(task.getId(), TaskStatus.COMPLETED, result.getMessage());
                // Create next task in the pipeline (CREATE_PR)
                Map<String, Object> nextTaskPayload = preserveEssentialPayload(task, result.getMetadata());
                createNextTask(task.getBuild(), TaskType.CREATE_PR, nextTaskPayload);
            } else {
                taskQueue.updateStatus(task.getId(), result.getStatus(), result.getMessage());
            }
            
        } catch (Exception e) {
            logger.error("Error in ValidatorAgent for task: {}", task.getId(), e);
            taskQueue.updateStatus(task.getId(), TaskStatus.FAILED, 
                "ValidatorAgent failed: " + e.getMessage());
        }
    }
    
    /**
     * Handles CREATE_PR task by calling the PrAgent.
     */
    private void handleCreatePrTask(Task task) {
        logger.info("Processing CREATE_PR task: id={}", task.getId());
        
        try {
            // Convert task payload to PrPayload
            Map<String, Object> payloadMap = task.getPayload();
            if (payloadMap == null) {
                payloadMap = new HashMap<>();
            }
            
            PrPayload payload = objectMapper.convertValue(payloadMap, PrPayload.class);
            
            logger.debug("Calling PrAgent for GitHub PR creation");
            
            // Call the actual PrAgent
            TaskResult result = prAgent.handle(task, payload);
            
            logger.info("PrAgent result: status={}, message={}", 
                result.getStatus(), result.getMessage());
            
            if (result.getStatus() == TaskStatus.COMPLETED) {
                taskQueue.updateStatus(task.getId(), TaskStatus.COMPLETED, result.getMessage());
                // Create final task in the pipeline (NOTIFY)
                createNextTask(task.getBuild(), TaskType.NOTIFY, result.getMetadata());
            } else {
                taskQueue.updateStatus(task.getId(), result.getStatus(), result.getMessage());
            }
            
        } catch (Exception e) {
            logger.error("Error in PrAgent for task: {}", task.getId(), e);
            taskQueue.updateStatus(task.getId(), TaskStatus.FAILED, 
                "PrAgent failed: " + e.getMessage());
        }
    }
    
    /**
     * Handles NOTIFY task by calling the NotificationAgent.
     */
    private void handleNotifyTask(Task task) {
        logger.info("Processing NOTIFY task: id={}", task.getId());
        
        try {
            // Parse payload or create default success notification
            NotifyPayload payload;
            if (task.getPayload() != null && !task.getPayload().isEmpty()) {
                payload = objectMapper.convertValue(task.getPayload(), NotifyPayload.class);
            } else {
                // Default to success notification if no payload specified
                payload = new NotifyPayload();
                payload.setNotificationType(NotificationType.SUCCESS);
            }
            
            // Call the notification agent
            TaskResult result = notificationAgent.handle(task, payload);
            
            if (result.getStatus() == TaskStatus.COMPLETED) {
                taskQueue.updateStatus(task.getId(), TaskStatus.COMPLETED, result.getMessage());
                
                // Mark build as completed
                Build build = task.getBuild();
                build.setStatus(BuildStatus.COMPLETED);
                buildRepository.save(build);
                
                logger.info("Build processing completed: buildId={}", build.getId());
            } else {
                taskQueue.updateStatus(task.getId(), result.getStatus(), result.getMessage());
            }
            
        } catch (Exception e) {
            logger.error("Error processing NOTIFY task: id={}", task.getId(), e);
            taskQueue.updateStatus(task.getId(), TaskStatus.FAILED, "Notification failed: " + e.getMessage());
        }
    }
    
    /**
     * Creates the next task in the processing pipeline.
     *
     * @param build The build being processed
     * @param taskType The type of task to create
     * @param metadata Optional metadata from previous task
     */
    /**
     * Preserves essential payload data when creating the next task in the pipeline.
     * This ensures that key information like repoUrl, branch, commitSha, etc. 
     * is not lost when transitioning between agents.
     */
    private Map<String, Object> preserveEssentialPayload(Task currentTask, Map<String, Object> newMetadata) {
        Map<String, Object> preservedPayload = new HashMap<>();
        
        // Get current task payload
        Map<String, Object> currentPayload = currentTask.getPayload();
        if (currentPayload != null) {
            // Preserve key data needed by subsequent agents
            String[] essentialKeys = {"repoUrl", "branch", "commitSha", "buildLogs", "scm", "workingDirectory", "fixBranch"};
            for (String key : essentialKeys) {
                if (currentPayload.containsKey(key)) {
                    preservedPayload.put(key, currentPayload.get(key));
                }
            }
            
            // Map field names for PrPayload compatibility
            if (currentPayload.containsKey("fixBranch")) {
                preservedPayload.put("branchName", currentPayload.get("fixBranch"));
            } else if (currentPayload.containsKey("branch")) {
                preservedPayload.put("branchName", currentPayload.get("branch"));
            }
            
            // Set default base branch if not present
            if (!preservedPayload.containsKey("baseBranch")) {
                preservedPayload.put("baseBranch", "main");
            }
            
            logger.debug("Preserved essential payload keys: {}", preservedPayload.keySet());
        }
        
        // Add new metadata (this can override preserved data if needed)
        if (newMetadata != null) {
            preservedPayload.putAll(newMetadata);
            logger.debug("Added new metadata keys: {}", newMetadata.keySet());
        }
        
        return preservedPayload;
    }

    private void createNextTask(Build build, TaskType taskType, Map<String, Object> payload) {
        Task nextTask = new Task(build, taskType);
        
        // Set the provided payload
        if (payload != null && !payload.isEmpty()) {
            nextTask.setPayload(payload);
        }
        
        taskQueue.enqueue(nextTask);
        
        logger.debug("Created next task: type={}, buildId={}, with payload keys={}", 
            taskType, build.getId(), payload != null ? payload.keySet() : "none");
    }
    
    /**
     * Generates a correlation ID for tracking related operations.
     */
    private String generateCorrelationId(Task task) {
        return String.format("orch-%d-%d-%d", 
            task.getBuild().getId(), task.getId(), System.currentTimeMillis());
    }
    
    /**
     * Gets the current status of task processing.
     *
     * @return Map of task types to active task counts
     */
    public Map<String, Integer> getActiveTaskCounts() {
        return new HashMap<>(activeTaskCounts);
    }
    
    /**
     * Enables or disables task processing.
     *
     * @param enabled Whether processing should be enabled
     */
    public void setProcessingEnabled(boolean enabled) {
        this.processingEnabled = enabled;
        logger.info("Task processing {}", enabled ? "enabled" : "disabled");
    }
    
    /**
     * Checks if task processing is currently enabled.
     *
     * @return true if processing is enabled
     */
    public boolean isProcessingEnabled() {
        return processingEnabled;
    }
}