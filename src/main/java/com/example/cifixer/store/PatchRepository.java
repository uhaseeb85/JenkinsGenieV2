package com.example.cifixer.store;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository interface for Patch entities.
 */
@Repository
public interface PatchRepository extends JpaRepository<Patch, Long> {
    
    /**
     * Find all patches for a build.
     *
     * @param buildId The build ID
     * @return List of patches for the build
     */
    @Query("SELECT p FROM Patch p WHERE p.build.id = :buildId ORDER BY p.createdAt")
    List<Patch> findByBuildId(@Param("buildId") Long buildId);
    
    /**
     * Find all applied patches for a build.
     *
     * @param buildId The build ID
     * @return List of applied patches for the build
     */
    @Query("SELECT p FROM Patch p WHERE p.build.id = :buildId AND p.applied = true ORDER BY p.createdAt")
    List<Patch> findAppliedByBuildId(@Param("buildId") Long buildId);
    
    /**
     * Count the number of applied patches for a build.
     *
     * @param buildId The build ID
     * @return Number of applied patches
     */
    @Query("SELECT COUNT(p) FROM Patch p WHERE p.build.id = :buildId AND p.applied = true")
    long countAppliedByBuildId(@Param("buildId") Long buildId);
}