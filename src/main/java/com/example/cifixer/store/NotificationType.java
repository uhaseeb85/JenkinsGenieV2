package com.example.cifixer.store;

/**
 * Enumeration of notification types sent to stakeholders.
 */
public enum NotificationType {
    /**
     * Notification sent when a build fix is successful and PR is created
     */
    SUCCESS,
    
    /**
     * Notification sent when the automated fix process fails
     */
    FAILURE,
    
    /**
     * Notification sent when manual intervention is required
     */
    MANUAL_INTERVENTION
}