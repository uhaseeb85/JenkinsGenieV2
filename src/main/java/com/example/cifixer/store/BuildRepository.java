package com.example.cifixer.store;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data repository for Build entities.
 */
@Repository
public interface BuildRepository extends JpaRepository<Build, Long> {
    
    /**
     * Find build by job name and build number.
     */
    Optional<Build> findByJobAndBuildNumber(String job, Integer buildNumber);
    
    /**
     * Find builds by status.
     */
    List<Build> findByStatus(BuildStatus status);
    
    /**
     * Find builds by status ordered by creation date.
     */
    List<Build> findByStatusOrderByCreatedAtAsc(BuildStatus status);
    
    /**
     * Find builds created after a specific date.
     */
    List<Build> findByCreatedAtAfter(LocalDateTime createdAt);
    
    /**
     * Find builds by repository URL.
     */
    List<Build> findByRepoUrl(String repoUrl);
    
    /**
     * Find builds by commit SHA.
     */
    Optional<Build> findByCommitSha(String commitSha);
    
    /**
     * Count builds by status.
     */
    long countByStatus(BuildStatus status);
    
    /**
     * Find builds that are currently being processed (not completed or failed).
     */
    @Query("SELECT b FROM Build b WHERE b.status = :status ORDER BY b.createdAt ASC")
    List<Build> findProcessingBuilds(@Param("status") BuildStatus status);
    
    /**
     * Find builds older than specified date for cleanup.
     */
    @Query("SELECT b FROM Build b WHERE b.createdAt < :cutoffDate")
    List<Build> findBuildsOlderThan(@Param("cutoffDate") LocalDateTime cutoffDate);
    
    /**
     * Check if a build exists for the given job and build number.
     */
    boolean existsByJobAndBuildNumber(String job, Integer buildNumber);
    
    /**
     * Find recent builds ordered by creation date descending.
     */
    @Query(value = "SELECT * FROM builds ORDER BY created_at DESC LIMIT :limit", nativeQuery = true)
    List<Build> findRecentBuilds(@Param("limit") int limit);
}