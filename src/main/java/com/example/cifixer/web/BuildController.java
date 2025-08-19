package com.example.cifixer.web;

import com.example.cifixer.core.Orchestrator;
import com.example.cifixer.core.Task;
import com.example.cifixer.core.TaskQueue;
import com.example.cifixer.store.Build;
import com.example.cifixer.store.BuildRepository;
import com.example.cifixer.store.TaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * REST controller for monitoring build status and managing tasks.
 */
@RestController
@RequestMapping("/builds")
public class BuildController {
    
    private static final Logger logger = LoggerFactory.getLogger(BuildController.class);
    
    private final BuildRepository buildRepository;
    private final TaskRepository taskRepository;
    private final TaskQueue taskQueue;
    private final Orchestrator orchestrator;
    
    public BuildController(BuildRepository buildRepository, 
                          TaskRepository taskRepository,
                          TaskQueue taskQueue,
                          Orchestrator orchestrator) {
        this.buildRepository = buildRepository;
        this.taskRepository = taskRepository;
        this.taskQueue = taskQueue;
        this.orchestrator = orchestrator;
    }
    
    /**
     * Gets the status of a specific build and its tasks.
     *
     * @param id The build ID
     * @return Build status with task information
     */
    @GetMapping("/{id}")
    public ResponseEntity<BuildStatusResponse> getBuildStatus(@PathVariable Long id) {
        logger.debug("Getting build status: id={}", id);
        
        Optional<Build> buildOpt = buildRepository.findById(id);
        if (!buildOpt.isPresent()) {
            logger.warn("Build not found: id={}", id);
            return ResponseEntity.notFound().build();
        }
        
        Build build = buildOpt.get();
        List<Task> tasks = taskRepository.findByBuildIdOrderByCreatedAtAsc(build.getId());
        
        BuildStatusResponse response = new BuildStatusResponse();
        response.setBuildId(build.getId());
        response.setJob(build.getJob());
        response.setBuildNumber(build.getBuildNumber());
        response.setBranch(build.getBranch());
        response.setCommitSha(build.getCommitSha());
        response.setStatus(build.getStatus());
        response.setCreatedAt(build.getCreatedAt());
        response.setUpdatedAt(build.getUpdatedAt());
        
        // Convert tasks to response format
        List<BuildStatusResponse.TaskStatusInfo> taskInfos = tasks.stream()
            .map(this::convertTaskToStatusInfo)
            .collect(Collectors.toList());
        response.setTasks(taskInfos);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Lists recent builds with their status.
     *
     * @param limit Maximum number of builds to return (default 20)
     * @return List of recent builds
     */
    @GetMapping
    public ResponseEntity<List<BuildStatusResponse>> listRecentBuilds(
            @RequestParam(defaultValue = "20") int limit) {
        
        logger.debug("Listing recent builds: limit={}", limit);
        
        List<Build> builds = buildRepository.findRecentBuilds(limit);
        
        List<BuildStatusResponse> responses = builds.stream()
            .map(this::convertBuildToStatusResponse)
            .collect(Collectors.toList());
        
        return ResponseEntity.ok(responses);
    }
    
    /**
     * Gets system status including active task counts.
     *
     * @return System status information
     */
    @GetMapping("/system/status")
    public ResponseEntity<Map<String, Object>> getSystemStatus() {
        logger.debug("Getting system status");
        
        Map<String, Object> status = new HashMap<>();
        status.put("processingEnabled", orchestrator.isProcessingEnabled());
        status.put("activeTaskCounts", orchestrator.getActiveTaskCounts());
        
        // Add task queue statistics
        Map<String, Long> queueStats = new HashMap<>();
        queueStats.put("pendingTasks", taskRepository.countByStatus(com.example.cifixer.core.TaskStatus.PENDING));
        queueStats.put("inProgressTasks", taskRepository.countByStatus(com.example.cifixer.core.TaskStatus.IN_PROGRESS));
        queueStats.put("completedTasks", taskRepository.countByStatus(com.example.cifixer.core.TaskStatus.COMPLETED));
        queueStats.put("failedTasks", taskRepository.countByStatus(com.example.cifixer.core.TaskStatus.FAILED));
        status.put("queueStatistics", queueStats);
        
        // Add build statistics
        Map<String, Long> buildStats = new HashMap<>();
        buildStats.put("processingBuilds", buildRepository.countByStatus(com.example.cifixer.store.BuildStatus.PROCESSING));
        buildStats.put("completedBuilds", buildRepository.countByStatus(com.example.cifixer.store.BuildStatus.COMPLETED));
        buildStats.put("failedBuilds", buildRepository.countByStatus(com.example.cifixer.store.BuildStatus.FAILED));
        status.put("buildStatistics", buildStats);
        
        return ResponseEntity.ok(status);
    }
    
    /**
     * Admin endpoint to manually retry a failed task.
     *
     * @param taskId The ID of the task to retry
     * @return Success or error response
     */
    @PostMapping("/admin/retry/{taskId}")
    public ResponseEntity<Map<String, String>> retryTask(@PathVariable Long taskId) {
        logger.info("Manual task retry requested: taskId={}", taskId);
        
        Optional<Task> taskOpt = taskRepository.findById(taskId);
        if (!taskOpt.isPresent()) {
            logger.warn("Task not found for retry: taskId={}", taskId);
            return ResponseEntity.notFound().build();
        }
        
        Task task = taskOpt.get();
        
        // Reset task for retry
        task.setStatus(com.example.cifixer.core.TaskStatus.PENDING);
        task.setAttempt(0);
        task.setErrorMessage(null);
        taskRepository.save(task);
        
        logger.info("Task reset for retry: taskId={}", taskId);
        
        Map<String, String> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Task queued for retry");
        return ResponseEntity.ok(response);
    }
    
    /**
     * Admin endpoint to enable/disable task processing.
     *
     * @param enabled Whether processing should be enabled
     * @return Success response
     */
    @PostMapping("/admin/processing")
    public ResponseEntity<Map<String, String>> setProcessingEnabled(
            @RequestParam boolean enabled) {
        
        logger.info("Setting task processing enabled: {}", enabled);
        orchestrator.setProcessingEnabled(enabled);
        
        Map<String, String> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Processing " + (enabled ? "enabled" : "disabled"));
        return ResponseEntity.ok(response);
    }
    
    /**
     * Converts a Build entity to BuildStatusResponse.
     */
    private BuildStatusResponse convertBuildToStatusResponse(Build build) {
        BuildStatusResponse response = new BuildStatusResponse();
        response.setBuildId(build.getId());
        response.setJob(build.getJob());
        response.setBuildNumber(build.getBuildNumber());
        response.setBranch(build.getBranch());
        response.setCommitSha(build.getCommitSha());
        response.setStatus(build.getStatus());
        response.setCreatedAt(build.getCreatedAt());
        response.setUpdatedAt(build.getUpdatedAt());
        
        // Get tasks for this build
        List<Task> tasks = taskRepository.findByBuildIdOrderByCreatedAtAsc(build.getId());
        List<BuildStatusResponse.TaskStatusInfo> taskInfos = tasks.stream()
            .map(this::convertTaskToStatusInfo)
            .collect(Collectors.toList());
        response.setTasks(taskInfos);
        
        return response;
    }
    
    /**
     * Converts a Task entity to TaskStatusInfo.
     */
    private BuildStatusResponse.TaskStatusInfo convertTaskToStatusInfo(Task task) {
        BuildStatusResponse.TaskStatusInfo info = new BuildStatusResponse.TaskStatusInfo();
        info.setTaskId(task.getId());
        info.setType(task.getType().name());
        info.setStatus(task.getStatus().name());
        info.setAttempt(task.getAttempt());
        info.setErrorMessage(task.getErrorMessage());
        info.setCreatedAt(task.getCreatedAt());
        info.setUpdatedAt(task.getUpdatedAt());
        return info;
    }
}