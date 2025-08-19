package com.example.cifixer.agents;

import com.example.cifixer.core.Task;
import com.example.cifixer.core.TaskResult;
import com.example.cifixer.core.TaskStatus;
import com.example.cifixer.store.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@SpringBootTest
@TestPropertySource(properties = {
        "github.token=test-token"
})
class PrAgentIntegrationTest {
    
    @Autowired
    private PrAgent prAgent;
    
    @MockBean
    private PullRequestRepository pullRequestRepository;
    
    @MockBean
    private ValidationRepository validationRepository;
    
    @MockBean
    private PatchRepository patchRepository;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    private MockWebServer mockGitHubServer;
    private Build testBuild;
    private Task testTask;
    private PrPayload testPayload;
    
    @BeforeEach
    void setUp() throws IOException {
        mockGitHubServer = new MockWebServer();
        mockGitHubServer.start();
        
        // Override GitHub API base URL to point to mock server
        String baseUrl = mockGitHubServer.url("/").toString();
        System.setProperty("github.api.base", baseUrl);
        
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
    }
    
    @AfterEach
    void tearDown() throws IOException {
        mockGitHubServer.shutdown();
        System.clearProperty("github.api.base");
    }
    
    @Test
    void shouldCreatePullRequestWithGitHubApi() throws Exception {
        // Given
        when(pullRequestRepository.existsByBuildId(1L)).thenReturn(false);
        when(pullRequestRepository.save(any(PullRequest.class))).thenAnswer(invocation -> {
            PullRequest pr = invocation.getArgument(0);
            pr.setId(1L);
            return pr;
        });
        when(validationRepository.findByBuildIdOrderByCreatedAtDesc(anyLong())).thenReturn(Arrays.asList());
        
        // Mock GitHub API responses
        String prResponseJson = "{\n" +
                "    \"id\": 12345,\n" +
                "    \"number\": 42,\n" +
                "    \"title\": \"Fix: Jenkins build #123 (abc123d)\",\n" +
                "    \"html_url\": \"https://github.com/owner/repo/pull/42\",\n" +
                "    \"state\": \"open\",\n" +
                "    \"draft\": false\n" +
                "}";
        
        mockGitHubServer.enqueue(new MockResponse()
                .setResponseCode(201)
                .setHeader("Content-Type", "application/json")
                .setBody(prResponseJson));
        
        // Mock labels API response
        mockGitHubServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("[]"));
        
        // When
        TaskResult result = prAgent.handle(testTask, testPayload);
        
        // Then
        assertThat(result.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(result.getMessage()).isEqualTo("Pull request created successfully");
        assertThat(result.getMetadata()).containsKey("prNumber");
        assertThat(result.getMetadata().get("prNumber")).isEqualTo(42);
        
        // Verify API calls
        RecordedRequest prRequest = mockGitHubServer.takeRequest();
        assertThat(prRequest.getPath()).isEqualTo("/repos/owner/repo/pulls");
        assertThat(prRequest.getMethod()).isEqualTo("POST");
        assertThat(prRequest.getHeader("Authorization")).isEqualTo("token test-token");
        
        RecordedRequest labelsRequest = mockGitHubServer.takeRequest();
        assertThat(labelsRequest.getPath()).isEqualTo("/issues/42/labels");
        assertThat(labelsRequest.getMethod()).isEqualTo("POST");
    }
    
    @Test
    void shouldHandleGitHubApiRateLimit() throws Exception {
        // Given
        when(pullRequestRepository.existsByBuildId(1L)).thenReturn(false);
        when(validationRepository.findByBuildIdOrderByCreatedAtDesc(anyLong())).thenReturn(Arrays.asList());
        
        // Mock rate limit response
        mockGitHubServer.enqueue(new MockResponse()
                .setResponseCode(403)
                .setHeader("X-RateLimit-Remaining", "0")
                .setHeader("X-RateLimit-Reset", "1640995200")
                .setHeader("Content-Type", "application/json")
                .setBody("{\n" +
                        "    \"message\": \"API rate limit exceeded\",\n" +
                        "    \"documentation_url\": \"https://docs.github.com/rest/overview/resources-in-the-rest-api#rate-limiting\"\n" +
                        "}"));
        
        // When
        TaskResult result = prAgent.handle(testTask, testPayload);
        
        // Then
        assertThat(result.getStatus()).isEqualTo(TaskStatus.FAILED);
        assertThat(result.getMessage()).contains("GitHub API rate limit exceeded");
        
        RecordedRequest request = mockGitHubServer.takeRequest();
        assertThat(request.getPath()).isEqualTo("/repos/owner/repo/pulls");
    }
    
    @Test
    void shouldHandleGitHubApiAuthenticationError() throws Exception {
        // Given
        when(pullRequestRepository.existsByBuildId(1L)).thenReturn(false);
        when(validationRepository.findByBuildIdOrderByCreatedAtDesc(anyLong())).thenReturn(Arrays.asList());
        
        // Mock authentication error
        mockGitHubServer.enqueue(new MockResponse()
                .setResponseCode(401)
                .setHeader("Content-Type", "application/json")
                .setBody("{\n" +
                        "    \"message\": \"Bad credentials\",\n" +
                        "    \"documentation_url\": \"https://docs.github.com/rest\"\n" +
                        "}"));
        
        // When
        TaskResult result = prAgent.handle(testTask, testPayload);
        
        // Then
        assertThat(result.getStatus()).isEqualTo(TaskStatus.FAILED);
        assertThat(result.getMessage()).contains("GitHub authentication failed");
    }
    
    @Test
    void shouldHandleRepositoryNotFound() throws Exception {
        // Given
        when(pullRequestRepository.existsByBuildId(1L)).thenReturn(false);
        when(validationRepository.findByBuildIdOrderByCreatedAtDesc(anyLong())).thenReturn(Arrays.asList());
        
        // Mock repository not found
        mockGitHubServer.enqueue(new MockResponse()
                .setResponseCode(404)
                .setHeader("Content-Type", "application/json")
                .setBody("{\n" +
                        "    \"message\": \"Not Found\",\n" +
                        "    \"documentation_url\": \"https://docs.github.com/rest/reference/repos#get-a-repository\"\n" +
                        "}"));
        
        // When
        TaskResult result = prAgent.handle(testTask, testPayload);
        
        // Then
        assertThat(result.getStatus()).isEqualTo(TaskStatus.FAILED);
        assertThat(result.getMessage()).contains("GitHub repository or resource not found");
    }
    
    @Test
    void shouldHandleValidationFailure() throws Exception {
        // Given
        when(pullRequestRepository.existsByBuildId(1L)).thenReturn(false);
        when(validationRepository.findByBuildIdOrderByCreatedAtDesc(anyLong())).thenReturn(Arrays.asList());
        
        // Mock validation error (e.g., branch already exists)
        mockGitHubServer.enqueue(new MockResponse()
                .setResponseCode(422)
                .setHeader("Content-Type", "application/json")
                .setBody("{\n" +
                        "    \"message\": \"Validation Failed\",\n" +
                        "    \"errors\": [\n" +
                        "        {\n" +
                        "            \"resource\": \"PullRequest\",\n" +
                        "            \"field\": \"head\",\n" +
                        "            \"code\": \"invalid\"\n" +
                        "        }\n" +
                        "    ]\n" +
                        "}"));
        
        // When
        TaskResult result = prAgent.handle(testTask, testPayload);
        
        // Then
        assertThat(result.getStatus()).isEqualTo(TaskStatus.FAILED);
        assertThat(result.getMessage()).contains("GitHub API validation failed");
    }
}