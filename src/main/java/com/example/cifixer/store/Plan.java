package com.example.cifixer.store;

import com.vladmihalcea.hibernate.type.json.JsonBinaryType;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * JPA entity representing a structured plan for fixing build failures.
 */
@Entity
@Table(name = "plans")
@TypeDef(name = "jsonb", typeClass = JsonBinaryType.class)
public class Plan {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "build_id", nullable = false, unique = true)
    private Build build;
    
    @Type(type = "jsonb")
    @Column(name = "plan_json", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> planJson;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    public Plan() {
        this.createdAt = LocalDateTime.now();
    }
    
    public Plan(Build build, Map<String, Object> planJson) {
        this();
        this.build = build;
        this.planJson = planJson;
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
    
    public Map<String, Object> getPlanJson() {
        return planJson;
    }
    
    public void setPlanJson(Map<String, Object> planJson) {
        this.planJson = planJson;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    @Override
    public String toString() {
        return "Plan{" +
                "id=" + id +
                ", buildId=" + (build != null ? build.getId() : null) +
                ", createdAt=" + createdAt +
                '}';
    }
}