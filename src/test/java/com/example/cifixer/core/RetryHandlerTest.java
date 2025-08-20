package com.example.cifixer.core;

import com.example.cifixer.store.Build;
import com.example.cifixer.store.BuildRepository;
import com.example.cifixer.store.BuildStatus;
import com.example.cifixer.web.WebhookValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RetryHandlerTest {
    
    @Mock
    private TaskQueue taskQueue;
    
    @Mock
    private BuildRepository buildRepository;
    
    private RetryHandler retryHandler;
    
    @BeforeEach
    void setUp() {
        retryHandler = new RetryHandler(taskQueue, buildRepository);
        
        // Set test configuration values
        ReflectionTestUtils.setField(retryHandler, "baseDelaySeconds", 1);
        ReflectionTestUtils.setField(retryHandler, "maxDelaySeconds", 60);
        ReflectionTestUtils.setField(retryHandler, "jitterEnabled", false);
    }
    
    @Test
    void shouldRetryTaskWhenAttemptsRemaining() {
        // Given
        Build build = createTestBuild();
        Task task = createTestTask(build, 1, 3);
        Exception error = new RuntimeException("Test error");
        
        // When
        retryHandler.handleTaskFailure(task, error);
        
        // Then
        verify(taskQueue, never()).updateStatus(eq(task.getId()), eq(TaskStatus.FAILED), any());
        verify(buildRepository, never()).save(any(Build.class));
        
        // Verify task status is updated to RETRY
        assertEquals(TaskStatus.RETRY, task.getStatus());
        assertTrue(task.getErrorMessage().contains("Test error"));
        assertEquals(2, task.getAttempt());
    }
    
    @Test
    void shouldFailTaskPermanentlyWhenMaxAttemptsReached() {
        // Given
        Build build = createTestBuild();
        Task task = createTestTask(build, 3, 3);
        Exception error = new RuntimeException("Final error");
        
        // When
        retryHandler.handleTaskFailure(task, error);
        
        // Then
        verify(taskQueue).updateStatus(eq(task.getId()), eq(TaskStatus.FAILED), contains("Final error"));
        verify(buildRepository).save(build);
        assertEquals(BuildStatus.FAILED, build.getStatus());
    }
    
    @Test
    void shouldNotRetryNonRetryableErrors() {
        // Given
        Build build = createTestBuild();
        Task task = createTestTask(build, 1, 3);
        Exception error = new SecurityException("Security violation");
        
        // When
        retryHandler.handleTaskFailure(task, error);
        
        // Then
        verify(taskQueue).updateStatus(eq(task.getId()), eq(TaskStatus.FAILED), contains("Security violation"));
        verify(buildRepository).save(build);
        assertEquals(BuildStatus.FAILED, build.getStatus());
    }
    
    @Test
    void shouldNotRetryValidationErrors() {
        // Given
        Build build = createTestBuild();
        Task task = createTestTask(build, 1, 3);
        Exception error = new WebhookValidator.ValidationException("Invalid input");
        
        // When
        retryHandler.handleTaskFailure(task, error);
        
        // Then
        verify(taskQueue).updateStatus(eq(task.getId()), eq(TaskStatus.FAILED), contains("Invalid input"));
        verify(buildRepository).save(build);
        assertEquals(BuildStatus.FAILED, build.getStatus());
    }
    
    @Test
    void shouldCalculateExponentialBackoffDelay() {
        // Given
        ReflectionTestUtils.setField(retryHandler, "baseDelaySeconds", 2);
        ReflectionTestUtils.setField(retryHandler, "maxDelaySeconds", 300);
        
        // When/Then
        assertEquals(2, invokeCalculateRetryDelay(0));  // 2 * 2^0 = 2
        assertEquals(4, invokeCalculateRetryDelay(1));  // 2 * 2^1 = 4
        assertEquals(8, invokeCalculateRetryDelay(2));  // 2 * 2^2 = 8
        assertEquals(16, invokeCalculateRetryDelay(3)); // 2 * 2^3 = 16
    }
    
    @Test
    void shouldCapDelayAtMaximum() {
        // Given
        ReflectionTestUtils.setField(retryHandler, "baseDelaySeconds", 2);
        ReflectionTestUtils.setField(retryHandler, "maxDelaySeconds", 10);
        
        // When/Then
        assertEquals(10, invokeCalculateRetryDelay(10)); // Should be capped at 10
    }
    
    @Test
    void shouldHandleManualRetrySuccessfully() {
        // Given
        Build build = createTestBuild();
        Task task = createTestTask(build, 3, 3);
        task.setStatus(TaskStatus.FAILED);
        
        when(taskQueue.findById(1L)).thenReturn(task);
        
        // When
        boolean result = retryHandler.manualRetry(1L);
        
        // Then
        assertTrue(result);
        assertEquals(TaskStatus.PENDING, task.getStatus());
        assertEquals(0, task.getAttempt());
        assertNull(task.getErrorMessage());
        verify(taskQueue).enqueue(task);
    }
    
    @Test
    void shouldFailManualRetryForNonFailedTask() {
        // Given
        Build build = createTestBuild();
        Task task = createTestTask(build, 1, 3);
        task.setStatus(TaskStatus.IN_PROGRESS);
        
        when(taskQueue.findById(1L)).thenReturn(task);
        
        // When
        boolean result = retryHandler.manualRetry(1L);
        
        // Then
        assertFalse(result);
        verify(taskQueue, never()).enqueue(any());
    }
    
    @Test
    void shouldFailManualRetryForNonExistentTask() {
        // Given
        when(taskQueue.findById(999L)).thenReturn(null);
        
        // When
        boolean result = retryHandler.manualRetry(999L);
        
        // Then
        assertFalse(result);
        verify(taskQueue, never()).enqueue(any());
    }
    
    @Test
    void shouldBuildComprehensiveErrorMessage() {
        // Given
        Build build = createTestBuild();
        Task task = createTestTask(build, 1, 3);
        Exception cause = new IllegalStateException("Root cause");
        Exception error = new RuntimeException("Main error", cause);
        
        // When
        retryHandler.handleTaskFailure(task, error);
        
        // Then
        assertTrue(task.getErrorMessage().contains("RuntimeException: Main error"));
        assertTrue(task.getErrorMessage().contains("caused by: Root cause"));
    }
    
    @Test
    void shouldHandleNullErrorMessage() {
        // Given
        Build build = createTestBuild();
        Task task = createTestTask(build, 1, 3);
        Exception error = new RuntimeException((String) null);
        
        // When
        retryHandler.handleTaskFailure(task, error);
        
        // Then
        assertTrue(task.getErrorMessage().contains("RuntimeException"));
    }
    
    private Build createTestBuild() {
        Build build = new Build("test-job", 123, "main", 
            "https://github.com/test/repo.git", "abc123");
        build.setId(1L);
        return build;
    }
    
    private Task createTestTask(Build build, int attempt, int maxAttempts) {
        Task task = new Task(build, TaskType.PLAN);
        task.setId(1L);
        task.setAttempt(attempt);
        task.setMaxAttempts(maxAttempts);
        return task;
    }
    
    private long invokeCalculateRetryDelay(int attempt) {
        try {
            return (Long) ReflectionTestUtils.invokeMethod(retryHandler, "calculateRetryDelay", attempt);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}