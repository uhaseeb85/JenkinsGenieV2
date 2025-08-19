package com.example.cifixer.store;

/**
 * Enumeration of possible pull request statuses.
 */
public enum PullRequestStatus {
    /**
     * Pull request has been created
     */
    CREATED,
    
    /**
     * Pull request creation failed
     */
    FAILED,
    
    /**
     * Pull request has been merged
     */
    MERGED,
    
    /**
     * Pull request has been closed without merging
     */
    CLOSED
}