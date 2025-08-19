package com.example.cifixer.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * HTTP client for communicating with local LLM endpoints.
 * Handles Spring-aware prompting, response validation, and retry logic.
 */
@Component
public class LlmClient {
    
    private static final Logger logger = LoggerFactory.getLogger(LlmClient.class);
    
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final int MAX_RETRIES = 3;
    private static final int DEFAULT_MAX_TOKENS = 2000;
    private static final double DEFAULT_TEMPERATURE = 0.1;
    private static final int DEFAULT_TIMEOUT_SECONDS = 60;
    
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final UnifiedDiffValidator diffValidator;
    
    @Value("${llm.endpoint:http://localhost:11434/api/generate}")
    private String llmEndpoint;
    
    @Value("${llm.model:codellama:7b}")
    private String defaultModel;
    
    @Value("${llm.max-tokens:2000}")
    private int maxTokens;
    
    @Value("${llm.temperature:0.1}")
    private double temperature;
    
    @Value("${llm.timeout-seconds:60}")
    private int timeoutSeconds;
    
    @Autowired
    public LlmClient(ObjectMapper objectMapper, UnifiedDiffValidator diffValidator) {
        this.objectMapper = objectMapper;
        this.diffValidator = diffValidator;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .writeTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .build();
    }
    
    /**
     * Generates a code patch using the LLM with retry logic.
     */
    public String generatePatch(String prompt, String filePath) throws LlmException {
        logger.info("Generating patch for file: {}", filePath);
        
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                logger.debug("LLM attempt {} of {} for file: {}", attempt, MAX_RETRIES, filePath);
                
                LlmResponse response = callLlm(prompt);
                
                if (response.hasError()) {
                    throw new LlmException("LLM returned error: " + response.getError(), "LLM_ERROR", true);
                }
                
                String generatedText = response.getText();
                if (generatedText == null || generatedText.trim().isEmpty()) {
                    throw new LlmException("LLM returned empty response", "EMPTY_RESPONSE", true);
                }
                
                // Extract unified diff from response
                String diff = extractUnifiedDiff(generatedText);
                if (diff == null) {
                    logger.warn("No unified diff found in LLM response for attempt {}", attempt);
                    if (attempt == MAX_RETRIES) {
                        throw new LlmException("No valid unified diff found in LLM response after " + MAX_RETRIES + " attempts", "NO_DIFF", false);
                    }
                    continue;
                }
                
                // Validate the diff
                UnifiedDiffValidator.ValidationResult validation = diffValidator.validateUnifiedDiff(diff, filePath);
                if (!validation.isValid()) {
                    logger.warn("Invalid diff generated on attempt {}: {}", attempt, validation.getErrorMessage());
                    if (attempt == MAX_RETRIES) {
                        throw new LlmException("Generated diff validation failed: " + validation.getErrorMessage(), "INVALID_DIFF", false);
                    }
                    continue;
                }
                
                logger.info("Successfully generated valid patch for file: {} on attempt {}", filePath, attempt);
                return diff;
                
            } catch (LlmException e) {
                if (!e.isRetryable() || attempt == MAX_RETRIES) {
                    throw e;
                }
                logger.warn("Retryable error on attempt {} for file {}: {}", attempt, filePath, e.getMessage());
                
                // Exponential backoff
                try {
                    Thread.sleep(1000L * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new LlmException("Interrupted during retry backoff", ie);
                }
            }
        }
        
        throw new LlmException("Failed to generate patch after " + MAX_RETRIES + " attempts", "MAX_RETRIES_EXCEEDED", false);
    }
    
    /**
     * Makes HTTP call to LLM endpoint.
     */
    private LlmResponse callLlm(String prompt) throws LlmException {
        try {
            // Truncate prompt if it's too long
            String truncatedPrompt = truncatePrompt(prompt);
            
            LlmRequest request = new LlmRequest(defaultModel, truncatedPrompt, maxTokens, temperature);
            String requestJson = objectMapper.writeValueAsString(request);
            
            RequestBody body = RequestBody.create(requestJson, JSON);
            Request httpRequest = new Request.Builder()
                    .url(llmEndpoint)
                    .post(body)
                    .addHeader("Content-Type", "application/json")
                    .build();
            
            logger.debug("Calling LLM endpoint: {} with model: {}", llmEndpoint, defaultModel);
            
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "No error body";
                    throw new LlmException("HTTP error " + response.code() + ": " + errorBody, "HTTP_ERROR", isRetryableHttpError(response.code()));
                }
                
                if (response.body() == null) {
                    throw new LlmException("Empty response body from LLM", "EMPTY_BODY", true);
                }
                
                String responseJson = response.body().string();
                logger.debug("LLM response received, length: {}", responseJson.length());
                
                return objectMapper.readValue(responseJson, LlmResponse.class);
            }
            
        } catch (IOException e) {
            throw new LlmException("Network error calling LLM: " + e.getMessage(), "NETWORK_ERROR", true, e);
        } catch (Exception e) {
            throw new LlmException("Unexpected error calling LLM: " + e.getMessage(), "UNEXPECTED_ERROR", false, e);
        }
    }
    
    /**
     * Extracts unified diff from LLM response text.
     */
    private String extractUnifiedDiff(String text) {
        if (text == null) {
            return null;
        }
        
        // Look for unified diff markers
        int startIndex = text.indexOf("--- a/");
        if (startIndex == -1) {
            // Try alternative markers
            startIndex = text.indexOf("```diff");
            if (startIndex != -1) {
                startIndex = text.indexOf("--- a/", startIndex);
            }
        }
        
        if (startIndex == -1) {
            logger.debug("No unified diff start marker found in response");
            return null;
        }
        
        // Find the end of the diff
        int endIndex = text.length();
        
        // Look for end markers
        int codeBlockEnd = text.indexOf("```", startIndex + 1);
        if (codeBlockEnd != -1) {
            endIndex = codeBlockEnd;
        }
        
        // Extract the diff portion
        String diff = text.substring(startIndex, endIndex).trim();
        
        // Basic validation that it looks like a diff
        if (diff.contains("--- a/") && diff.contains("+++ b/") && diff.contains("@@")) {
            return diff;
        }
        
        logger.debug("Extracted text does not appear to be a valid unified diff");
        return null;
    }
    
    /**
     * Truncates prompt to fit within token limits.
     */
    private String truncatePrompt(String prompt) {
        // Rough estimation: 1 token ≈ 4 characters
        int maxPromptChars = (maxTokens - 500) * 4; // Reserve 500 tokens for response
        
        if (prompt.length() <= maxPromptChars) {
            return prompt;
        }
        
        logger.warn("Truncating prompt from {} to {} characters", prompt.length(), maxPromptChars);
        
        // Try to truncate at a reasonable boundary
        String truncated = prompt.substring(0, maxPromptChars);
        int lastNewline = truncated.lastIndexOf('\n');
        if (lastNewline > maxPromptChars * 0.8) {
            truncated = truncated.substring(0, lastNewline);
        }
        
        return truncated + "\n\n[TRUNCATED - Content too long for context window]";
    }
    
    /**
     * Determines if an HTTP error code is retryable.
     */
    private boolean isRetryableHttpError(int code) {
        // Retry on server errors and rate limiting
        return code >= 500 || code == 429 || code == 408;
    }
    
    /**
     * Health check for LLM endpoint.
     */
    public boolean isHealthy() {
        try {
            Request request = new Request.Builder()
                    .url(llmEndpoint.replace("/api/generate", "/api/tags"))
                    .get()
                    .build();
            
            try (Response response = httpClient.newCall(request).execute()) {
                return response.isSuccessful();
            }
        } catch (Exception e) {
            logger.warn("LLM health check failed: {}", e.getMessage());
            return false;
        }
    }
}