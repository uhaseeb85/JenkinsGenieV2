package com.example.cifixer.github;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GitHubClientTest {
    
    private MockWebServer mockServer;
    private GitHubClient gitHubClient;
    private ObjectMapper objectMapper;
    
    @BeforeEach
    void setUp() throws IOException {
        mockServer = new MockWebServer();
        mockServer.start();
        
        objectMapper = new ObjectMapper();
        
        // Create client with mock server URL
        String baseUrl = mockServer.url("/").toString();
        gitHubClient = new GitHubClient("test-token", objectMapper) {
            // Override the base URL for testing
        };
    }
    
    @AfterEach
    void tearDown() throws IOException {
        mockServer.shutdown();
    }
    
    @Test
    void shouldCreatePullRequestSuccessfully() throws Exception {
        // Given
        String responseJson = "{\n" +
                "    \"id\": 12345,\n" +
                "    \"number\": 42,\n" +
                "    \"title\": \"Fix: Jenkins build #123 (abc123d)\",\n" +
                "    \"html_url\": \"https://github.com/owner/repo/pull/42\",\n" +
                "    \"state\": \"open\",\n" +
                "    \"draft\": false\n" +
                "}";
        
        mockServer.enqueue(new MockResponse()
                .setResponseCode(201)
                .setHeader("Content-Type", "application/json")
                .setBody(responseJson));
        
        GitHubCreatePullRequestRequest request = new GitHubCreatePullRequestRequest(
                "Fix: Jenkins build #123 (abc123d)",
                "## Automated CI Fix",
                "ci-fix/123",
                "main"
        );
        
        // When
        GitHubPullRequestResponse response = gitHubClient.createPullRequest("owner", "repo", request);
        
        // Then
        assertThat(response.getNumber()).isEqualTo(42);
        assertThat(response.getTitle()).isEqualTo("Fix: Jenkins build #123 (abc123d)");
        assertThat(response.getHtmlUrl()).isEqualTo("https://github.com/owner/repo/pull/42");
        assertThat(response.getState()).isEqualTo("open");
        
        RecordedRequest recordedRequest = mockServer.takeRequest();
        assertThat(recordedRequest.getPath()).isEqualTo("/repos/owner/repo/pulls");
        assertThat(recordedRequest.getMethod()).isEqualTo("POST");
        assertThat(recordedRequest.getHeader("Authorization")).isEqualTo("token test-token");
        assertThat(recordedRequest.getHeader("Accept")).isEqualTo("application/vnd.github.v3+json");
    }
    
    @Test
    void shouldAddLabelsSuccessfully() throws Exception {
        // Given
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("[]"));
        
        String[] labels = {"ci-fix", "automated", "spring-boot"};
        
        // When
        gitHubClient.addLabels("owner", "repo", 42, labels);
        
        // Then
        RecordedRequest recordedRequest = mockServer.takeRequest();
        assertThat(recordedRequest.getPath()).isEqualTo("/issues/42/labels");
        assertThat(recordedRequest.getMethod()).isEqualTo("POST");
        assertThat(recordedRequest.getHeader("Authorization")).isEqualTo("token test-token");
        
        String requestBody = recordedRequest.getBody().readUtf8();
        assertThat(requestBody).contains("ci-fix");
        assertThat(requestBody).contains("automated");
        assertThat(requestBody).contains("spring-boot");
    }
    
    @Test
    void shouldThrowExceptionOnAuthenticationFailure() {
        // Given
        mockServer.enqueue(new MockResponse()
                .setResponseCode(401)
                .setHeader("Content-Type", "application/json")
                .setBody("{\n" +
                        "    \"message\": \"Bad credentials\"\n" +
                        "}"));
        
        GitHubCreatePullRequestRequest request = new GitHubCreatePullRequestRequest(
                "Test PR", "Test body", "feature", "main"
        );
        
        // When & Then
        assertThatThrownBy(() -> gitHubClient.createPullRequest("owner", "repo", request))
                .isInstanceOf(GitHubApiException.class)
                .hasMessageContaining("GitHub authentication failed");
    }
    
    @Test
    void shouldThrowExceptionOnRateLimit() {
        // Given
        mockServer.enqueue(new MockResponse()
                .setResponseCode(403)
                .setHeader("X-RateLimit-Remaining", "0")
                .setHeader("X-RateLimit-Reset", "1640995200")
                .setHeader("Content-Type", "application/json")
                .setBody("{\n" +
                        "    \"message\": \"API rate limit exceeded\"\n" +
                        "}"));
        
        GitHubCreatePullRequestRequest request = new GitHubCreatePullRequestRequest(
                "Test PR", "Test body", "feature", "main"
        );
        
        // When & Then
        assertThatThrownBy(() -> gitHubClient.createPullRequest("owner", "repo", request))
                .isInstanceOf(GitHubApiException.class)
                .hasMessageContaining("GitHub API rate limit exceeded");
    }
    
    @Test
    void shouldThrowExceptionOnRepositoryNotFound() {
        // Given
        mockServer.enqueue(new MockResponse()
                .setResponseCode(404)
                .setHeader("Content-Type", "application/json")
                .setBody("{\n" +
                        "    \"message\": \"Not Found\"\n" +
                        "}"));
        
        GitHubCreatePullRequestRequest request = new GitHubCreatePullRequestRequest(
                "Test PR", "Test body", "feature", "main"
        );
        
        // When & Then
        assertThatThrownBy(() -> gitHubClient.createPullRequest("owner", "repo", request))
                .isInstanceOf(GitHubApiException.class)
                .hasMessageContaining("GitHub repository or resource not found");
    }
    
    @Test
    void shouldThrowExceptionOnValidationFailure() {
        // Given
        mockServer.enqueue(new MockResponse()
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
        
        GitHubCreatePullRequestRequest request = new GitHubCreatePullRequestRequest(
                "Test PR", "Test body", "invalid-branch", "main"
        );
        
        // When & Then
        assertThatThrownBy(() -> gitHubClient.createPullRequest("owner", "repo", request))
                .isInstanceOf(GitHubApiException.class)
                .hasMessageContaining("GitHub API validation failed");
    }
}