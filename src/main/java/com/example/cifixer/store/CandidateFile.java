package com.example.cifixer.store;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * JPA entity representing a candidate file identified for potential fixes.
 */
@Entity
@Table(name = "candidate_files")
public class CandidateFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "build_id", nullable = false)
    private Build build;

    @Column(name = "file_path", nullable = false)
    private String filePath;

    @Column(name = "rank_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal rankScore;

    @Column(nullable = false)
    private String reason;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public CandidateFile() {
        this.createdAt = LocalDateTime.now();
    }

    public CandidateFile(Build build, String filePath, BigDecimal rankScore, String reason) {
        this();
        this.build = build;
        this.filePath = filePath;
        this.rankScore = rankScore;
        this.reason = reason;
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

    public BigDecimal getRankScore() {
        return rankScore;
    }

    public void setRankScore(BigDecimal rankScore) {
        this.rankScore = rankScore;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return "CandidateFile{" +
                "id=" + id +
                ", buildId=" + (build != null ? build.getId() : null) +
                ", filePath='" + filePath + '\'' +
                ", rankScore=" + rankScore +
                ", reason='" + reason + '\'' +
                '}';
    }
}