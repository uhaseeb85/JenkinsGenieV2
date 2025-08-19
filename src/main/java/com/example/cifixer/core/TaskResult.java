package com.example.cifixer.core;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents the result of processing a task by an agent.
 */
public class TaskResult {
    
    private TaskStatus status;
    private String message;
    private Map<String, Object> metadata;
    
    public TaskResult() {
        this.metadata = new HashMap<>();
    }
    
    public TaskResult(TaskStatus status, String message) {
        this.status = status;
        this.message = message;
        this.metadata = new HashMap<>();
    }
    
    public TaskResult(TaskStatus status, String message, Map<String, Object> metadata) {
        this.status = status;
        this.message = message;
        this.metadata = metadata != null ? metadata : new HashMap<>();
    }
    
    public static TaskResult success(String message) {
        return new TaskResult(TaskStatus.COMPLETED, message);
    }
    
    public static TaskResult success(String message, Map<String, Object> metadata) {
        return new TaskResult(TaskStatus.COMPLETED, message, metadata);
    }
    
    public static TaskResult failure(String message) {
        return new TaskResult(TaskStatus.FAILED, message);
    }
    
    public static TaskResult failure(String message, Map<String, Object> metadata) {
        return new TaskResult(TaskStatus.FAILED, message, metadata);
    }
    
    public static TaskResult retry(String message) {
        return new TaskResult(TaskStatus.RETRY, message);
    }
    
    // Getters and Setters
    public TaskStatus getStatus() {
        return status;
    }
    
    public void setStatus(TaskStatus status) {
        this.status = status;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public Map<String, Object> getMetadata() {
        return metadata;
    }
    
    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }
    
    public void addMetadata(String key, Object value) {
        this.metadata.put(key, value);
    }
    
    @Override
    public String toString() {
        return "TaskResult{" +
                "status=" + status +
                ", message='" + message + '\'' +
                ", metadata=" + metadata +
                '}';
    }
}