package com.example.cifixer.store;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * JPA entity representing a GitHub pull request created for a build fix.
 */
@Entity
@Table(name = "pull_requests")
public class PullRequest {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "build_id", nullable = false)
    private Build build;
    
    @Column(name = "pr_number")
    private Integer prNumber;
    
    @Column(name = "pr_url")
    private String prUrl;
    
    @Column(name = "branch_name", nullable = false)
    private String branchName;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PullRequestStatus status = PullRequestStatus.CREATED;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    public PullRequest() {
        this.createdAt = LocalDateTime.now();
    }
    
    public PullRequest(Build build, String branchName) {
        this();
        this.build = build;
        this.branchName = branchName;
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
    
    public Integer getPrNumber() {
        return prNumber;
    }
    
    public void setPrNumber(Integer prNumber) {
        this.prNumber = prNumber;
    }
    
    public String getPrUrl() {
        return prUrl;
    }
    
    public void setPrUrl(String prUrl) {
        this.prUrl = prUrl;
    }
    
    public String getBranchName() {
        return branchName;
    }
    
    public void setBranchName(String branchName) {
        this.branchName = branchName;
    }
    
    public PullRequestStatus getStatus() {
        return status;
    }
    
    public void setStatus(PullRequestStatus status) {
        this.status = status;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    @Override
    public String toString() {
        return "PullRequest{" +
                "id=" + id +
                ", buildId=" + (build != null ? build.getId() : null) +
                ", prNumber=" + prNumber +
                ", branchName='" + branchName + '\'' +
                ", status=" + status +
                '}';
    }
}