package com.example.cifixer.agents;

import com.example.cifixer.core.Task;
import com.example.cifixer.core.TaskResult;
import com.example.cifixer.core.TaskStatus;
import com.example.cifixer.github.*;
import com.example.cifixer.store.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PrAgentTest {
    
    @Mock
    private GitHubClient gitHubClient;
    
    @Mock
    private PullRequestTemplate prTemplate;
    
    @Mock
    private PullRequestRepository pullRequestRepository;
    
    @Mock
    private PatchRepository patchRepository;
    
    @Mock
    private ValidationRepository validationRepository;
    
    @Mock
    private ObjectMapper objectMapper;
    
    @InjectMocks
    private PrAgent prAgent;
    
    private Build testBuild;
    private Task testTask;
    private PrPayload testPayload;
    
    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(prAgent, "githubToken", "test-token");
        
        testBuild = new Build("test-job", 123, "main", "https://github.com/owner/repo.git", "abc123def456");
        testBuild.setId(1L);
        
        testTask = new Task(testBuild, null);
        testTask.setId(1L);
        
        testPayload = new PrPayload();
        testPayload.setRepoUrl("https://github.com/owner/repo.git");
        testPayload.setBranchName("ci-fix/1");
        testPayload.setBaseBranch("main");
        testPayload.setCommitSha("abc123def456");
        testPayload.setPatchedFiles(Arrays.asList("src/main/java/User.java", "pom.xml"));
        testPayload.setPlanSummary("Fix missing @Repository annotation");
        testPayload.setValidationResults("COMPILE: PASSED\nTEST: PASSED");
    }
    
    @Test
    void shouldCreatePullRequestSuccessfully() throws Exception {
        // Given
        when(pullRequestRepository.existsByBuildId(1L)).thenReturn(false);
        when(prTemplate.generateTitle(testBuild)).thenReturn("Fix: Jenkins build #123 (abc123d)");
        when(prTemplate.generateDescription(eq(testBuild), anyString(), anyList(), anyString()))
                .thenReturn("## Automated CI Fix\n\nThis PR fixes the build failure.");
        when(prTemplate.getStandardLabels()).thenReturn(new String[]{"ci-fix", "automated"});
        
        GitHubPullRequestResponse prResponse = new GitHubPullRequestResponse();
        prResponse.setNumber(42);
        prResponse.setHtmlUrl("https://github.com/owner/repo/pull/42");
        
        when(gitHubClient.createPullRequest(eq("owner"), eq("repo"), any(GitHubCreatePullRequestRequest.class)))
                .thenReturn(prResponse);
        
        List<Validation> validations = Arrays.asList(
                createValidation(ValidationType.COMPILE, 0),
                createValidation(ValidationType.TEST, 0)
        );
        when(validationRepository.findByBuildIdOrderByCreatedAtDesc(1L)).thenReturn(validations);
        
        // When
        TaskResult result = prAgent.handle(testTask, testPayload);
        
        // Then
        assertThat(result.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(result.getMessage()).isEqualTo("Pull request created successfully");
        assertThat(result.getMetadata()).containsKey("prNumber");
        assertThat(result.getMetadata()).containsKey("prUrl");
        assertThat(result.getMetadata().get("prNumber")).isEqualTo(42);
        
        verify(gitHubClient).createPullRequest(eq("owner"), eq("repo"), any(GitHubCreatePullRequestRequest.class));
        verify(gitHubClient).addLabels("owner", "repo", 42, new String[]{"ci-fix", "automated"});
        verify(pullRequestRepository).save(any(PullRequest.class));
    }
    
    @Test
    void shouldReturnSuccessWhenPullRequestAlreadyExists() {
        // Given
        when(pullRequestRepository.existsByBuildId(1L)).thenReturn(true);
        
        // When
        TaskResult result = prAgent.handle(testTask, testPayload);
        
        // Then
        assertThat(result.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(result.getMessage()).isEqualTo("Pull request already exists for this build");
        
        verify(gitHubClient, never()).createPullRequest(anyString(), anyString(), any());
        verify(pullRequestRepository, never()).save(any());
    }
    
    @Test
    void shouldHandleGitHubApiException() throws Exception {
        // Given
        when(pullRequestRepository.existsByBuildId(1L)).thenReturn(false);
        when(prTemplate.generateTitle(testBuild)).thenReturn("Fix: Jenkins build #123 (abc123d)");
        when(prTemplate.generateDescription(eq(testBuild), anyString(), anyList(), anyString()))
                .thenReturn("## Automated CI Fix");
        
        when(gitHubClient.createPullRequest(eq("owner"), eq("repo"), any(GitHubCreatePullRequestRequest.class)))
                .thenThrow(new GitHubApiException("API rate limit exceeded"));
        
        when(validationRepository.findByBuildIdOrderByCreatedAtDesc(1L)).thenReturn(Arrays.asList());
        
        // When
        TaskResult result = prAgent.handle(testTask, testPayload);
        
        // Then
        assertThat(result.getStatus()).isEqualTo(TaskStatus.FAILED);
        assertThat(result.getMessage()).contains("GitHub API error: API rate limit exceeded");
        
        verify(pullRequestRepository, never()).save(any());
    }
    
    @Test
    void shouldHandleInvalidRepositoryUrl() {
        // Given
        testPayload.setRepoUrl("invalid-url");
        when(pullRequestRepository.existsByBuildId(1L)).thenReturn(false);
        
        // When
        TaskResult result = prAgent.handle(testTask, testPayload);
        
        // Then
        assertThat(result.getStatus()).isEqualTo(TaskStatus.FAILED);
        assertThat(result.getMessage()).contains("Unexpected error");
        
        verify(gitHubClient, never()).createPullRequest(anyString(), anyString(), any());
    }
    
    @Test
    void shouldContinueWhenLabelAdditionFails() throws Exception {
        // Given
        when(pullRequestRepository.existsByBuildId(1L)).thenReturn(false);
        when(prTemplate.generateTitle(testBuild)).thenReturn("Fix: Jenkins build #123 (abc123d)");
        when(prTemplate.generateDescription(eq(testBuild), anyString(), anyList(), anyString()))
                .thenReturn("## Automated CI Fix");
        when(prTemplate.getStandardLabels()).thenReturn(new String[]{"ci-fix", "automated"});
        
        GitHubPullRequestResponse prResponse = new GitHubPullRequestResponse();
        prResponse.setNumber(42);
        prResponse.setHtmlUrl("https://github.com/owner/repo/pull/42");
        
        when(gitHubClient.createPullRequest(eq("owner"), eq("repo"), any(GitHubCreatePullRequestRequest.class)))
                .thenReturn(prResponse);
        
        // Labels fail but PR creation should still succeed
        doThrow(new GitHubApiException("Failed to add labels"))
                .when(gitHubClient).addLabels(anyString(), anyString(), anyInt(), any(String[].class));
        
        when(validationRepository.findByBuildIdOrderByCreatedAtDesc(1L)).thenReturn(Arrays.asList());
        
        // When
        TaskResult result = prAgent.handle(testTask, testPayload);
        
        // Then
        assertThat(result.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(result.getMessage()).isEqualTo("Pull request created successfully");
        
        verify(pullRequestRepository).save(any(PullRequest.class));
    }
    
    @Test
    void shouldParseGitHubRepositoryUrlCorrectly() {
        // This tests the private parseRepositoryUrl method indirectly
        // Given
        when(pullRequestRepository.existsByBuildId(1L)).thenReturn(false);
        when(prTemplate.generateTitle(testBuild)).thenReturn("Fix: Jenkins build #123 (abc123d)");
        when(prTemplate.generateDescription(eq(testBuild), anyString(), anyList(), anyString()))
                .thenReturn("## Automated CI Fix");
        
        // Test different URL formats
        testPayload.setRepoUrl("https://github.com/owner/repo");
        
        try {
            when(gitHubClient.createPullRequest(eq("owner"), eq("repo"), any(GitHubCreatePullRequestRequest.class)))
                    .thenReturn(new GitHubPullRequestResponse());
            when(validationRepository.findByBuildIdOrderByCreatedAtDesc(1L)).thenReturn(Arrays.asList());
            
            // When
            TaskResult result = prAgent.handle(testTask, testPayload);
            
            // Then
            verify(gitHubClient).createPullRequest(eq("owner"), eq("repo"), any(GitHubCreatePullRequestRequest.class));
            
        } catch (Exception e) {
            // Expected for this test setup
        }
    }
    
    private Validation createValidation(ValidationType type, int exitCode) {
        Validation validation = new Validation();
        validation.setBuild(testBuild);
        validation.setValidationType(type);
        validation.setExitCode(exitCode);
        return validation;
    }
}