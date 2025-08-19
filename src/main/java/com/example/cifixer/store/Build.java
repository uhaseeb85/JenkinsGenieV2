package com.example.cifixer.store;

import com.vladmihalcea.hibernate.type.json.JsonBinaryType;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * JPA entity representing a build that failed and needs to be fixed.
 */
@Entity
@Table(name = "builds")
@TypeDef(name = "jsonb", typeClass = JsonBinaryType.class)
public class Build {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String job;
    
    @Column(name = "build_number", nullable = false)
    private Integer buildNumber;
    
    @Column(nullable = false)
    private String branch;
    
    @Column(name = "repo_url", nullable = false)
    private String repoUrl;
    
    @Column(name = "commit_sha", nullable = false)
    private String commitSha;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BuildStatus status = BuildStatus.PROCESSING;
    
    @Type(type = "jsonb")
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> payload;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    public Build() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
    
    public Build(String job, Integer buildNumber, String branch, String repoUrl, String commitSha) {
        this();
        this.job = job;
        this.buildNumber = buildNumber;
        this.branch = branch;
        this.repoUrl = repoUrl;
        this.commitSha = commitSha;
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
    
    public String getRepoUrl() {
        return repoUrl;
    }
    
    public void setRepoUrl(String repoUrl) {
        this.repoUrl = repoUrl;
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
    
    public Map<String, Object> getPayload() {
        return payload;
    }
    
    public void setPayload(Map<String, Object> payload) {
        this.payload = payload;
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
        return "Build{" +
                "id=" + id +
                ", job='" + job + '\'' +
                ", buildNumber=" + buildNumber +
                ", branch='" + branch + '\'' +
                ", commitSha='" + commitSha + '\'' +
                ", status=" + status +
                '}';
    }
}