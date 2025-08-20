package com.example.cifixer.core;

import com.example.cifixer.store.Build;
import com.example.cifixer.store.BuildRepository;
import com.example.cifixer.store.BuildStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Handles task retry logic with exponential backoff and comprehensive error handling.
 */
@Component
public class RetryHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(RetryHandler.class);
    
    private final TaskQueue taskQueue;
    private final BuildRepository buildRepository;
    private final ScheduledExecutorService retryScheduler;
    
    @Value("${retry.base.delay.seconds:2}")
    private int baseDelaySeconds;
    
    @Value("${retry.max.delay.seconds:300}")
    private int maxDelaySeconds;
    
    @Value("${retry.jitter.enabled:true}")
    private boolean jitterEnabled;
    
    @Value("${retry.jitter.factor:0.1}")
    private double jitterFactor;
    
    public RetryHandler(TaskQueue taskQueue, BuildRepository buildRepository) {
        this.taskQueue = taskQueue;
        this.buildRepository = buildRepository;
        this.retryScheduler = Executors.newScheduledThreadPool(5, r -> {
            Thread t = new Thread(r, "retry-handler");
            t.setDaemon(true);
            return t;
        });
    }
    
    /**
     * Handles task failure with retry logic and exponential backoff.
     *
     * @param task The failed task
     * @param error The error that occurred
     */
    public void handleTaskFailure(Task task, Exception error) {
        String correlationId = generateCorrelationId(task);
        
        try {
            MDC.put("correlationId", correlationId);
            MDC.put("taskId", String.valueOf(task.getId()));
            MDC.put("buildId", String.valueOf(task.getBuild().getId()));
            MDC.put("taskType", task.getType().name());
            
            logger.error("Task failed: attempt={}/{}, error={}", 
                task.getAttempt(), task.getMaxAttempts(), error.getMessage(), error);
            
            String errorMessage = buildErrorMessage(error);
            
            if (shouldRetry(task, error)) {
                scheduleRetry(task, errorMessage, correlationId);
            } else {
                markTaskAsPermanentlyFailed(task, errorMessage, correlationId);
            }
            
        } finally {
            MDC.clear();
        }
    }
    
    /**
     * Determines if a task should be retried based on attempt count and error type.
     */
    private boolean shouldRetry(Task task, Exception error) {
        if (task.getAttempt() >= task.getMaxAttempts()) {
            return false;
        }
        
        // Don't retry certain types of errors
        if (isNonRetryableError(error)) {
            logger.warn("Non-retryable error detected: {}", error.getClass().getSimpleName());
            return false;
        }
        
        return true;
    }
    
    /**
     * Checks if an error is non-retryable (e.g., validation errors, security exceptions).
     */
    private boolean isNonRetryableError(Exception error) {
        return error instanceof SecurityException ||
               error instanceof IllegalArgumentException ||
               error instanceof com.example.cifixer.web.WebhookValidator.ValidationException ||
               (error.getMessage() != null && 
                (error.getMessage().contains("Invalid file path") ||
                 error.getMessage().contains("Dangerous operation") ||
                 error.getMessage().contains("Authentication failed")));
    }
    
    /**
     * Schedules a task retry with exponential backoff.
     */
    private void scheduleRetry(Task task, String errorMessage, String correlationId) {
        long delaySeconds = calculateRetryDelay(task.getAttempt());
        
        logger.info("Scheduling task retry: taskId={}, attempt={}/{}, delay={}s", 
            task.getId(), task.getAttempt() + 1, task.getMaxAttempts(), delaySeconds);
        
        // Update task status to RETRY with delay information
        task.setStatus(TaskStatus.RETRY);
        task.setErrorMessage(errorMessage + " (retry in " + delaySeconds + "s)");
        task.setAttempt(task.getAttempt() + 1);
        
        // Schedule the retry
        CompletableFuture.runAsync(() -> {
            try {
                MDC.put("correlationId", correlationId);
                MDC.put("taskId", String.valueOf(task.getId()));
                MDC.put("buildId", String.valueOf(task.getBuild().getId()));
                
                // Reset task status to PENDING for retry
                task.setStatus(TaskStatus.PENDING);
                task.setErrorMessage(null);
                taskQueue.enqueue(task);
                
                logger.info("Task retry queued: taskId={}, attempt={}", 
                    task.getId(), task.getAttempt());
                
            } catch (Exception e) {
                logger.error("Failed to queue task retry: taskId={}", task.getId(), e);
            } finally {
                MDC.clear();
            }
        }, CompletableFuture.delayedExecutor(delaySeconds, TimeUnit.SECONDS, retryScheduler));
    }
    
    /**
     * Marks a task as permanently failed and updates the build status.
     */
    private void markTaskAsPermanentlyFailed(Task task, String errorMessage, String correlationId) {
        logger.error("Task failed permanently: taskId={}, buildId={}, finalError={}", 
            task.getId(), task.getBuild().getId(), errorMessage);
        
        // Update task status
        taskQueue.updateStatus(task.getId(), TaskStatus.FAILED, errorMessage);
        
        // Mark build as failed
        Build build = task.getBuild();
        build.setStatus(BuildStatus.FAILED);
        buildRepository.save(build);
        
        // Log failure metrics for monitoring
        logger.warn("Build failed due to task failure: buildId={}, job={}, buildNumber={}, taskType={}", 
            build.getId(), build.getJob(), build.getBuildNumber(), task.getType());
    }
    
    /**
     * Calculates retry delay using exponential backoff with optional jitter.
     */
    private long calculateRetryDelay(int attempt) {
        // Exponential backoff: baseDelay * 2^attempt
        long delay = (long) (baseDelaySeconds * Math.pow(2, attempt));
        
        // Cap at maximum delay
        delay = Math.min(delay, maxDelaySeconds);
        
        // Add jitter to prevent thundering herd
        if (jitterEnabled) {
            double jitter = delay * jitterFactor * Math.random();
            delay += (long) jitter;
        }
        
        return delay;
    }
    
    /**
     * Builds a comprehensive error message from the exception.
     */
    private String buildErrorMessage(Exception error) {
        StringBuilder message = new StringBuilder();
        message.append(error.getClass().getSimpleName());
        
        if (error.getMessage() != null) {
            message.append(": ").append(error.getMessage());
        }
        
        // Add cause information if available
        Throwable cause = error.getCause();
        if (cause != null && cause.getMessage() != null) {
            message.append(" (caused by: ").append(cause.getMessage()).append(")");
        }
        
        return message.toString();
    }
    
    /**
     * Generates a correlation ID for tracking related log entries.
     */
    private String generateCorrelationId(Task task) {
        return String.format("build-%d-task-%d-%d", 
            task.getBuild().getId(), task.getId(), System.currentTimeMillis());
    }
    
    /**
     * Manually retries a failed task (for admin operations).
     *
     * @param taskId The ID of the task to retry
     * @return true if retry was scheduled, false if task cannot be retried
     */
    public boolean manualRetry(Long taskId) {
        try {
            Task task = taskQueue.findById(taskId);
            if (task == null) {
                logger.warn("Cannot retry task - not found: taskId={}", taskId);
                return false;
            }
            
            if (task.getStatus() != TaskStatus.FAILED) {
                logger.warn("Cannot retry task - not in FAILED status: taskId={}, status={}", 
                    taskId, task.getStatus());
                return false;
            }
            
            // Reset attempt count for manual retry
            task.setAttempt(0);
            task.setStatus(TaskStatus.PENDING);
            task.setErrorMessage(null);
            taskQueue.enqueue(task);
            
            logger.info("Manual retry scheduled: taskId={}", taskId);
            return true;
            
        } catch (Exception e) {
            logger.error("Failed to schedule manual retry: taskId={}", taskId, e);
            return false;
        }
    }
    
    /**
     * Gets retry statistics for monitoring.
     */
    public RetryStatistics getRetryStatistics() {
        // This would typically query the database for retry metrics
        // For now, return a placeholder implementation
        return new RetryStatistics(0, 0, 0);
    }
    
    /**
     * Shuts down the retry scheduler gracefully.
     */
    public void shutdown() {
        logger.info("Shutting down retry handler");
        retryScheduler.shutdown();
        try {
            if (!retryScheduler.awaitTermination(30, TimeUnit.SECONDS)) {
                retryScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            retryScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * Statistics about retry operations.
     */
    public static class RetryStatistics {
        private final long totalRetries;
        private final long successfulRetries;
        private final long failedRetries;
        
        public RetryStatistics(long totalRetries, long successfulRetries, long failedRetries) {
            this.totalRetries = totalRetries;
            this.successfulRetries = successfulRetries;
            this.failedRetries = failedRetries;
        }
        
        public long getTotalRetries() { return totalRetries; }
        public long getSuccessfulRetries() { return successfulRetries; }
        public long getFailedRetries() { return failedRetries; }
        
        public double getSuccessRate() {
            return totalRetries > 0 ? (double) successfulRetries / totalRetries : 0.0;
        }
    }
}