package com.example.cifixer.store;

/**
 * Enumeration of possible build statuses in the CI fixer system.
 */
public enum BuildStatus {
    /**
     * Build is being processed by the system
     */
    PROCESSING,
    
    /**
     * Build has been successfully fixed
     */
    FIXED,
    
    /**
     * Build processing completed successfully
     */
    COMPLETED,
    
    /**
     * Build could not be fixed automatically
     */
    FAILED,
    
    /**
     * Build processing was cancelled
     */
    CANCELLED
}