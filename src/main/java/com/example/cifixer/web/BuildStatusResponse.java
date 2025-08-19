package com.example.cifixer.web;

import com.example.cifixer.store.BuildStatus;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Response object for build status queries.
 */
public class BuildStatusResponse {
    
    private Long buildId;
    private String job;
    private Integer buildNumber;
    private String branch;
    private String commitSha;
    private BuildStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<TaskStatusInfo> tasks;
    
    public BuildStatusResponse() {
    }
    
    public Long getBuildId() {
        return buildId;
    }
    
    public void setBuildId(Long buildId) {
        this.buildId = buildId;
    }
    
    public String getJob() {
        return job;
    }
    
    public void setJob(String job) {
        this.job = job;
    }
    
    public Integer getBuildNumber() {
        return buildNumber;
    }
    
    public void setBuildNumber(Integer buildNumber) {
        this.buildNumber = buildNumber;
    }
    
    public String getBranch() {
        return branch;
    }
    
    public void setBranch(String branch) {
        this.branch = branch;
    }
    
    public String getCommitSha() {
        return commitSha;
    }
    
    public void setCommitSha(String commitSha) {
        this.commitSha = commitSha;
    }
    
    public BuildStatus getStatus() {
        return status;
    }
    
    public void setStatus(BuildStatus status) {
        this.status = status;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
    
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
    
    public List<TaskStatusInfo> getTasks() {
        return tasks;
    }
    
    public void setTasks(List<TaskStatusInfo> tasks) {
        this.tasks = tasks;
    }
    
    public static class TaskStatusInfo {
        private Long taskId;
        private String type;
        private String status;
        private Integer attempt;
        private String errorMessage;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        
        public TaskStatusInfo() {
        }
        
        public Long getTaskId() {
            return taskId;
        }
        
        public void setTaskId(Long taskId) {
            this.taskId = taskId;
        }
        
        public String getType() {
            return type;
        }
        
        public void setType(String type) {
            this.type = type;
        }
        
        public String getStatus() {
            return status;
        }
        
        public void setStatus(String status) {
            this.status = status;
        }
        
        public Integer getAttempt() {
            return attempt;
        }
        
        public void setAttempt(Integer attempt) {
            this.attempt = attempt;
        }
        
        public String getErrorMessage() {
            return errorMessage;
        }
        
        public void setErrorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
        }
        
        public LocalDateTime getCreatedAt() {
            return createdAt;
        }
        
        public void setCreatedAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
        }
        
        public LocalDateTime getUpdatedAt() {
            return updatedAt;
        }
        
        public void setUpdatedAt(LocalDateTime updatedAt) {
            this.updatedAt = updatedAt;
        }
    }
}