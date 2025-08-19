package com.example.cifixer.store;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository interface for PullRequest entities.
 */
@Repository
public interface PullRequestRepository extends JpaRepository<PullRequest, Long> {
    
    /**
     * Find a pull request by build ID.
     *
     * @param buildId The build ID
     * @return Optional containing the pull request if found
     */
    @Query("SELECT pr FROM PullRequest pr WHERE pr.build.id = :buildId")
    Optional<PullRequest> findByBuildId(@Param("buildId") Long buildId);
    
    /**
     * Check if a pull request exists for the given build.
     *
     * @param buildId The build ID
     * @return true if a pull request exists, false otherwise
     */
    @Query("SELECT COUNT(pr) > 0 FROM PullRequest pr WHERE pr.build.id = :buildId")
    boolean existsByBuildId(@Param("buildId") Long buildId);
}