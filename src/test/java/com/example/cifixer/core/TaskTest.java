package com.example.cifixer.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Simple unit tests for Task entity.
 */
public class TaskTest {

    @Test
    public void testTaskCreation() {
        Task task = new Task();
        assertNotNull(task);
    }

    @Test
    public void testTaskStatusEnum() {
        // Test that TaskStatus enum values exist
        assertNotNull(TaskStatus.PENDING);
        assertNotNull(TaskStatus.IN_PROGRESS);
        assertNotNull(TaskStatus.COMPLETED);
        assertNotNull(TaskStatus.FAILED);
    }

    @Test
    public void testTaskTypeEnum() {
        // Test that TaskType enum values exist
        assertNotNull(TaskType.PLAN);
        assertNotNull(TaskType.RETRIEVE);
        assertNotNull(TaskType.PATCH);
        assertNotNull(TaskType.VALIDATE);
        assertNotNull(TaskType.CREATE_PR);
        assertNotNull(TaskType.NOTIFY);
    }

    @Test
    public void testTaskBasicProperties() {
        Task task = new Task();
        
        // Test ID
        task.setId(1L);
        assertEquals(1L, task.getId());
        
        // Test type
        task.setType(TaskType.PLAN);
        assertEquals(TaskType.PLAN, task.getType());
        
        // Test status
        task.setStatus(TaskStatus.PENDING);
        assertEquals(TaskStatus.PENDING, task.getStatus());
        
        // Test attempt
        task.setAttempt(1);
        assertEquals(Integer.valueOf(1), task.getAttempt());
        
        // Test max attempts
        task.setMaxAttempts(3);
        assertEquals(Integer.valueOf(3), task.getMaxAttempts());
    }

    @Test
    public void testTaskToString() {
        Task task = new Task();
        task.setType(TaskType.PLAN);
        task.setStatus(TaskStatus.PENDING);
        
        String toString = task.toString();
        assertNotNull(toString);
        // Basic test - just ensure toString doesn't throw exception
    }
}