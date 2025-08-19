package com.example.cifixer.core;

/**
 * Enumeration of task types in the multi-agent CI fixer system.
 */
public enum TaskType {
    /**
     * Analyze build logs and create a structured plan
     */
    PLAN,
    
    /**
     * Clone repository and prepare working directory
     */
    REPO,
    
    /**
     * Retrieve and rank candidate files for fixing
     */
    RETRIEVE,
    
    /**
     * Generate and apply code patches
     */
    PATCH,
    
    /**
     * Validate that fixes resolve the build issues
     */
    VALIDATE,
    
    /**
     * Create GitHub pull request with fixes
     */
    CREATE_PR,
    
    /**
     * Send notifications to stakeholders
     */
    NOTIFY
}