package com.example.cifixer.store;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * JPA entity representing a validation result from Maven/Gradle build execution.
 */
@Entity
@Table(name = "validations")
public class Validation {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "build_id", nullable = false)
    private Build build;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "validation_type", nullable = false)
    private ValidationType validationType;
    
    @Column(name = "exit_code", nullable = false)
    private Integer exitCode;
    
    @Column(columnDefinition = "TEXT")
    private String stdout;
    
    @Column(columnDefinition = "TEXT")
    private String stderr;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    public Validation() {
        this.createdAt = LocalDateTime.now();
    }
    
    public Validation(Build build, ValidationType validationType, Integer exitCode) {
        this();
        this.build = build;
        this.validationType = validationType;
        this.exitCode = exitCode;
    }
    
    public Validation(Build build, ValidationType validationType, Integer exitCode, String stdout, String stderr) {
        this(build, validationType, exitCode);
        this.stdout = stdout;
        this.stderr = stderr;
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
    
    public ValidationType getValidationType() {
        return validationType;
    }
    
    public void setValidationType(ValidationType validationType) {
        this.validationType = validationType;
    }
    
    public Integer getExitCode() {
        return exitCode;
    }
    
    public void setExitCode(Integer exitCode) {
        this.exitCode = exitCode;
    }
    
    public String getStdout() {
        return stdout;
    }
    
    public void setStdout(String stdout) {
        this.stdout = stdout;
    }
    
    public String getStderr() {
        return stderr;
    }
    
    public void setStderr(String stderr) {
        this.stderr = stderr;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    /**
     * Check if this validation was successful (exit code 0).
     */
    public boolean isSuccessful() {
        return exitCode != null && exitCode == 0;
    }
    
    @Override
    public String toString() {
        return "Validation{" +
                "id=" + id +
                ", buildId=" + (build != null ? build.getId() : null) +
                ", validationType=" + validationType +
                ", exitCode=" + exitCode +
                ", successful=" + isSuccessful() +
                '}';
    }
}