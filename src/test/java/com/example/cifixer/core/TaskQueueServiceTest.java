package com.example.cifixer.core;

import com.example.cifixer.store.Build;
import com.example.cifixer.store.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskQueueServiceTest {
    
    @Mock
    private TaskRepository taskRepository;
    
    @InjectMocks
    private TaskQueueService taskQueueService;
    
    private Build testBuild;
    private Task testTask;
    
    @BeforeEach
    void setUp() {
        testBuild = new Build("test-job", 123, "main", 
            "https://github.com/example/repo.git", "abc123");
        testBuild.setId(1L);
        
        testTask = new Task(testBuild, TaskType.PLAN);
        testTask.setId(1L);
    }
    
    @Test
    void enqueue_ShouldSaveTask() {
        // When
        taskQueueService.enqueue(testTask);
        
        // Then
        verify(taskRepository).save(testTask);
    }
    
    @Test
    void dequeue_ValidAgentType_ShouldReturnAndUpdateTask() {
        // Given
        when(taskRepository.findNextPendingTaskForUpdate("PLAN")).thenReturn(Optional.of(testTask));
        when(taskRepository.save(any(Task.class))).thenReturn(testTask);
        
        // When
        Optional<Task> result = taskQueueService.dequeue("PLAN");
        
        // Then
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(testTask);
        assertThat(testTask.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
        assertThat(testTask.getAttempt()).isEqualTo(1);
        
        verify(taskRepository).findNextPendingTaskForUpdate("PLAN");
        verify(taskRepository).save(testTask);
    }
    
    @Test
    void dequeue_NoTasksAvailable_ShouldReturnEmpty() {
        // Given
        when(taskRepository.findNextPendingTaskForUpdate("PLAN")).thenReturn(Optional.empty());
        
        // When
        Optional<Task> result = taskQueueService.dequeue("PLAN");
        
        // Then
        assertThat(result).isEmpty();
        verify(taskRepository, never()).save(any());
    }
    
    @Test
    void dequeue_InvalidAgentType_ShouldReturnEmpty() {
        // When
        Optional<Task> result = taskQueueService.dequeue("INVALID");
        
        // Then
        assertThat(result).isEmpty();
        verify(taskRepository, never()).findNextPendingTaskForUpdate(any());
    }
    
    @Test
    void updateStatus_TaskExists_ShouldUpdateStatus() {
        // Given
        when(taskRepository.findById(1L)).thenReturn(Optional.of(testTask));
        
        // When
        taskQueueService.updateStatus(1L, TaskStatus.COMPLETED);
        
        // Then
        assertThat(testTask.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        verify(taskRepository).save(testTask);
    }
    
    @Test
    void updateStatus_WithErrorMessage_ShouldUpdateStatusAndError() {
        // Given
        when(taskRepository.findById(1L)).thenReturn(Optional.of(testTask));
        String errorMessage = "Test error";
        
        // When
        taskQueueService.updateStatus(1L, TaskStatus.FAILED, errorMessage);
        
        // Then
        assertThat(testTask.getStatus()).isEqualTo(TaskStatus.FAILED);
        assertThat(testTask.getErrorMessage()).isEqualTo(errorMessage);
        verify(taskRepository).save(testTask);
    }
    
    @Test
    void updateStatus_TaskNotFound_ShouldNotThrow() {
        // Given
        when(taskRepository.findById(1L)).thenReturn(Optional.empty());
        
        // When & Then (should not throw)
        taskQueueService.updateStatus(1L, TaskStatus.COMPLETED);
        
        verify(taskRepository, never()).save(any());
    }
    
    @Test
    void shouldRetry_WithinMaxAttempts_ShouldReturnTrue() {
        // Given
        testTask.setAttempt(2);
        testTask.setMaxAttempts(3);
        
        // When
        boolean result = taskQueueService.shouldRetry(testTask);
        
        // Then
        assertThat(result).isTrue();
    }
    
    @Test
    void shouldRetry_ExceedsMaxAttempts_ShouldReturnFalse() {
        // Given
        testTask.setAttempt(3);
        testTask.setMaxAttempts(3);
        
        // When
        boolean result = taskQueueService.shouldRetry(testTask);
        
        // Then
        assertThat(result).isFalse();
    }
    
    @Test
    void requeueForRetry_WithinMaxAttempts_ShouldRequeueTask() {
        // Given
        testTask.setAttempt(1);
        testTask.setMaxAttempts(3);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(testTask));
        
        // When
        boolean result = taskQueueService.requeueForRetry(1L, "Test error");
        
        // Then
        assertThat(result).isTrue();
        assertThat(testTask.getStatus()).isEqualTo(TaskStatus.RETRY);
        assertThat(testTask.getErrorMessage()).isEqualTo("Test error");
        verify(taskRepository).save(testTask);
    }
    
    @Test
    void requeueForRetry_ExceedsMaxAttempts_ShouldMarkAsFailed() {
        // Given
        testTask.setAttempt(3);
        testTask.setMaxAttempts(3);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(testTask));
        
        // When
        boolean result = taskQueueService.requeueForRetry(1L, "Test error");
        
        // Then
        assertThat(result).isFalse();
        assertThat(testTask.getStatus()).isEqualTo(TaskStatus.FAILED);
        assertThat(testTask.getErrorMessage()).isEqualTo("Test error");
        verify(taskRepository).save(testTask);
    }
}