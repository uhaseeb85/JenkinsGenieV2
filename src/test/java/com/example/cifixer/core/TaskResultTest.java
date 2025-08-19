package com.example.cifixer.core;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TaskResultTest {

    @Test
    void shouldCreateSuccessResult() {
        TaskResult result = TaskResult.success("Task completed successfully");
        
        assertEquals(TaskStatus.COMPLETED, result.getStatus());
        assertEquals("Task completed successfully", result.getMessage());
        assertNotNull(result.getMetadata());
        assertTrue(result.getMetadata().isEmpty());
    }

    @Test
    void shouldCreateFailureResult() {
        TaskResult result = TaskResult.failure("Task failed");
        
        assertEquals(TaskStatus.FAILED, result.getStatus());
        assertEquals("Task failed", result.getMessage());
        assertNotNull(result.getMetadata());
    }

    @Test
    void shouldCreateRetryResult() {
        TaskResult result = TaskResult.retry("Task needs retry");
        
        assertEquals(TaskStatus.RETRY, result.getStatus());
        assertEquals("Task needs retry", result.getMessage());
    }

    @Test
    void shouldCreateResultWithMetadata() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("buildId", 123L);
        metadata.put("attempt", 1);
        
        TaskResult result = TaskResult.success("Task completed", metadata);
        
        assertEquals(TaskStatus.COMPLETED, result.getStatus());
        assertEquals("Task completed", result.getMessage());
        assertEquals(2, result.getMetadata().size());
        assertEquals(123L, result.getMetadata().get("buildId"));
        assertEquals(1, result.getMetadata().get("attempt"));
    }

    @Test
    void shouldAddMetadata() {
        TaskResult result = TaskResult.success("Task completed");
        result.addMetadata("key", "value");
        
        assertEquals("value", result.getMetadata().get("key"));
    }
}