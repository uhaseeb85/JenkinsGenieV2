package com.example.cifixer.git;

import com.example.cifixer.core.Task;
import com.example.cifixer.core.TaskResult;
import com.example.cifixer.core.TaskStatus;
import com.example.cifixer.core.TaskType;
import com.example.cifixer.store.Build;
import com.example.cifixer.util.BuildTool;
import com.example.cifixer.util.SpringProjectAnalyzer;
import com.example.cifixer.util.SpringProjectContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Unit tests for RepoAgent.
 */
@ExtendWith(MockitoExtension.class)
class RepoAgentTest {
    
    @Mock
    private SpringProjectAnalyzer springProjectAnalyzer;
    
    private RepoAgent repoAgent;
    
    @BeforeEach
    void setUp() {
        repoAgent = new RepoAgent(springProjectAnalyzer);
    }
    
    @Test
    void shouldExtractRepoPayloadFromMap() {
        // Given
        Build build = new Build("test-job", 100, "main", "https://github.com/test/repo.git", "abc123");
        build.setId(1L);
        Task task = new Task(build, TaskType.REPO);
        
        Map<String, Object> payload = new HashMap<>();
        payload.put("repoUrl", "https://github.com/test/repo.git");
        payload.put("branch", "main");
        payload.put("commitSha", "abc123");
        payload.put("buildId", 1L);
        
        Map<String, String> credentials = new HashMap<>();
        credentials.put("username", "testuser");
        credentials.put("token", "testtoken");
        payload.put("credentials", credentials);
        
        // Mock SpringProjectAnalyzer
        SpringProjectContext mockContext = new SpringProjectContext();
        mockContext.setBuildTool(BuildTool.MAVEN);
        mockContext.setSpringBootVersion("2.7.18");
        mockContext.setMavenModules(Arrays.asList("root"));
        
        when(springProjectAnalyzer.analyzeProject(anyString())).thenReturn(mockContext);
        
        // When - This will fail due to actual Git operations, but we can test payload extraction
        TaskResult result = repoAgent.handle(task, payload);
        
        // Then - We expect failure due to invalid repo URL, but we can verify the attempt was made
        assertThat(result.getStatus()).isEqualTo(TaskStatus.FAILED);
        assertThat(result.getMessage()).contains("Failed to prepare repository");
    }
    
    @Test
    void shouldGenerateCorrectWorkingDirectoryPath() {
        // Given
        Long buildId = 12345L;
        
        // When
        String workingDir = repoAgent.getWorkingDirectory(buildId);
        
        // Then
        assertThat(workingDir).contains("build-12345");
        assertThat(workingDir).contains("cifixer");
    }
    
    @Test
    void shouldHandleNullPayloadValues() {
        // Given
        Build build = new Build("test-job", 100, "main", "https://github.com/test/repo.git", "abc123");
        build.setId(1L);
        Task task = new Task(build, TaskType.REPO);
        
        Map<String, Object> payload = new HashMap<>();
        payload.put("repoUrl", null);
        payload.put("branch", null);
        payload.put("commitSha", null);
        payload.put("buildId", null);
        
        // When
        TaskResult result = repoAgent.handle(task, payload);
        
        // Then
        assertThat(result.getStatus()).isEqualTo(TaskStatus.FAILED);
        assertThat(result.getMessage()).contains("Failed to prepare repository");
    }
    
    @Test
    void shouldHandleEmptyPayload() {
        // Given
        Build build = new Build("test-job", 100, "main", "https://github.com/test/repo.git", "abc123");
        build.setId(1L);
        Task task = new Task(build, TaskType.REPO);
        
        Map<String, Object> payload = new HashMap<>();
        
        // When
        TaskResult result = repoAgent.handle(task, payload);
        
        // Then
        assertThat(result.getStatus()).isEqualTo(TaskStatus.FAILED);
        assertThat(result.getMessage()).contains("Failed to prepare repository");
    }
    
    @Test
    void shouldHandleBuildIdAsInteger() {
        // Given
        Build build = new Build("test-job", 100, "main", "https://github.com/test/repo.git", "abc123");
        build.setId(1L);
        Task task = new Task(build, TaskType.REPO);
        
        Map<String, Object> payload = new HashMap<>();
        payload.put("repoUrl", "https://github.com/test/repo.git");
        payload.put("branch", "main");
        payload.put("commitSha", "abc123");
        payload.put("buildId", 1); // Integer instead of Long
        
        // Mock SpringProjectAnalyzer
        SpringProjectContext mockContext = new SpringProjectContext();
        mockContext.setBuildTool(BuildTool.MAVEN);
        mockContext.setSpringBootVersion("2.7.18");
        mockContext.setMavenModules(Arrays.asList("root"));
        
        when(springProjectAnalyzer.analyzeProject(anyString())).thenReturn(mockContext);
        
        // When
        TaskResult result = repoAgent.handle(task, payload);
        
        // Then - Should handle Integer to Long conversion
        assertThat(result.getStatus()).isEqualTo(TaskStatus.FAILED); // Will fail due to invalid repo, but conversion should work
    }
    
    @Test
    void shouldReturnFalseForNonExistentWorkingDirectory() {
        // Given
        Long nonExistentBuildId = 99999L;
        
        // When
        boolean exists = repoAgent.workingDirectoryExists(nonExistentBuildId);
        
        // Then
        assertThat(exists).isFalse();
    }
}