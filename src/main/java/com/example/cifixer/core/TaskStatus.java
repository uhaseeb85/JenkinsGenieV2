package com.example.cifixer.core;

/**
 * Enumeration of possible task statuses in the CI fixer system.
 */
public enum TaskStatus {
    /**
     * Task is waiting to be processed
     */
    PENDING,
    
    /**
     * Task is currently being processed
     */
    IN_PROGRESS,
    
    /**
     * Task completed successfully
     */
    COMPLETED,
    
    /**
     * Task failed and should be retried
     */
    RETRY,
    
    /**
     * Task failed permanently
     */
    FAILED
}