package com.example.cifixer.web;

import com.example.cifixer.core.TaskQueueService;
import com.example.cifixer.monitoring.DatabaseMetrics;
import com.example.cifixer.monitoring.StructuredLogger;
import com.example.cifixer.store.Build;
import com.example.cifixer.core.Task;
import com.example.cifixer.core.TaskStatus;
import com.example.cifixer.store.BuildRepository;
import com.example.cifixer.store.TaskRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Admin endpoints for system monitoring and manual operations.
 */
@RestController
@RequestMapping("/admin")
public class AdminController {

    private final TaskRepository taskRepository;
    private final BuildRepository buildRepository;
    private final TaskQueueService taskQueueService;
    private final DatabaseMetrics databaseMetrics;
    private final StructuredLogger structuredLogger;

    public AdminController(TaskRepository taskRepository,
                          BuildRepository buildRepository,
                          TaskQueueService taskQueueService,
                          DatabaseMetrics databaseMetrics,
                          StructuredLogger structuredLogger) {
        this.taskRepository = taskRepository;
        this.buildRepository = buildRepository;
        this.taskQueueService = taskQueueService;
        this.databaseMetrics = databaseMetrics;
        this.structuredLogger = structuredLogger;
    }

    /**
     * Get system status overview
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getSystemStatus() {
        Map<String, Object> status = new HashMap<>();
        
        // Task queue statistics
        Map<String, Object> taskStats = new HashMap<>();
        taskStats.put("pending", taskRepository.countByStatus(TaskStatus.PENDING));
        taskStats.put("processing", taskRepository.countByStatus(TaskStatus.IN_PROGRESS));
        taskStats.put("completed", taskRepository.countByStatus(TaskStatus.COMPLETED));
        taskStats.put("failed", taskRepository.countByStatus(TaskStatus.FAILED));
        status.put("tasks", taskStats);
        
        // Build statistics
        Map<String, Object> buildStats = new HashMap<>();
        buildStats.put("total", buildRepository.count());
        buildStats.put("processing", buildRepository.countByStatus("PROCESSING"));
        buildStats.put("completed", buildRepository.countByStatus("COMPLETED"));
        buildStats.put("failed", buildRepository.countByStatus("FAILED"));
        status.put("builds", buildStats);
        
        // Database connection pool status
        Map<String, Object> dbStats = new HashMap<>();
        dbStats.put("activeConnections", databaseMetrics.getActiveConnections());
        dbStats.put("idleConnections", databaseMetrics.getIdleConnections());
        dbStats.put("totalConnections", databaseMetrics.getTotalConnections());
        dbStats.put("threadsAwaitingConnection", databaseMetrics.getThreadsAwaitingConnection());
        status.put("database", dbStats);
        
        // System information
        Map<String, Object> systemInfo = new HashMap<>();
        Runtime runtime = Runtime.getRuntime();
        systemInfo.put("totalMemoryMB", runtime.totalMemory() / 1024 / 1024);
        systemInfo.put("freeMemoryMB", runtime.freeMemory() / 1024 / 1024);
        systemInfo.put("maxMemoryMB", runtime.maxMemory() / 1024 / 1024);
        systemInfo.put("availableProcessors", runtime.availableProcessors());
        status.put("system", systemInfo);
        
        status.put("timestamp", LocalDateTime.now());
        
        return ResponseEntity.ok(status);
    }

    /**
     * Get recent tasks with pagination
     */
    @GetMapping("/tasks")
    public ResponseEntity<Page<Task>> getTasks(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) TaskStatus status) {
        
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        
        Page<Task> tasks;
        if (status != null) {
            tasks = taskRepository.findByStatus(status, pageable);
        } else {
            tasks = taskRepository.findAll(pageable);
        }
        
        return ResponseEntity.ok(tasks);
    }

    /**
     * Get task details by ID
     */
    @GetMapping("/tasks/{taskId}")
    public ResponseEntity<Task> getTask(@PathVariable Long taskId) {
        Optional<Task> task = taskRepository.findById(taskId);
        return task.map(ResponseEntity::ok)
                  .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Retry a failed task
     */
    @PostMapping("/tasks/{taskId}/retry")
    public ResponseEntity<Map<String, Object>> retryTask(@PathVariable Long taskId) {
        Optional<Task> taskOpt = taskRepository.findById(taskId);
        
        if (!taskOpt.isPresent()) {
            return ResponseEntity.notFound().build();
        }
        
        Task task = taskOpt.get();
        
        if (task.getStatus() != TaskStatus.FAILED) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Task is not in FAILED status");
            error.put("currentStatus", task.getStatus());
            return ResponseEntity.badRequest().body(error);
        }
        
        // Reset task for retry
        task.setStatus(TaskStatus.PENDING);
        task.setAttempt(0);
        task.setErrorMessage(null);
        task.setUpdatedAt(LocalDateTime.now());
        taskRepository.save(task);
        
        structuredLogger.info("Task manually retried by admin", 
                             "taskId", taskId, 
                             "taskType", task.getType().name());
        
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Task queued for retry");
        response.put("taskId", taskId);
        response.put("status", "PENDING");
        
        return ResponseEntity.ok(response);
    }

    /**
     * Get recent builds with pagination
     */
    @GetMapping("/builds")
    public ResponseEntity<Page<Build>> getBuilds(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status) {
        
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        
        Page<Build> builds;
        if (status != null && !status.trim().isEmpty()) {
            builds = buildRepository.findByStatus(status, pageable);
        } else {
            builds = buildRepository.findAll(pageable);
        }
        
        return ResponseEntity.ok(builds);
    }

    /**
     * Get build details by ID
     */
    @GetMapping("/builds/{buildId}")
    public ResponseEntity<Build> getBuild(@PathVariable Long buildId) {
        Optional<Build> build = buildRepository.findById(buildId);
        return build.map(ResponseEntity::ok)
                   .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get tasks for a specific build
     */
    @GetMapping("/builds/{buildId}/tasks")
    public ResponseEntity<List<Task>> getBuildTasks(@PathVariable Long buildId) {
        Optional<Build> build = buildRepository.findById(buildId);
        
        if (!build.isPresent()) {
            return ResponseEntity.notFound().build();
        }
        
        List<Task> tasks = taskRepository.findByBuildIdOrderByCreatedAtAsc(buildId);
        return ResponseEntity.ok(tasks);
    }

    /**
     * Retry all failed tasks for a build
     */
    @PostMapping("/builds/{buildId}/retry")
    public ResponseEntity<Map<String, Object>> retryBuildTasks(@PathVariable Long buildId) {
        Optional<Build> buildOpt = buildRepository.findById(buildId);
        
        if (!buildOpt.isPresent()) {
            return ResponseEntity.notFound().build();
        }
        
        List<Task> failedTasks = taskRepository.findByBuildIdAndStatus(buildId, TaskStatus.FAILED);
        
        if (failedTasks.isEmpty()) {
            Map<String, Object> response = new HashMap<>();
            response.put("message", "No failed tasks found for this build");
            response.put("buildId", buildId);
            return ResponseEntity.ok(response);
        }
        
        // Reset all failed tasks
        for (Task task : failedTasks) {
            task.setStatus(TaskStatus.PENDING);
            task.setAttempt(0);
            task.setErrorMessage(null);
            task.setUpdatedAt(LocalDateTime.now());
        }
        taskRepository.saveAll(failedTasks);
        
        structuredLogger.info("Build tasks manually retried by admin", 
                             "buildId", buildId, 
                             "retriedTaskCount", failedTasks.size());
        
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Failed tasks queued for retry");
        response.put("buildId", buildId);
        response.put("retriedTaskCount", failedTasks.size());
        
        return ResponseEntity.ok(response);
    }

    /**
     * Get queue statistics
     */
    @GetMapping("/queue/stats")
    public ResponseEntity<Map<String, Object>> getQueueStats() {
        Map<String, Object> stats = new HashMap<>();
        
        // Tasks by type and status
        List<Object[]> tasksByTypeAndStatus = taskRepository.countTasksByTypeAndStatus();
        Map<String, Map<String, Long>> taskTypeStats = new HashMap<>();
        
        for (Object[] row : tasksByTypeAndStatus) {
            String taskType = (String) row[0];
            String status = (String) row[1];
            Long count = (Long) row[2];
            
            taskTypeStats.computeIfAbsent(taskType, k -> new HashMap<>()).put(status, count);
        }
        
        stats.put("tasksByType", taskTypeStats);
        
        // Recent task processing times
        List<Object[]> recentTaskTimes = taskRepository.findRecentTaskProcessingTimes(PageRequest.of(0, 100));
        Map<String, Object> processingTimes = new HashMap<>();
        
        for (Object[] row : recentTaskTimes) {
            String taskType = (String) row[0];
            Double avgDurationMinutes = (Double) row[1];
            Long taskCount = (Long) row[2];
            
            Map<String, Object> typeStats = new HashMap<>();
            typeStats.put("averageDurationMinutes", avgDurationMinutes);
            typeStats.put("taskCount", taskCount);
            
            processingTimes.put(taskType, typeStats);
        }
        
        stats.put("processingTimes", processingTimes);
        stats.put("timestamp", LocalDateTime.now());
        
        return ResponseEntity.ok(stats);
    }

    /**
     * Health check endpoint with detailed system information
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> getDetailedHealth() {
        Map<String, Object> health = new HashMap<>();
        
        try {
            // Database health
            health.put("database", databaseMetrics.health().getDetails());
            
            // Task queue health
            long pendingTasks = taskRepository.countByStatus(TaskStatus.PENDING);
            long processingTasks = taskRepository.countByStatus(TaskStatus.IN_PROGRESS);
            
            Map<String, Object> queueHealth = new HashMap<>();
            queueHealth.put("pendingTasks", pendingTasks);
            queueHealth.put("processingTasks", processingTasks);
            queueHealth.put("status", pendingTasks > 100 ? "DEGRADED" : "UP");
            health.put("taskQueue", queueHealth);
            
            // Overall status
            boolean isHealthy = databaseMetrics.health().getStatus().getCode().equals("UP") &&
                               pendingTasks < 100;
            
            health.put("status", isHealthy ? "UP" : "DOWN");
            health.put("timestamp", LocalDateTime.now());
            
            return ResponseEntity.ok(health);
            
        } catch (Exception e) {
            structuredLogger.error("Health check failed", e);
            
            Map<String, Object> errorHealth = new HashMap<>();
            errorHealth.put("status", "DOWN");
            errorHealth.put("error", e.getMessage());
            errorHealth.put("timestamp", LocalDateTime.now());
            
            return ResponseEntity.status(503).body(errorHealth);
        }
    }
}