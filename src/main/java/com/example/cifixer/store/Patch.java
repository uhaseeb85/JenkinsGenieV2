package com.example.cifixer.store;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * JPA entity representing a patch generated and applied to fix issues.
 */
@Entity
@Table(name = "patches")
public class Patch {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "build_id", nullable = false)
    private Build build;
    
    @Column(name = "file_path", nullable = false)
    private String filePath;
    
    @Column(name = "unified_diff", nullable = false)
    @Lob
    private String unifiedDiff;
    
    @Column(nullable = false)
    private Boolean applied = false;
    
    @Column(name = "apply_log")
    @Lob
    private String applyLog;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    public Patch() {
        this.createdAt = LocalDateTime.now();
    }
    
    public Patch(Build build, String filePath, String unifiedDiff) {
        this();
        this.build = build;
        this.filePath = filePath;
        this.unifiedDiff = unifiedDiff;
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
    
    public String getFilePath() {
        return filePath;
    }
    
    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }
    
    public String getUnifiedDiff() {
        return unifiedDiff;
    }
    
    public void setUnifiedDiff(String unifiedDiff) {
        this.unifiedDiff = unifiedDiff;
    }
    
    public Boolean getApplied() {
        return applied;
    }
    
    public void setApplied(Boolean applied) {
        this.applied = applied;
    }
    
    public String getApplyLog() {
        return applyLog;
    }
    
    public void setApplyLog(String applyLog) {
        this.applyLog = applyLog;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    @Override
    public String toString() {
        return "Patch{" +
                "id=" + id +
                ", buildId=" + (build != null ? build.getId() : null) +
                ", filePath='" + filePath + '\'' +
                ", applied=" + applied +
                '}';
    }
}