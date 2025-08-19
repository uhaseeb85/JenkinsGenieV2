package com.example.cifixer.github;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Client for interacting with the GitHub REST API.
 * Handles authentication, rate limiting, and error handling.
 */
@Component
public class GitHubClient {
    
    private static final Logger logger = LoggerFactory.getLogger(GitHubClient.class);
    private static final String GITHUB_API_BASE = "https://api.github.com";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String githubToken;
    
    public GitHubClient(@Value("${github.token}") String githubToken, ObjectMapper objectMapper) {
        this.githubToken = githubToken;
        this.objectMapper = objectMapper;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();
    }
    
    /**
     * Creates a pull request on GitHub.
     *
     * @param owner Repository owner
     * @param repo Repository name
     * @param request Pull request creation request
     * @return GitHub API response containing PR details
     * @throws GitHubApiException if the API call fails
     */
    public GitHubPullRequestResponse createPullRequest(String owner, String repo, GitHubCreatePullRequestRequest request) 
            throws GitHubApiException {
        
        String url = String.format("%s/repos/%s/%s/pulls", GITHUB_API_BASE, owner, repo);
        
        try {
            String requestBody = objectMapper.writeValueAsString(request);
            
            Request httpRequest = new Request.Builder()
                    .url(url)
                    .header("Authorization", "token " + githubToken)
                    .header("Accept", "application/vnd.github.v3+json")
                    .header("User-Agent", "Multi-Agent-CI-Fixer/1.0")
                    .post(RequestBody.create(requestBody, JSON))
                    .build();
            
            logger.info("Creating PR for {}/{} from {} to {}", owner, repo, request.getHead(), request.getBase());
            
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                String responseBody = response.body() != null ? response.body().string() : "";
                
                if (!response.isSuccessful()) {
                    handleApiError(response, responseBody);
                }
                
                GitHubPullRequestResponse prResponse = objectMapper.readValue(responseBody, GitHubPullRequestResponse.class);
                logger.info("Successfully created PR #{} at {}", prResponse.getNumber(), prResponse.getHtmlUrl());
                
                return prResponse;
            }
            
        } catch (IOException e) {
            logger.error("Failed to create pull request for {}/{}: {}", owner, repo, e.getMessage());
            throw new GitHubApiException("Failed to create pull request", e);
        }
    }
    
    /**
     * Adds labels to a pull request.
     *
     * @param owner Repository owner
     * @param repo Repository name
     * @param prNumber Pull request number
     * @param labels Array of label names to add
     * @throws GitHubApiException if the API call fails
     */
    public void addLabels(String owner, String repo, int prNumber, String[] labels) throws GitHubApiException {
        String url = String.format("%s/repos/%s/%s/issues/%d/labels", GITHUB_API_BASE, owner, repo, prNumber);
        
        try {
            String requestBody = objectMapper.writeValueAsString(labels);
            
            Request httpRequest = new Request.Builder()
                    .url(url)
                    .header("Authorization", "token " + githubToken)
                    .header("Accept", "application/vnd.github.v3+json")
                    .header("User-Agent", "Multi-Agent-CI-Fixer/1.0")
                    .post(RequestBody.create(requestBody, JSON))
                    .build();
            
            logger.debug("Adding labels {} to PR #{} in {}/{}", labels, prNumber, owner, repo);
            
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                String responseBody = response.body() != null ? response.body().string() : "";
                
                if (!response.isSuccessful()) {
                    handleApiError(response, responseBody);
                }
                
                logger.debug("Successfully added labels to PR #{}", prNumber);
            }
            
        } catch (IOException e) {
            logger.error("Failed to add labels to PR #{} in {}/{}: {}", prNumber, owner, repo, e.getMessage());
            throw new GitHubApiException("Failed to add labels to pull request", e);
        }
    }
    
    /**
     * Handles GitHub API error responses and throws appropriate exceptions.
     */
    private void handleApiError(Response response, String responseBody) throws GitHubApiException {
        int statusCode = response.code();
        
        try {
            JsonNode errorJson = objectMapper.readTree(responseBody);
            String message = errorJson.has("message") ? errorJson.get("message").asText() : "Unknown error";
            
            switch (statusCode) {
                case 401:
                    throw new GitHubApiException("GitHub authentication failed: " + message);
                case 403:
                    if (response.header("X-RateLimit-Remaining", "1").equals("0")) {
                        String resetTime = response.header("X-RateLimit-Reset", "unknown");
                        throw new GitHubApiException("GitHub API rate limit exceeded. Reset time: " + resetTime);
                    }
                    throw new GitHubApiException("GitHub API access forbidden: " + message);
                case 404:
                    throw new GitHubApiException("GitHub repository or resource not found: " + message);
                case 422:
                    throw new GitHubApiException("GitHub API validation failed: " + message);
                default:
                    throw new GitHubApiException("GitHub API error (" + statusCode + "): " + message);
            }
            
        } catch (IOException e) {
            throw new GitHubApiException("GitHub API error (" + statusCode + "): " + responseBody);
        }
    }
}