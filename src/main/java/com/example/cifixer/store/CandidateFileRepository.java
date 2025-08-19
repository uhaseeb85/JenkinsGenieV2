package com.example.cifixer.store;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Spring Data repository for CandidateFile entities.
 */
@Repository
public interface CandidateFileRepository extends JpaRepository<CandidateFile, Long> {
    
    /**
     * Find candidate files by build ID ordered by rank score descending.
     */
    List<CandidateFile> findByBuildIdOrderByRankScoreDesc(Long buildId);
    
    /**
     * Find candidate files by build ID with minimum rank score.
     */
    @Query("SELECT cf FROM CandidateFile cf WHERE cf.build.id = :buildId AND cf.rankScore >= :minScore ORDER BY cf.rankScore DESC")
    List<CandidateFile> findByBuildIdAndMinScore(@Param("buildId") Long buildId, @Param("minScore") BigDecimal minScore);
    
    /**
     * Find top N candidate files by build ID.
     */
    @Query("SELECT cf FROM CandidateFile cf WHERE cf.build.id = :buildId ORDER BY cf.rankScore DESC")
    List<CandidateFile> findTopCandidatesByBuildId(@Param("buildId") Long buildId);
    
    /**
     * Find candidate files by file path pattern.
     */
    @Query("SELECT cf FROM CandidateFile cf WHERE cf.filePath LIKE :pathPattern ORDER BY cf.rankScore DESC")
    List<CandidateFile> findByFilePathPattern(@Param("pathPattern") String pathPattern);
    
    /**
     * Find candidate files created after a specific date.
     */
    List<CandidateFile> findByCreatedAtAfter(LocalDateTime createdAt);
    
    /**
     * Find candidate files created before a specific date for cleanup.
     */
    List<CandidateFile> findByCreatedAtBefore(LocalDateTime cutoffDate);
    
    /**
     * Count candidate files by build ID.
     */
    long countByBuildId(Long buildId);
    
    /**
     * Find highest ranked candidate file for a build.
     */
    default CandidateFile findTopRankedByBuildId(Long buildId) {
        List<CandidateFile> candidates = findByBuildIdOrderByRankScoreDesc(buildId);
        return candidates.isEmpty() ? null : candidates.get(0);
    }
    
    /**
     * Delete candidate files by build ID.
     */
    void deleteByBuildId(Long buildId);
}