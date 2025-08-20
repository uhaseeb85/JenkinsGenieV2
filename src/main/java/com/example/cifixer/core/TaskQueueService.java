package com.example.cifixer.core;

import com.example.cifixer.store.TaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Database-backed implementation of the TaskQueue interface.
 * Uses PostgreSQL SELECT FOR UPDATE SKIP LOCKED for efficient task processing.
 */
@Service
@Transactional
public class TaskQueueService implements TaskQueue {
    
    private static final Logger logger = LoggerFactory.getLogger(TaskQueueService.class);
    
    private final TaskRepository taskRepository;
    
    public TaskQueueService(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }
    
    @Override
    public void enqueue(Task task) {
        logger.info("Enqueuing task: type={}, buildId={}", 
            task.getType(), task.getBuild().getId());
        
        taskRepository.save(task);
        
        logger.debug("Task enqueued successfully: id={}", task.getId());
    }
    
    @Override
    public Optional<Task> dequeue(String agentType) {
        logger.debug("Dequeuing task for agent type: {}", agentType);
        
        // Convert agent type to TaskType enum
        TaskType taskType;
        try {
            taskType = TaskType.valueOf(agentType.toUpperCase());
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid agent type: {}", agentType);
            return Optional.empty();
        }
        
        // Find and lock the next available task
        Optional<Task> taskOpt = taskRepository.findNextPendingTaskForUpdate(taskType.name());
        
        if (taskOpt.isPresent()) {
            Task task = taskOpt.get();
            
            // Mark task as in progress
            task.setStatus(TaskStatus.IN_PROGRESS);
            task.setAttempt(task.getAttempt() + 1);
            taskRepository.save(task);
            
            logger.info("Dequeued task: id={}, type={}, attempt={}", 
                task.getId(), task.getType(), task.getAttempt());
            
            return Optional.of(task);
        }
        
        logger.debug("No pending tasks found for agent type: {}", agentType);
        return Optional.empty();
    }
    
    @Override
    public void updateStatus(Long taskId, TaskStatus status) {
        updateStatus(taskId, status, null);
    }
    
    @Override
    public void updateStatus(Long taskId, TaskStatus status, String errorMessage) {
        logger.debug("Updating task status: id={}, status={}", taskId, status);
        
        Optional<Task> taskOpt = taskRepository.findById(taskId);
        if (taskOpt.isPresent()) {
            Task task = taskOpt.get();
            task.setStatus(status);
            
            if (errorMessage != null) {
                task.setErrorMessage(errorMessage);
            }
            
            taskRepository.save(task);
            
            logger.info("Task status updated: id={}, status={}, attempt={}", 
                taskId, status, task.getAttempt());
        } else {
            logger.warn("Task not found for status update: id={}", taskId);
        }
    }
    
    /**
     * Checks if a task should be retried based on its current attempt count.
     *
     * @param task The task to check
     * @return true if the task should be retried
     */
    public boolean shouldRetry(Task task) {
        return task.getAttempt() < task.getMaxAttempts();
    }
    
    @Override
    public Task findById(Long taskId) {
        Optional<Task> taskOpt = taskRepository.findById(taskId);
        return taskOpt.orElse(null);
    }
    
    /**
     * Requeues a failed task for retry if it hasn't exceeded max attempts.
     *
     * @param taskId The ID of the task to retry
     * @param errorMessage The error message from the failed attempt
     * @return true if the task was requeued, false if max attempts exceeded
     */
    public boolean requeueForRetry(Long taskId, String errorMessage) {
        Optional<Task> taskOpt = taskRepository.findById(taskId);
        if (taskOpt.isPresent()) {
            Task task = taskOpt.get();
            
            if (shouldRetry(task)) {
                task.setStatus(TaskStatus.RETRY);
                task.setErrorMessage(errorMessage);
                taskRepository.save(task);
                
                logger.info("Task requeued for retry: id={}, attempt={}/{}", 
                    taskId, task.getAttempt(), task.getMaxAttempts());
                return true;
            } else {
                task.setStatus(TaskStatus.FAILED);
                task.setErrorMessage(errorMessage);
                taskRepository.save(task);
                
                logger.warn("Task failed permanently: id={}, maxAttempts={}", 
                    taskId, task.getMaxAttempts());
                return false;
            }
        }
        
        logger.warn("Task not found for retry: id={}", taskId);
        return false;
    }
}