package com.example.cifixer.store;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository interface for Validation entities.
 */
@Repository
public interface ValidationRepository extends JpaRepository<Validation, Long> {
    
    /**
     * Find all validations for a build, ordered by creation time descending.
     *
     * @param buildId The build ID
     * @return List of validations for the build
     */
    @Query("SELECT v FROM Validation v WHERE v.build.id = :buildId ORDER BY v.createdAt DESC")
    List<Validation> findByBuildIdOrderByCreatedAtDesc(@Param("buildId") Long buildId);
    
    /**
     * Find the latest validation of a specific type for a build.
     *
     * @param buildId The build ID
     * @param validationType The validation type
     * @return List containing the latest validation of the specified type
     */
    @Query("SELECT v FROM Validation v WHERE v.build.id = :buildId AND v.validationType = :validationType ORDER BY v.createdAt DESC")
    List<Validation> findLatestByBuildIdAndType(@Param("buildId") Long buildId, @Param("validationType") String validationType);
}