package com.example.cifixer.github;

import com.example.cifixer.core.SecretManager;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Client for interacting with the GitHub REST API.
 * Handles authentication, rate limiting, error handling, and secure SSL connections.
 */
@Component
public class GitHubClient {
    
    private static final Logger logger = LoggerFactory.getLogger(GitHubClient.class);
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final SecretManager secretManager;
    
    @Value("${external.apis.github.base-url:https://api.github.com}")
    private String githubApiBase;
    
    @Value("${external.apis.github.retry.max-attempts:3}")
    private int maxRetryAttempts;
    
    public GitHubClient(@Qualifier("githubHttpClient") OkHttpClient httpClient, 
                       ObjectMapper objectMapper, 
                       SecretManager secretManager) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.secretManager = secretManager;
    }
    
    /**
     * Creates a pull request on GitHub with retry logic.
     *
     * @param owner Repository owner
     * @param repo Repository name
     * @param request Pull request creation request
     * @return GitHub API response containing PR details
     * @throws GitHubApiException if the API call fails
     */
    public GitHubPullRequestResponse createPullRequest(String owner, String repo, GitHubCreatePullRequestRequest request) 
            throws GitHubApiException {
        
        String githubToken = secretManager.getGithubToken();
        if (githubToken == null) {
            throw new GitHubApiException("GitHub token not configured");
        }
        
        String url = String.format("%s/repos/%s/%s/pulls", githubApiBase, owner, repo);
        
        return executeWithRetry(() -> {
            try {
                String requestBody = objectMapper.writeValueAsString(request);
                
                Request httpRequest = new Request.Builder()
                        .url(url)
                        .header("Authorization", "token " + githubToken)
                        .header("Accept", "application/vnd.github.v3+json")
                        .header("User-Agent", "Multi-Agent-CI-Fixer/1.0")
                        .post(RequestBody.create(requestBody, JSON))
                        .build();
                
                logger.info("Creating PR for {}/{} from {} to {} (token: {})",
                    owner, repo, request.getHead(), request.getBase(), secretManager.redactToken(githubToken));
                logger.info("GitHub API Request URL: {}", url);
                logger.info("GitHub API Request Body: {}", requestBody);
                
                try (Response response = httpClient.newCall(httpRequest).execute()) {
                    String responseBody = response.body() != null ? response.body().string() : "";
                    
                    logger.info("GitHub API Response Status: {}", response.code());
                    logger.info("GitHub API Response Body: {}", responseBody);
                    
                    if (!response.isSuccessful()) {
                        handleApiError(response, responseBody);
                    }
                    
                    GitHubPullRequestResponse prResponse = objectMapper.readValue(responseBody, GitHubPullRequestResponse.class);
                    logger.info("Successfully created PR #{} at {}", prResponse.getNumber(), prResponse.getHtmlUrl());
                    logger.info("Full PR Response: {}", objectMapper.writeValueAsString(prResponse));
                    
                    return prResponse;
                }
                
            } catch (IOException e) {
                logger.error("Failed to create pull request for {}/{}: {}", owner, repo, e.getMessage());
                throw new GitHubApiException("Failed to create pull request", e);
            }
        }, "createPullRequest");
    }
    
    /**
     * Adds labels to a pull request with retry logic.
     *
     * @param owner Repository owner
     * @param repo Repository name
     * @param prNumber Pull request number
     * @param labels Array of label names to add
     * @throws GitHubApiException if the API call fails
     */
    public void addLabels(String owner, String repo, int prNumber, String[] labels) throws GitHubApiException {
        String githubToken = secretManager.getGithubToken();
        if (githubToken == null) {
            throw new GitHubApiException("GitHub token not configured");
        }
        
        String url = String.format("%s/repos/%s/%s/issues/%d/labels", githubApiBase, owner, repo, prNumber);
        
        executeWithRetry(() -> {
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
                    return null; // Void return for executeWithRetry
                }
                
            } catch (IOException e) {
                logger.error("Failed to add labels to PR #{} in {}/{}: {}", prNumber, owner, repo, e.getMessage());
                throw new GitHubApiException("Failed to add labels to pull request", e);
            }
        }, "addLabels");
    }
    
    /**
     * Executes an API call with retry logic for transient failures.
     *
     * @param operation The operation to execute
     * @param operationName Name for logging
     * @return Result of the operation
     * @throws GitHubApiException if all retries fail
     */
    private <T> T executeWithRetry(GitHubOperation<T> operation, String operationName) throws GitHubApiException {
        GitHubApiException lastException = null;
        
        for (int attempt = 1; attempt <= maxRetryAttempts; attempt++) {
            try {
                return operation.execute();
            } catch (GitHubApiException e) {
                lastException = e;
                
                if (!isRetryableError(e) || attempt == maxRetryAttempts) {
                    throw e;
                }
                
                long backoffMs = calculateBackoff(attempt);
                logger.warn("GitHub API {} failed on attempt {}/{}, retrying in {}ms: {}", 
                    operationName, attempt, maxRetryAttempts, backoffMs, e.getMessage());
                
                try {
                    Thread.sleep(backoffMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new GitHubApiException("Interrupted during retry backoff", ie);
                }
            }
        }
        
        throw lastException;
    }
    
    /**
     * Determines if a GitHub API error is retryable.
     */
    private boolean isRetryableError(GitHubApiException e) {
        String message = e.getMessage().toLowerCase();
        return message.contains("rate limit") || 
               message.contains("timeout") || 
               message.contains("server error") ||
               message.contains("502") || 
               message.contains("503") || 
               message.contains("504");
    }
    
    /**
     * Calculates exponential backoff delay.
     */
    private long calculateBackoff(int attempt) {
        return Math.min(1000L * (1L << (attempt - 1)), 30000L); // Max 30 seconds
    }
    
    /**
     * Functional interface for GitHub operations that can be retried.
     */
    @FunctionalInterface
    private interface GitHubOperation<T> {
        T execute() throws GitHubApiException;
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