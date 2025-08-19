package com.example.cifixer.core;

import com.example.cifixer.agents.NotificationAgent;
import com.example.cifixer.agents.NotifyPayload;
import com.example.cifixer.store.Build;
import com.example.cifixer.store.BuildRepository;
import com.example.cifixer.store.BuildStatus;
import com.example.cifixer.store.NotificationType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    
    @Autowired
    private NotificationAgent notificationAgent;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Value("${orchestrator.processing.enabled:true}")
    private boolean processingEnabled;
    
    @Value("${orchestrator.max.concurrent.tasks:5}")
    private int maxConcurrentTasks;
    
    // Track currently processing tasks to avoid overloading
    private final Map<String, Integer> activeTaskCounts = new HashMap<>();
    
    public Orchestrator(TaskQueue taskQueue, BuildRepository buildRepository) {
        this.taskQueue = taskQueue;
        this.buildRepository = buildRepository;
        
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
        logger.info("Dispatching task: id={}, type={}, buildId={}", 
            task.getId(), task.getType(), task.getBuild().getId());
        
        try {
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
            logger.error("Error dispatching task: id={}", task.getId(), e);
            handleTaskFailure(task, e);
        } finally {
            // Decrement active task count
            String agentType = task.getType().name();
            int currentCount = activeTaskCounts.getOrDefault(agentType, 0);
            activeTaskCounts.put(agentType, Math.max(0, currentCount - 1));
        }
    }
    
    /**
     * Placeholder for PLAN task handling.
     */
    private void handlePlanTask(Task task) {
        logger.info("Processing PLAN task: id={}", task.getId());
        
        // Placeholder: Mark as completed for now
        // In task 5, this will call PlannerAgent
        taskQueue.updateStatus(task.getId(), TaskStatus.COMPLETED);
        
        // Create next task in the pipeline (REPO)
        createNextTask(task.getBuild(), TaskType.REPO);
    }
    
    /**
     * Placeholder for REPO task handling.
     */
    private void handleRepoTask(Task task) {
        logger.info("Processing REPO task: id={}", task.getId());
        
        // Placeholder: Mark as completed for now
        // In task 6, this will call RepoAgent
        taskQueue.updateStatus(task.getId(), TaskStatus.COMPLETED);
        
        // Create next task in the pipeline (RETRIEVE)
        createNextTask(task.getBuild(), TaskType.RETRIEVE);
    }
    
    /**
     * Placeholder for RETRIEVE task handling.
     */
    private void handleRetrieveTask(Task task) {
        logger.info("Processing RETRIEVE task: id={}", task.getId());
        
        // Placeholder: Mark as completed for now
        taskQueue.updateStatus(task.getId(), TaskStatus.COMPLETED);
        
        // Create next task in the pipeline (PATCH)
        createNextTask(task.getBuild(), TaskType.PATCH);
    }
    
    /**
     * Placeholder for PATCH task handling.
     */
    private void handlePatchTask(Task task) {
        logger.info("Processing PATCH task: id={}", task.getId());
        
        // Placeholder: Mark as completed for now
        taskQueue.updateStatus(task.getId(), TaskStatus.COMPLETED);
        
        // Create next task in the pipeline (VALIDATE)
        createNextTask(task.getBuild(), TaskType.VALIDATE);
    }
    
    /**
     * Placeholder for VALIDATE task handling.
     */
    private void handleValidateTask(Task task) {
        logger.info("Processing VALIDATE task: id={}", task.getId());
        
        // Placeholder: Mark as completed for now
        taskQueue.updateStatus(task.getId(), TaskStatus.COMPLETED);
        
        // Create next task in the pipeline (CREATE_PR)
        createNextTask(task.getBuild(), TaskType.CREATE_PR);
    }
    
    /**
     * Placeholder for CREATE_PR task handling.
     */
    private void handleCreatePrTask(Task task) {
        logger.info("Processing CREATE_PR task: id={}", task.getId());
        
        // Placeholder: Mark as completed for now
        taskQueue.updateStatus(task.getId(), TaskStatus.COMPLETED);
        
        // Create final task in the pipeline (NOTIFY)
        createNextTask(task.getBuild(), TaskType.NOTIFY);
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
     */
    private void createNextTask(Build build, TaskType taskType) {
        Task nextTask = new Task(build, taskType);
        taskQueue.enqueue(nextTask);
        
        logger.debug("Created next task: type={}, buildId={}", taskType, build.getId());
    }
    
    /**
     * Handles task failures with retry logic.
     *
     * @param task The failed task
     * @param error The error that occurred
     */
    private void handleTaskFailure(Task task, Exception error) {
        logger.error("Task failed: id={}, type={}, attempt={}", 
            task.getId(), task.getType(), task.getAttempt(), error);
        
        String errorMessage = error.getMessage();
        if (errorMessage == null) {
            errorMessage = error.getClass().getSimpleName();
        }
        
        // Check if task should be retried
        if (task.getAttempt() < task.getMaxAttempts()) {
            taskQueue.updateStatus(task.getId(), TaskStatus.RETRY, errorMessage);
            logger.info("Task queued for retry: id={}, attempt={}/{}", 
                task.getId(), task.getAttempt(), task.getMaxAttempts());
        } else {
            taskQueue.updateStatus(task.getId(), TaskStatus.FAILED, errorMessage);
            
            // Mark build as failed if this was the last attempt
            Build build = task.getBuild();
            build.setStatus(BuildStatus.FAILED);
            buildRepository.save(build);
            
            logger.warn("Task failed permanently: id={}, buildId={}", 
                task.getId(), build.getId());
        }
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