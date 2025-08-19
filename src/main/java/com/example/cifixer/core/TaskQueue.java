package com.example.cifixer.core;

import java.util.Optional;

/**
 * Interface for managing the task queue in the CI fixer system.
 */
public interface TaskQueue {
    
    /**
     * Adds a task to the queue for processing.
     *
     * @param task The task to enqueue
     */
    void enqueue(Task task);
    
    /**
     * Retrieves the next available task for the specified agent type.
     * Uses database-level locking to prevent race conditions.
     *
     * @param agentType The type of agent requesting a task
     * @return An optional task, empty if no tasks are available
     */
    Optional<Task> dequeue(String agentType);
    
    /**
     * Updates the status of a task.
     *
     * @param taskId The ID of the task to update
     * @param status The new status
     */
    void updateStatus(Long taskId, TaskStatus status);
    
    /**
     * Updates the status of a task with an error message.
     *
     * @param taskId The ID of the task to update
     * @param status The new status
     * @param errorMessage The error message to store
     */
    void updateStatus(Long taskId, TaskStatus status, String errorMessage);
}