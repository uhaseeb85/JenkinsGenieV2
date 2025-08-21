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
        long startTime = System.currentTimeMillis();
        String requestId = generateRequestId();
        
        // Add request ID to MDC for log correlation
        try {
            org.slf4j.MDC.put("llmRequestId", requestId);
            
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
                    .addHeader("User-Agent", "Multi-Agent-CI-Fixer/1.0")
                    .addHeader("X-Request-ID", requestId);
            
            // Add provider-specific headers
            addProviderHeaders(requestBuilder);
            
            Request httpRequest = requestBuilder.build();
            
            // Log detailed request information
            logDetailedRequest(httpRequest, requestJson);
            
            LlmResponse result = null;
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
                
                // Log detailed response information
                logDetailedResponse(response, responseJson);
                
                result = parseApiResponse(responseJson);
                return result;
            } finally {
                long endTime = System.currentTimeMillis();
                long duration = endTime - startTime;
                
                // Log performance metrics
                logger.info("LLM API call completed - RequestID: {}, Duration: {} ms, Model: {}, Provider: {}, Status: {}",
                    requestId, duration, defaultModel, apiProvider,
                    (result != null && !result.hasError()) ? "SUCCESS" : "FAILED");
                
                // Record metrics for monitoring
                recordApiCallMetrics(duration, result);
                
                // Clean up MDC to prevent memory leaks
                org.slf4j.MDC.remove("llmRequestId");
            }
            
        } catch (IOException e) {
            throw new LlmException("Network error calling API: " + e.getMessage(), "NETWORK_ERROR", true, e);
        } catch (Exception e) {
            throw new LlmException("Unexpected error calling API: " + e.getMessage(), "UNEXPECTED_ERROR", false, e);
        }
    }
    
    /**
     * Builds API request JSON based on provider and model.
     */
    private String buildApiRequest(String prompt) throws IOException {
        // Check if using Claude model (regardless of provider)
        if (defaultModel.toLowerCase().contains("claude")) {
            return buildAnthropicRequest(prompt);
        }
        
        // Otherwise use provider-specific format
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
        
        // Always use the full model name (OpenRouter requires provider prefix like "anthropic/claude-3.5-sonnet")
        request.setModel(defaultModel);
        
        // Set max tokens for response generation
        request.setMaxTokens(maxTokens);
        
        // Set system prompt
        request.setSystem("You are a senior Java Spring Boot developer. Generate minimal unified diffs to fix Spring Boot compilation and runtime errors. Always respond with a valid unified diff format.");
        
        // Create user message
        AnthropicMessage message = new AnthropicMessage();
        message.setRole("user");
        message.setContent(prompt);
        
        request.setMessages(new AnthropicMessage[]{message});
        
        // Log the model being used
        logger.debug("Using Claude model: {}", request.getModel());
        
        return objectMapper.writeValueAsString(request);
    }
    
    /**
     * Gets the appropriate API endpoint based on provider and model.
     */
    private String getApiEndpoint() {
        // For OpenRouter, always use the OpenAI-compatible endpoint regardless of model
        if ("openrouter".equalsIgnoreCase(apiProvider)) {
            return apiBaseUrl + OPENAI_CHAT_COMPLETIONS_ENDPOINT;
        }
        
        // For direct Anthropic API access
        if ("anthropic".equalsIgnoreCase(apiProvider)) {
            return apiBaseUrl + ANTHROPIC_MESSAGES_ENDPOINT;
        }
        
        // For other providers (OpenAI, Azure, etc.)
        return apiBaseUrl + OPENAI_CHAT_COMPLETIONS_ENDPOINT;
    }
    
    /**
     * Adds provider-specific headers to the request.
     */
    private void addProviderHeaders(Request.Builder requestBuilder) {
        // For OpenRouter, add OpenRouter-specific headers
        if ("openrouter".equalsIgnoreCase(apiProvider)) {
            requestBuilder.addHeader("Authorization", "Bearer " + apiKey);
            requestBuilder.addHeader("HTTP-Referer", "https://github.com/your-org/ci-fixer");
            requestBuilder.addHeader("X-Title", "Multi-Agent CI Fixer");
            
            // Add Claude-specific headers if using a Claude model
            if (defaultModel.toLowerCase().contains("claude")) {
                requestBuilder.addHeader("anthropic-version", "2023-06-01");
            }
            return;
        }
        
        // For direct Anthropic API access
        if ("anthropic".equalsIgnoreCase(apiProvider)) {
            requestBuilder.addHeader("x-api-key", apiKey);
            requestBuilder.addHeader("anthropic-version", "2023-06-01");
            return;
        }
        
        // For OpenAI
        if ("openai".equalsIgnoreCase(apiProvider)) {
            requestBuilder.addHeader("Authorization", "Bearer " + apiKey);
            return;
        }
        
        // For Azure OpenAI
        if ("azure".equalsIgnoreCase(apiProvider)) {
            requestBuilder.addHeader("api-key", apiKey);
            return;
        }
        
        // Default case
        requestBuilder.addHeader("Authorization", "Bearer " + apiKey);
    }
    
    /**
     * Parses API response based on provider and model.
     */
    private LlmResponse parseApiResponse(String responseJson) throws IOException {
        // Check if using Claude model (regardless of provider)
        if (defaultModel.toLowerCase().contains("claude")) {
            try {
                return parseAnthropicResponse(responseJson);
            } catch (Exception e) {
                // If parsing as Anthropic response fails, try OpenAI format as fallback
                // This handles cases where OpenRouter might wrap Claude responses in OpenAI format
                logger.warn("Failed to parse Claude response in Anthropic format, trying OpenAI format: {}", e.getMessage());
                return parseOpenAiResponse(responseJson);
            }
        }
        
        // Otherwise use provider-specific format
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
        // With 128,000 max tokens, reserve 4,000 tokens for response
        int reservedTokens = Math.min(4000, maxTokens / 32);
        int maxPromptChars = (maxTokens - reservedTokens) * 4;
        
        if (prompt.length() <= maxPromptChars) {
            return prompt;
        }
        
        logger.warn("Truncating prompt from {} to {} characters (max tokens: {}, reserved: {})",
                   prompt.length(), maxPromptChars, maxTokens, reservedTokens);
        
        // Try to truncate at a reasonable boundary
        String truncated = prompt.substring(0, maxPromptChars);
        int lastNewline = truncated.lastIndexOf('\n');
        if (lastNewline > maxPromptChars * 0.9) {
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
    
    /**
     * Generates a unique request ID for tracking LLM API calls.
     *
     * @return A unique request ID string
     */
    private String generateRequestId() {
        return "llm-" + System.currentTimeMillis() + "-" +
               Math.abs(java.util.UUID.randomUUID().toString().hashCode() % 10000);
    }
    
    /**
     * Records metrics for LLM API calls for monitoring and analysis.
     *
     * @param duration The duration of the API call in milliseconds
     * @param response The LLM response object
     */
    private void recordApiCallMetrics(long duration, LlmResponse response) {
        try {
            // Log token usage if available
            if (response != null && response.getUsage() != null) {
                LlmResponse.Usage usage = response.getUsage();
                logger.info("LLM Token Usage - Prompt: {}, Completion: {}, Total: {}",
                    usage.getPromptTokens(), usage.getCompletionTokens(), usage.getTotalTokens());
                
                // Here you could add metrics to a monitoring system like Prometheus
                // For example:
                // metricsRegistry.counter("llm.api.tokens.prompt").increment(usage.getPromptTokens());
                // metricsRegistry.counter("llm.api.tokens.completion").increment(usage.getCompletionTokens());
                // metricsRegistry.counter("llm.api.tokens.total").increment(usage.getTotalTokens());
            }
            
            // Record API call duration
            // metricsRegistry.timer("llm.api.duration").record(duration, TimeUnit.MILLISECONDS);
            
            // Record success/failure
            // metricsRegistry.counter("llm.api.calls", "status", response != null && !response.hasError() ? "success" : "failure").increment();
        } catch (Exception e) {
            // Don't let metrics recording failure affect the main flow
            logger.warn("Failed to record LLM API metrics: {}", e.getMessage());
        }
    }
    
    /**
     * Logs detailed information about the LLM API request.
     *
     * @param request The HTTP request
     * @param requestJson The JSON payload
     */
    private void logDetailedRequest(Request request, String requestJson) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n==== LLM API REQUEST ====\n");
        sb.append(String.format("URL: %s\n", request.url()));
        sb.append(String.format("Method: %s\n", request.method()));
        sb.append("Headers:\n");
        
        request.headers().toMultimap().forEach((name, values) -> {
            // Mask sensitive headers like Authorization
            if (name.equalsIgnoreCase("Authorization") || name.equalsIgnoreCase("x-api-key")) {
                sb.append(String.format("  %s: %s\n", name, "********"));
            } else {
                sb.append(String.format("  %s: %s\n", name, String.join(", ", values)));
            }
        });
        
        sb.append("Body:\n");
        try {
            // Pretty print JSON if possible
            ObjectMapper mapper = new ObjectMapper();
            Object json = mapper.readValue(requestJson, Object.class);
            sb.append(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(json));
        } catch (Exception e) {
            // Fallback to raw JSON if pretty printing fails
            sb.append(requestJson);
        }
        sb.append("\n==========================\n");
        
        logger.info("Calling {} API endpoint: {} with model: {}", apiProvider, request.url(), defaultModel);
        logger.info(sb.toString());
    }
    
    /**
     * Logs detailed information about the LLM API response.
     *
     * @param response The HTTP response
     * @param responseJson The JSON response body
     */
    private void logDetailedResponse(Response response, String responseJson) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n==== LLM API RESPONSE ====\n");
        sb.append(String.format("Status: %d %s\n", response.code(), response.message()));
        sb.append("Headers:\n");
        
        response.headers().toMultimap().forEach((name, values) -> {
            sb.append(String.format("  %s: %s\n", name, String.join(", ", values)));
        });
        
        sb.append(String.format("Response Size: %d bytes\n", responseJson.length()));
        sb.append("Body:\n");
        try {
            // Pretty print JSON if possible
            ObjectMapper mapper = new ObjectMapper();
            Object json = mapper.readValue(responseJson, Object.class);
            sb.append(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(json));
        } catch (Exception e) {
            // Fallback to raw JSON if pretty printing fails
            sb.append(responseJson);
        }
        sb.append("\n===========================\n");
        
        logger.info("LLM Response received, status: {}, length: {}", response.code(), responseJson.length());
        logger.info(sb.toString());
    }
}