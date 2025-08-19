package com.example.cifixer.llm;

/**
 * Exception thrown when LLM operations fail.
 */
public class LlmException extends Exception {
    
    private final String errorCode;
    private final boolean retryable;
    
    public LlmException(String message) {
        super(message);
        this.errorCode = null;
        this.retryable = false;
    }
    
    public LlmException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = null;
        this.retryable = true; // Network errors are typically retryable
    }
    
    public LlmException(String message, String errorCode, boolean retryable) {
        super(message);
        this.errorCode = errorCode;
        this.retryable = retryable;
    }
    
    public LlmException(String message, String errorCode, boolean retryable, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.retryable = retryable;
    }
    
    public String getErrorCode() {
        return errorCode;
    }
    
    public boolean isRetryable() {
        return retryable;
    }
}