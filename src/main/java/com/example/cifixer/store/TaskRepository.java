package com.example.cifixer.store;

import com.example.cifixer.core.Task;
import com.example.cifixer.core.TaskStatus;
import com.example.cifixer.core.TaskType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import javax.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data repository for Task entities with task queue operations.
 */
@Repository
public interface TaskRepository extends JpaRepository<Task, Long> {
    
    /**
     * Find tasks by build ID.
     */
    List<Task> findByBuildId(Long buildId);
    
    /**
     * Find tasks by build ID ordered by creation date.
     */
    List<Task> findByBuildIdOrderByCreatedAtAsc(Long buildId);
    
    /**
     * Find tasks by status.
     */
    List<Task> findByStatus(TaskStatus status);
    
    /**
     * Find tasks by status and type.
     */
    List<Task> findByStatusAndType(TaskStatus status, TaskType type);
    
    /**
     * Find tasks by type ordered by creation date.
     */
    List<Task> findByTypeOrderByCreatedAtAsc(TaskType type);
    
    /**
     * Find pending tasks for processing with pessimistic locking.
     * Uses SELECT FOR UPDATE SKIP LOCKED pattern for concurrent processing.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(value = "SELECT t FROM Task t WHERE t.status = :status ORDER BY t.createdAt ASC")
    List<Task> findPendingTasksForUpdate(@Param("status") TaskStatus status);
    
    /**
     * Find next pending task of specific type with locking.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Task t WHERE t.status = :status AND t.type = :type ORDER BY t.createdAt ASC")
    Optional<Task> findNextPendingTaskByType(@Param("status") TaskStatus status, @Param("type") TaskType type);
    
    /**
     * Find next pending or retry task of specific type with locking for queue processing.
     * Uses SKIP LOCKED to avoid blocking when multiple workers are processing.
     */
    @Query(value = "SELECT * FROM tasks WHERE (status = 'PENDING' OR status = 'RETRY') AND type = :type ORDER BY created_at ASC LIMIT 1 FOR UPDATE SKIP LOCKED", nativeQuery = true)
    Optional<Task> findNextPendingTaskForUpdate(@Param("type") String type);
    
    /**
     * Find failed tasks that can be retried.
     */
    @Query("SELECT t FROM Task t WHERE t.status = :status AND t.attempt < t.maxAttempts")
    List<Task> findRetryableTasks(@Param("status") TaskStatus status);
    
    /**
     * Find tasks that have exceeded max attempts.
     */
    @Query("SELECT t FROM Task t WHERE t.status IN (:statuses) AND t.attempt >= t.maxAttempts")
    List<Task> findFailedTasks(@Param("statuses") List<TaskStatus> statuses);
    
    /**
     * Count tasks by status.
     */
    long countByStatus(TaskStatus status);
    
    /**
     * Count tasks by build and status.
     */
    long countByBuildIdAndStatus(Long buildId, TaskStatus status);
    
    /**
     * Update task status and increment attempt count.
     */
    @Modifying
    @Query("UPDATE Task t SET t.status = :status, t.attempt = t.attempt + 1, t.updatedAt = :updatedAt WHERE t.id = :taskId")
    int updateTaskStatusAndIncrementAttempt(@Param("taskId") Long taskId, @Param("status") TaskStatus status, @Param("updatedAt") LocalDateTime updatedAt);
    
    /**
     * Update task status and error message.
     */
    @Modifying
    @Query("UPDATE Task t SET t.status = :status, t.errorMessage = :errorMessage, t.updatedAt = :updatedAt WHERE t.id = :taskId")
    int updateTaskStatusAndError(@Param("taskId") Long taskId, @Param("status") TaskStatus status, @Param("errorMessage") String errorMessage, @Param("updatedAt") LocalDateTime updatedAt);
    
    /**
     * Find tasks created before a specific date for cleanup.
     */
    List<Task> findByCreatedAtBefore(LocalDateTime cutoffDate);
    
    /**
     * Find tasks by build and type.
     */
    Optional<Task> findByBuildIdAndType(Long buildId, TaskType type);
    
    /**
     * Check if there are any pending or in-progress tasks for a build.
     */
    @Query("SELECT COUNT(t) > 0 FROM Task t WHERE t.build.id = :buildId AND t.status IN (:statuses)")
    boolean hasActiveTasks(@Param("buildId") Long buildId, @Param("statuses") List<TaskStatus> statuses);
    
    /**
     * Find tasks by status with pagination.
     */
    Page<Task> findByStatus(TaskStatus status, Pageable pageable);
    
    /**
     * Find tasks by build ID and status.
     */
    List<Task> findByBuildIdAndStatus(Long buildId, TaskStatus status);
    
    /**
     * Count tasks by type and status for statistics.
     */
    @Query("SELECT t.type, t.status, COUNT(t) FROM Task t GROUP BY t.type, t.status")
    List<Object[]> countTasksByTypeAndStatus();
    
    /**
     * Find recent task processing times for performance monitoring.
     */
    @Query("SELECT t.type, AVG(EXTRACT(EPOCH FROM (t.updatedAt - t.createdAt))/60), COUNT(t) " +
           "FROM Task t WHERE t.status = 'COMPLETED' AND t.updatedAt > :since " +
           "GROUP BY t.type")
    List<Object[]> findRecentTaskProcessingTimes(@Param("since") LocalDateTime since, Pageable pageable);
    
    /**
     * Find recent task processing times with default time range.
     */
    default List<Object[]> findRecentTaskProcessingTimes(Pageable pageable) {
        return findRecentTaskProcessingTimes(LocalDateTime.now().minusHours(24), pageable);
    }
}