package com.example.cifixer.web;

import com.example.cifixer.core.Task;
import com.example.cifixer.core.TaskQueueService;
import com.example.cifixer.core.TaskStatus;
import com.example.cifixer.core.TaskType;
import com.example.cifixer.monitoring.DatabaseMetrics;
import com.example.cifixer.monitoring.StructuredLogger;
import com.example.cifixer.store.Build;
import com.example.cifixer.store.BuildRepository;
import com.example.cifixer.store.BuildStatus;
import com.example.cifixer.store.TaskRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminController.class)
class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TaskRepository taskRepository;

    @MockBean
    private BuildRepository buildRepository;

    @MockBean
    private TaskQueueService taskQueueService;

    @MockBean
    private DatabaseMetrics databaseMetrics;

    @MockBean
    private StructuredLogger structuredLogger;

    @Test
    void shouldReturnSystemStatus() throws Exception {
        // Given
        when(taskRepository.countByStatus(TaskStatus.PENDING)).thenReturn(10L);
        when(taskRepository.countByStatus(TaskStatus.IN_PROGRESS)).thenReturn(2L);
        when(taskRepository.countByStatus(TaskStatus.COMPLETED)).thenReturn(100L);
        when(taskRepository.countByStatus(TaskStatus.FAILED)).thenReturn(5L);
        
        when(buildRepository.count()).thenReturn(50L);
        when(buildRepository.countByStatus("PROCESSING")).thenReturn(3L);
        when(buildRepository.countByStatus("COMPLETED")).thenReturn(40L);
        when(buildRepository.countByStatus("FAILED")).thenReturn(7L);
        
        when(databaseMetrics.getActiveConnections()).thenReturn(5);
        when(databaseMetrics.getIdleConnections()).thenReturn(3);
        when(databaseMetrics.getTotalConnections()).thenReturn(8);
        when(databaseMetrics.getThreadsAwaitingConnection()).thenReturn(0);

        // When/Then
        mockMvc.perform(get("/admin/status"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.tasks.pending").value(10))
                .andExpect(jsonPath("$.tasks.processing").value(2))
                .andExpect(jsonPath("$.tasks.completed").value(100))
                .andExpect(jsonPath("$.tasks.failed").value(5))
                .andExpect(jsonPath("$.builds.total").value(50))
                .andExpect(jsonPath("$.builds.processing").value(3))
                .andExpect(jsonPath("$.builds.completed").value(40))
                .andExpect(jsonPath("$.builds.failed").value(7))
                .andExpect(jsonPath("$.database.activeConnections").value(5))
                .andExpect(jsonPath("$.database.idleConnections").value(3))
                .andExpect(jsonPath("$.system.availableProcessors").exists())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void shouldReturnTasksWithPagination() throws Exception {
        // Given
        Task task1 = createTestTask(1L, TaskType.PLAN, TaskStatus.COMPLETED);
        Task task2 = createTestTask(2L, TaskType.RETRIEVE, TaskStatus.FAILED);
        
        Page<Task> taskPage = new PageImpl<>(Arrays.asList(task1, task2), PageRequest.of(0, 20), 2);
        when(taskRepository.findAll(any(PageRequest.class))).thenReturn(taskPage);

        // When/Then
        mockMvc.perform(get("/admin/tasks")
                .param("page", "0")
                .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[1].id").value(2));
    }

    @Test
    void shouldReturnTasksByStatus() throws Exception {
        // Given
        Task failedTask = createTestTask(1L, TaskType.PLAN, TaskStatus.FAILED);
        Page<Task> taskPage = new PageImpl<>(Arrays.asList(failedTask), PageRequest.of(0, 20), 1);
        
        when(taskRepository.findByStatus(eq(TaskStatus.FAILED), any(PageRequest.class))).thenReturn(taskPage);

        // When/Then
        mockMvc.perform(get("/admin/tasks")
                .param("status", "FAILED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].status").value("FAILED"));
    }

    @Test
    void shouldReturnTaskById() throws Exception {
        // Given
        Task task = createTestTask(1L, TaskType.PLAN, TaskStatus.COMPLETED);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));

        // When/Then
        mockMvc.perform(get("/admin/tasks/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.type").value("PLAN"))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void shouldReturnNotFoundForNonExistentTask() throws Exception {
        // Given
        when(taskRepository.findById(999L)).thenReturn(Optional.empty());

        // When/Then
        mockMvc.perform(get("/admin/tasks/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldRetryFailedTask() throws Exception {
        // Given
        Task failedTask = createTestTask(1L, TaskType.PLAN, TaskStatus.FAILED);
        failedTask.setErrorMessage("Previous error");
        when(taskRepository.findById(1L)).thenReturn(Optional.of(failedTask));
        when(taskRepository.save(any(Task.class))).thenReturn(failedTask);

        // When/Then
        mockMvc.perform(post("/admin/tasks/1/retry"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Task queued for retry"))
                .andExpect(jsonPath("$.taskId").value(1))
                .andExpect(jsonPath("$.status").value("PENDING"));

        verify(structuredLogger).info(eq("Task manually retried by admin"), 
                                    eq("taskId"), eq(1L), 
                                    eq("taskType"), eq("PLAN"));
    }

    @Test
    void shouldNotRetryNonFailedTask() throws Exception {
        // Given
        Task completedTask = createTestTask(1L, TaskType.PLAN, TaskStatus.COMPLETED);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(completedTask));

        // When/Then
        mockMvc.perform(post("/admin/tasks/1/retry"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Task is not in FAILED status"))
                .andExpect(jsonPath("$.currentStatus").value("COMPLETED"));
    }

    @Test
    void shouldReturnBuildsWithPagination() throws Exception {
        // Given
        Build build1 = createTestBuild(1L, "test-job", BuildStatus.COMPLETED);
        Build build2 = createTestBuild(2L, "another-job", BuildStatus.FAILED);
        
        Page<Build> buildPage = new PageImpl<>(Arrays.asList(build1, build2), PageRequest.of(0, 20), 2);
        when(buildRepository.findAll(any(PageRequest.class))).thenReturn(buildPage);

        // When/Then
        mockMvc.perform(get("/admin/builds"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[1].id").value(2));
    }

    @Test
    void shouldReturnBuildById() throws Exception {
        // Given
        Build build = createTestBuild(1L, "test-job", BuildStatus.COMPLETED);
        when(buildRepository.findById(1L)).thenReturn(Optional.of(build));

        // When/Then
        mockMvc.perform(get("/admin/builds/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.job").value("test-job"))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void shouldReturnBuildTasks() throws Exception {
        // Given
        Build build = createTestBuild(1L, "test-job", BuildStatus.PROCESSING);
        Task task1 = createTestTask(1L, TaskType.PLAN, TaskStatus.COMPLETED);
        Task task2 = createTestTask(2L, TaskType.RETRIEVE, TaskStatus.IN_PROGRESS);
        
        when(buildRepository.findById(1L)).thenReturn(Optional.of(build));
        when(taskRepository.findByBuildIdOrderByCreatedAtAsc(1L)).thenReturn(Arrays.asList(task1, task2));

        // When/Then
        mockMvc.perform(get("/admin/builds/1/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[1].id").value(2));
    }

    @Test
    void shouldRetryBuildTasks() throws Exception {
        // Given
        Build build = createTestBuild(1L, "test-job", BuildStatus.FAILED);
        Task failedTask1 = createTestTask(1L, TaskType.PLAN, TaskStatus.FAILED);
        Task failedTask2 = createTestTask(2L, TaskType.RETRIEVE, TaskStatus.FAILED);
        
        when(buildRepository.findById(1L)).thenReturn(Optional.of(build));
        when(taskRepository.findByBuildIdAndStatus(1L, TaskStatus.FAILED))
                .thenReturn(Arrays.asList(failedTask1, failedTask2));
        when(taskRepository.saveAll(anyList())).thenReturn(Arrays.asList(failedTask1, failedTask2));

        // When/Then
        mockMvc.perform(post("/admin/builds/1/retry"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Failed tasks queued for retry"))
                .andExpect(jsonPath("$.buildId").value(1))
                .andExpect(jsonPath("$.retriedTaskCount").value(2));

        verify(structuredLogger).info(eq("Build tasks manually retried by admin"), 
                                    eq("buildId"), eq(1L), 
                                    eq("retriedTaskCount"), eq(2));
    }

    @Test
    void shouldReturnQueueStats() throws Exception {
        // Given
        when(taskRepository.countTasksByTypeAndStatus()).thenReturn(Arrays.asList(
                new Object[]{"PLAN", "COMPLETED", 10L},
                new Object[]{"PLAN", "FAILED", 2L},
                new Object[]{"RETRIEVE", "COMPLETED", 8L}
        ));
        
        when(taskRepository.findRecentTaskProcessingTimes(any(PageRequest.class))).thenReturn(Arrays.asList(
                new Object[]{"PLAN", 2.5, 10L},
                new Object[]{"RETRIEVE", 1.8, 8L}
        ));

        // When/Then
        mockMvc.perform(get("/admin/queue/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tasksByType.PLAN.COMPLETED").value(10))
                .andExpect(jsonPath("$.tasksByType.PLAN.FAILED").value(2))
                .andExpect(jsonPath("$.processingTimes.PLAN.averageDurationMinutes").value(2.5))
                .andExpect(jsonPath("$.processingTimes.PLAN.taskCount").value(10));
    }

    @Test
    void shouldReturnDetailedHealth() throws Exception {
        // Given
        Health dbHealth = Health.up()
                .withDetail("activeConnections", 5)
                .withDetail("totalConnections", 10)
                .build();
        
        when(databaseMetrics.health()).thenReturn(dbHealth);
        when(taskRepository.countByStatus(TaskStatus.PENDING)).thenReturn(25L);
        when(taskRepository.countByStatus(TaskStatus.IN_PROGRESS)).thenReturn(3L);

        // When/Then
        mockMvc.perform(get("/admin/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.database.activeConnections").value(5))
                .andExpect(jsonPath("$.taskQueue.pendingTasks").value(25))
                .andExpect(jsonPath("$.taskQueue.status").value("UP"));
    }

    @Test
    void shouldReturnDegradedHealthWhenTooManyPendingTasks() throws Exception {
        // Given
        Health dbHealth = Health.up().build();
        when(databaseMetrics.health()).thenReturn(dbHealth);
        when(taskRepository.countByStatus(TaskStatus.PENDING)).thenReturn(150L); // > 100
        when(taskRepository.countByStatus(TaskStatus.IN_PROGRESS)).thenReturn(5L);

        // When/Then
        mockMvc.perform(get("/admin/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DOWN"))
                .andExpect(jsonPath("$.taskQueue.status").value("DEGRADED"));
    }

    @Test
    void shouldReturnErrorHealthOnException() throws Exception {
        // Given
        when(databaseMetrics.health()).thenThrow(new RuntimeException("Database error"));

        // When/Then
        mockMvc.perform(get("/admin/health"))
                .andExpect(status().is(503))
                .andExpect(jsonPath("$.status").value("DOWN"))
                .andExpect(jsonPath("$.error").value("Database error"));
    }

    private Task createTestTask(Long id, TaskType type, TaskStatus status) {
        Task task = new Task();
        task.setId(id);
        task.setType(type);
        task.setStatus(status);
        task.setAttempt(1);
        task.setMaxAttempts(3);
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        return task;
    }

    private Build createTestBuild(Long id, String job, BuildStatus status) {
        Build build = new Build();
        build.setId(id);
        build.setJob(job);
        build.setBuildNumber(123);
        build.setBranch("main");
        build.setRepoUrl("https://github.com/test/repo");
        build.setCommitSha("abc123");
        build.setStatus(status);
        build.setCreatedAt(LocalDateTime.now());
        build.setUpdatedAt(LocalDateTime.now());
        return build;
    }
}