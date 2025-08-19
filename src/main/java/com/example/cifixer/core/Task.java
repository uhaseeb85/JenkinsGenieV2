package com.example.cifixer.core;

import com.example.cifixer.store.Build;
import com.vladmihalcea.hibernate.type.json.JsonBinaryType;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * JPA entity representing a task in the CI fixer system.
 */
@Entity
@Table(name = "tasks")
@TypeDef(name = "jsonb", typeClass = JsonBinaryType.class)
public class Task {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "build_id", nullable = false)
    private Build build;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskType type;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskStatus status;
    
    @Column(nullable = false)
    private Integer attempt;
    
    @Column(name = "max_attempts", nullable = false)
    private Integer maxAttempts;
    
    @Type(type = "jsonb")
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> payload;
    
    @Column(name = "error_message")
    private String errorMessage;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    public Task() {
        this.status = TaskStatus.PENDING;
        this.attempt = 0;
        this.maxAttempts = 3;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
    
    public Task(Build build, TaskType type) {
        this();
        this.build = build;
        this.type = type;
    }
    
    public Task(Build build, TaskType type, Map<String, Object> payload) {
        this(build, type);
        this.payload = payload;
    }
    
    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public Build getBuild() {
        return build;
    }
    
    public void setBuild(Build build) {
        this.build = build;
    }
    
    public TaskType getType() {
        return type;
    }
    
    public void setType(TaskType type) {
        this.type = type;
    }
    
    public TaskStatus getStatus() {
        return status;
    }
    
    public void setStatus(TaskStatus status) {
        this.status = status;
        this.updatedAt = LocalDateTime.now();
    }
    
    public Integer getAttempt() {
        return attempt;
    }
    
    public void setAttempt(Integer attempt) {
        this.attempt = attempt;
    }
    
    public Integer getMaxAttempts() {
        return maxAttempts;
    }
    
    public void setMaxAttempts(Integer maxAttempts) {
        this.maxAttempts = maxAttempts;
    }
    
    public Map<String, Object> getPayload() {
        return payload;
    }
    
    public void setPayload(Map<String, Object> payload) {
        this.payload = payload;
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
    
    @Override
    public String toString() {
        return "Task{" +
                "id=" + id +
                ", buildId=" + (build != null ? build.getId() : null) +
                ", type=" + type +
                ", status=" + status +
                ", attempt=" + attempt +
                ", maxAttempts=" + maxAttempts +
                '}';
    }
}