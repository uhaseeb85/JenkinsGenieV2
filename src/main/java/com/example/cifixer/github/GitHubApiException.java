package com.example.cifixer.github;

/**
 * Exception thrown when GitHub API operations fail.
 */
public class GitHubApiException extends Exception {
    
    public GitHubApiException(String message) {
        super(message);
    }
    
    public GitHubApiException(String message, Throwable cause) {
        super(message, cause);
    }
}