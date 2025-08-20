package com.example.cifixer.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * HTTP client for communicating with external OpenAI-compatible API endpoints.
 * Supports multiple providers: OpenRouter, OpenAI, Anthropic, Azure OpenAI.
 * Handles Spring-aware prompting, response validation, retry logic, and secure SSL connections.
 */
@Component
public class LlmClient {
    
    private static final Logger logger = LoggerFactory.getLogger(LlmClient.class);
    
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final String OPENAI_CHAT_COMPLETIONS_ENDPOINT = "/chat/completions";
    private static final String ANTHROPIC_MESSAGES_ENDPOINT = "/messages";
    
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final UnifiedDiffValidator diffValidator;
    
    @Value("${llm.api.base-url:https://openrouter.ai/api/v1}")
    private String apiBaseUrl;
    
    @Value("${llm.api.key}")
    private String apiKey;
    
    @Value("${llm.api.model:anthropic/claude-3.5-sonnet}")
    private String defaultModel;
    
    @Value("${llm.api.provider:openrouter}")
    private String apiProvider;
    
    @Value("${llm.api.max-tokens:4000}")
    private int maxTokens;
    
    @Value("${llm.api.temperature:0.1}")
    private double temperature;
    
    @Value("${llm.api.timeout-seconds:60}")
    private int timeoutSeconds;
    
    @Value("${llm.api.max-retries:3}")
    private int maxRetries;
    
    @Autowired
    public LlmClient(@Qualifier("llmHttpClient") OkHttpClient httpClient,
                    ObjectMapper objectMapper, 
                    UnifiedDiffValidator diffValidator) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.diffValidator = diffValidator;
    }
    
    /**
     * Generates a code patch using the LLM with retry logic.
     */
    public String generatePatch(String prompt, String filePath) throws LlmException {
        logger.info("Generating patch for file: {}", filePath);
        
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                logger.debug("API attempt {} of {} for file: {}", attempt, maxRetries, filePath);
                
                LlmResponse response = callLlm(prompt);
                
                if (response.hasError()) {
                    throw new LlmException("API returned error: " + response.getError(), "LLM_ERROR", true);
                }
                
                String generatedText = response.getText();
                if (generatedText == null || generatedText.trim().isEmpty()) {
                    throw new LlmException("API returned empty response", "EMPTY_RESPONSE", true);
                }
                
                // Extract unified diff from response
                String diff = extractUnifiedDiff(generatedText);
                if (diff == null) {
                    logger.warn("No unified diff found in API response for attempt {}", attempt);
                    if (attempt == maxRetries) {
                        throw new LlmException("No valid unified diff found in API response after " + maxRetries + " attempts", "NO_DIFF", false);
                    }
                    continue;
                }
                
                // Validate the diff
                UnifiedDiffValidator.ValidationResult validation = diffValidator.validateUnifiedDiff(diff, filePath);
                if (!validation.isValid()) {
                    logger.warn("Invalid diff generated on attempt {}: {}", attempt, validation.getErrorMessage());
                    if (attempt == maxRetries) {
                        throw new LlmException("Generated diff validation failed: " + validation.getErrorMessage(), "INVALID_DIFF", false);
                    }
                    continue;
                }
                
                logger.info("Successfully generated valid patch for file: {} on attempt {}", filePath, attempt);
                return diff;
                
            } catch (LlmException e) {
                if (!e.isRetryable() || attempt == maxRetries) {
                    throw e;
                }
                logger.warn("Retryable error on attempt {} for file {}: {}", attempt, filePath, e.getMessage());
                
                // Exponential backoff with jitter
                try {
                    long backoffMs = (long) (Math.pow(2, attempt) * 1000 + Math.random() * 1000);
                    Thread.sleep(backoffMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new LlmException("Interrupted during retry backoff", ie);
                }
            }
        }
        
        throw new LlmException("Failed to generate patch after " + maxRetries + " attempts", "MAX_RETRIES_EXCEEDED", false);
    }
    
    /**
     * Makes HTTP call to external OpenAI-compatible API endpoint.
     */
    private LlmResponse callLlm(String prompt) throws LlmException {
        try {
            // Validate API key
            if (apiKey == null || apiKey.trim().isEmpty()) {
                throw new LlmException("API key not configured", "MISSING_API_KEY", false);
            }
            
            // Truncate prompt if it's too long
            String truncatedPrompt = truncatePrompt(prompt);
            
            // Build request based on provider
            String requestJson = buildApiRequest(truncatedPrompt);
            String endpoint = getApiEndpoint();
            
            RequestBody body = RequestBody.create(requestJson, JSON);
            Request.Builder requestBuilder = new Request.Builder()
                    .url(endpoint)
                    .post(body)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("User-Agent", "Multi-Agent-CI-Fixer/1.0");
            
            // Add provider-specific headers
            addProviderHeaders(requestBuilder);
            
            Request httpRequest = requestBuilder.build();
            
            logger.debug("Calling {} API endpoint: {} with model: {}", apiProvider, endpoint, defaultModel);
            
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "No error body";
                    logger.error("API error {}: {}", response.code(), errorBody);
                    throw new LlmException("HTTP error " + response.code() + ": " + errorBody, "HTTP_ERROR", isRetryableHttpError(response.code()));
                }
                
                if (response.body() == null) {
                    throw new LlmException("Empty response body from API", "EMPTY_BODY", true);
                }
                
                String responseJson = response.body().string();
                logger.debug("API response received, length: {}", responseJson.length());
                
                return parseApiResponse(responseJson);
            }
            
        } catch (IOException e) {
            throw new LlmException("Network error calling API: " + e.getMessage(), "NETWORK_ERROR", true, e);
        } catch (Exception e) {
            throw new LlmException("Unexpected error calling API: " + e.getMessage(), "UNEXPECTED_ERROR", false, e);
        }
    }
    
    /**
     * Builds API request JSON based on provider.
     */
    private String buildApiRequest(String prompt) throws IOException {
        switch (apiProvider.toLowerCase()) {
            case "anthropic":
                return buildAnthropicRequest(prompt);
            case "openai":
            case "openrouter":
            case "azure":
            default:
                return buildOpenAiRequest(prompt);
        }
    }
    
    /**
     * Builds OpenAI-compatible chat completion request.
     */
    private String buildOpenAiRequest(String prompt) throws IOException {
        ChatCompletionRequest request = new ChatCompletionRequest();
        request.setModel(defaultModel);
        request.setMaxTokens(maxTokens);
        request.setTemperature(temperature);
        
        // Create system and user messages
        ChatMessage systemMessage = new ChatMessage();
        systemMessage.setRole("system");
        systemMessage.setContent("You are a senior Java Spring Boot developer. Generate minimal unified diffs to fix Spring Boot compilation and runtime errors. Always respond with a valid unified diff format.");
        
        ChatMessage userMessage = new ChatMessage();
        userMessage.setRole("user");
        userMessage.setContent(prompt);
        
        request.setMessages(new ChatMessage[]{systemMessage, userMessage});
        
        return objectMapper.writeValueAsString(request);
    }
    
    /**
     * Builds Anthropic messages request.
     */
    private String buildAnthropicRequest(String prompt) throws IOException {
        AnthropicRequest request = new AnthropicRequest();
        request.setModel(defaultModel);
        request.setMaxTokens(maxTokens);
        request.setSystem("You are a senior Java Spring Boot developer. Generate minimal unified diffs to fix Spring Boot compilation and runtime errors. Always respond with a valid unified diff format.");
        
        AnthropicMessage message = new AnthropicMessage();
        message.setRole("user");
        message.setContent(prompt);
        
        request.setMessages(new AnthropicMessage[]{message});
        
        return objectMapper.writeValueAsString(request);
    }
    
    /**
     * Gets the appropriate API endpoint based on provider.
     */
    private String getApiEndpoint() {
        switch (apiProvider.toLowerCase()) {
            case "anthropic":
                return apiBaseUrl + ANTHROPIC_MESSAGES_ENDPOINT;
            case "openai":
            case "openrouter":
            case "azure":
            default:
                return apiBaseUrl + OPENAI_CHAT_COMPLETIONS_ENDPOINT;
        }
    }
    
    /**
     * Adds provider-specific headers to the request.
     */
    private void addProviderHeaders(Request.Builder requestBuilder) {
        switch (apiProvider.toLowerCase()) {
            case "anthropic":
                requestBuilder.addHeader("x-api-key", apiKey);
                requestBuilder.addHeader("anthropic-version", "2023-06-01");
                break;
            case "openai":
                requestBuilder.addHeader("Authorization", "Bearer " + apiKey);
                break;
            case "openrouter":
                requestBuilder.addHeader("Authorization", "Bearer " + apiKey);
                requestBuilder.addHeader("HTTP-Referer", "https://github.com/your-org/ci-fixer");
                requestBuilder.addHeader("X-Title", "Multi-Agent CI Fixer");
                break;
            case "azure":
                requestBuilder.addHeader("api-key", apiKey);
                break;
            default:
                requestBuilder.addHeader("Authorization", "Bearer " + apiKey);
                break;
        }
    }
    
    /**
     * Parses API response based on provider.
     */
    private LlmResponse parseApiResponse(String responseJson) throws IOException {
        switch (apiProvider.toLowerCase()) {
            case "anthropic":
                return parseAnthropicResponse(responseJson);
            case "openai":
            case "openrouter":
            case "azure":
            default:
                return parseOpenAiResponse(responseJson);
        }
    }
    
    /**
     * Parses OpenAI-compatible response.
     */
    private LlmResponse parseOpenAiResponse(String responseJson) throws IOException {
        ChatCompletionResponse apiResponse = objectMapper.readValue(responseJson, ChatCompletionResponse.class);
        
        LlmResponse response = new LlmResponse();
        
        if (apiResponse.getError() != null) {
            response.setError(apiResponse.getError().getMessage());
            return response;
        }
        
        if (apiResponse.getChoices() != null && apiResponse.getChoices().length > 0) {
            String content = apiResponse.getChoices()[0].getMessage().getContent();
            response.setText(content);
            
            // Convert to LlmResponse.Choice format
            LlmResponse.Choice[] choices = new LlmResponse.Choice[apiResponse.getChoices().length];
            for (int i = 0; i < apiResponse.getChoices().length; i++) {
                choices[i] = new LlmResponse.Choice();
                choices[i].setText(apiResponse.getChoices()[i].getMessage().getContent());
                choices[i].setFinishReason(apiResponse.getChoices()[i].getFinishReason());
            }
            response.setChoices(choices);
        }
        
        if (apiResponse.getUsage() != null) {
            LlmResponse.Usage usage = new LlmResponse.Usage();
            usage.setPromptTokens(apiResponse.getUsage().getPromptTokens());
            usage.setCompletionTokens(apiResponse.getUsage().getCompletionTokens());
            usage.setTotalTokens(apiResponse.getUsage().getTotalTokens());
            response.setUsage(usage);
        }
        
        return response;
    }
    
    /**
     * Parses Anthropic response.
     */
    private LlmResponse parseAnthropicResponse(String responseJson) throws IOException {
        AnthropicResponse apiResponse = objectMapper.readValue(responseJson, AnthropicResponse.class);
        
        LlmResponse response = new LlmResponse();
        
        if (apiResponse.getError() != null) {
            response.setError(apiResponse.getError().getMessage());
            return response;
        }
        
        if (apiResponse.getContent() != null && apiResponse.getContent().length > 0) {
            String content = apiResponse.getContent()[0].getText();
            response.setText(content);
            
            // Convert to LlmResponse.Choice format
            LlmResponse.Choice[] choices = new LlmResponse.Choice[1];
            choices[0] = new LlmResponse.Choice();
            choices[0].setText(content);
            choices[0].setFinishReason(apiResponse.getStopReason());
            response.setChoices(choices);
        }
        
        if (apiResponse.getUsage() != null) {
            LlmResponse.Usage usage = new LlmResponse.Usage();
            usage.setPromptTokens(apiResponse.getUsage().getInputTokens());
            usage.setCompletionTokens(apiResponse.getUsage().getOutputTokens());
            usage.setTotalTokens(apiResponse.getUsage().getInputTokens() + apiResponse.getUsage().getOutputTokens());
            response.setUsage(usage);
        }
        
        return response;
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
     * Health check for external API endpoint.
     */
    public boolean isHealthy() {
        try {
            // Use models endpoint for health check
            String healthEndpoint = apiBaseUrl + "/models";
            Request.Builder requestBuilder = new Request.Builder()
                    .url(healthEndpoint)
                    .get()
                    .addHeader("User-Agent", "Multi-Agent-CI-Fixer/1.0");
            
            // Add authentication headers
            addProviderHeaders(requestBuilder);
            
            Request request = requestBuilder.build();
            
            try (Response response = httpClient.newCall(request).execute()) {
                boolean healthy = response.isSuccessful();
                logger.debug("API health check: {} - {}", healthEndpoint, healthy ? "healthy" : "unhealthy");
                return healthy;
            }
        } catch (Exception e) {
            logger.warn("API health check failed: {}", e.getMessage());
            return false;
        }
    }
}