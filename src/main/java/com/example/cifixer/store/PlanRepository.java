package com.example.cifixer.store;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data repository for Plan entities.
 */
@Repository
public interface PlanRepository extends JpaRepository<Plan, Long> {
    
    /**
     * Find plan by build ID.
     */
    Optional<Plan> findByBuildId(Long buildId);
    
    /**
     * Find plans created after a specific date.
     */
    List<Plan> findByCreatedAtAfter(LocalDateTime createdAt);
    
    /**
     * Find plans created before a specific date for cleanup.
     */
    List<Plan> findByCreatedAtBefore(LocalDateTime cutoffDate);
    
    /**
     * Check if a plan exists for the given build.
     */
    boolean existsByBuildId(Long buildId);
    
    /**
     * Find plans by build status.
     */
    @Query("SELECT p FROM Plan p JOIN p.build b WHERE b.status = :status")
    List<Plan> findByBuildStatus(@Param("status") BuildStatus status);
    
    /**
     * Count plans created after a specific date.
     */
    long countByCreatedAtAfter(LocalDateTime createdAt);
}