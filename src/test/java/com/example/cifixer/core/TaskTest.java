package com.example.cifixer.core;

import com.example.cifixer.store.Build;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TaskTest {

    @Test
    void shouldCreateTaskWithDefaults() {
        Task task = new Task();
        
        assertEquals(TaskStatus.PENDING, task.getStatus());
        assertEquals(Integer.valueOf(0), task.getAttempt());
        assertEquals(Integer.valueOf(3), task.getMaxAttempts());
        assertNotNull(task.getCreatedAt());
        assertNotNull(task.getUpdatedAt());
    }

    @Test
    void shouldCreateTaskWithBuildAndType() {
        Build build = new Build("test-job", 123, "main", "https://github.com/test/repo.git", "abc123");
        Task task = new Task(build, TaskType.PLAN);
        
        assertEquals(build, task.getBuild());
        assertEquals(TaskType.PLAN, task.getType());
        assertEquals(TaskStatus.PENDING, task.getStatus());
    }

    @Test
    void shouldCreateTaskWithPayload() {
        Build build = new Build("test-job", 123, "main", "https://github.com/test/repo.git", "abc123");
        Map<String, Object> payload = new HashMap<>();
        payload.put("repoUrl", "https://github.com/example/repo");
        
        Task task = new Task(build, TaskType.RETRIEVE, payload);
        
        assertEquals(build, task.getBuild());
        assertEquals(TaskType.RETRIEVE, task.getType());
        assertEquals(payload, task.getPayload());
    }

    @Test
    void shouldUpdateTimestampWhenStatusChanges() {
        Task task = new Task();
        task.setStatus(TaskStatus.IN_PROGRESS);
        
        assertEquals(TaskStatus.IN_PROGRESS, task.getStatus());
        assertNotNull(task.getUpdatedAt());
    }
}