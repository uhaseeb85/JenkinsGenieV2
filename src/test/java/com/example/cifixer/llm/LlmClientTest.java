package com.example.cifixer.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LlmClientTest {
    
    private MockWebServer mockWebServer;
    private LlmClient llmClient;
    private ObjectMapper objectMapper;
    private UnifiedDiffValidator diffValidator;
    
    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        
        objectMapper = new ObjectMapper();
        diffValidator = new UnifiedDiffValidator();
        
        // Create mock HTTP client
        okhttp3.OkHttpClient httpClient = new okhttp3.OkHttpClient.Builder()
                .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .build();
        
        llmClient = new LlmClient(httpClient, objectMapper, diffValidator);
        
        // Set test configuration for external API
        ReflectionTestUtils.setField(llmClient, "apiBaseUrl", mockWebServer.url("").toString().replaceAll("/$", ""));
        ReflectionTestUtils.setField(llmClient, "apiKey", "test-api-key");
        ReflectionTestUtils.setField(llmClient, "defaultModel", "test-model");
        ReflectionTestUtils.setField(llmClient, "apiProvider", "openrouter");
        ReflectionTestUtils.setField(llmClient, "maxTokens", 1000);
        ReflectionTestUtils.setField(llmClient, "temperature", 0.1);
        ReflectionTestUtils.setField(llmClient, "timeoutSeconds", 10);
        ReflectionTestUtils.setField(llmClient, "maxRetries", 3);
    }
    
    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }
    
    @Test
    void shouldGenerateValidPatch() throws Exception {
        // Mock successful OpenAI-compatible response with valid diff
        String validDiff = "--- a/src/main/java/com/example/UserService.java\n" +
            "+++ b/src/main/java/com/example/UserService.java\n" +
            "@@ -10,6 +10,7 @@\n" +
            " public class UserService {\n" +
            " \n" +
            "     private final UserRepository userRepository;\n" +
            "+    \n" +
            "+    @Autowired\n" +
            "     public UserService(UserRepository userRepository) {\n" +
            "         this.userRepository = userRepository;\n" +
            "     }";
        
        // Create OpenAI-compatible response
        ChatCompletionResponse mockResponse = new ChatCompletionResponse();
        ChatCompletionResponse.ChatChoice choice = new ChatCompletionResponse.ChatChoice();
        ChatMessage message = new ChatMessage("assistant", validDiff);
        choice.setMessage(message);
        choice.setFinishReason("stop");
        mockResponse.setChoices(new ChatCompletionResponse.ChatChoice[]{choice});
        
        mockWebServer.enqueue(new MockResponse()
                .setBody(objectMapper.writeValueAsString(mockResponse))
                .addHeader("Content-Type", "application/json"));
        
        String result = llmClient.generatePatch("Fix this Spring service", "src/main/java/com/example/UserService.java");
        
        assertThat(result).isEqualTo(validDiff);
        
        // Verify request
        RecordedRequest request = mockWebServer.takeRequest();
        assertThat(request.getPath()).isEqualTo("/chat/completions");
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer test-api-key");
        
        ChatCompletionRequest sentRequest = objectMapper.readValue(request.getBody().readUtf8(), ChatCompletionRequest.class);
        assertThat(sentRequest.getModel()).isEqualTo("test-model");
        assertThat(sentRequest.getMaxTokens()).isEqualTo(1000);
        assertThat(sentRequest.getTemperature()).isEqualTo(0.1);
        assertThat(sentRequest.getMessages()).hasSize(2); // system + user message
        assertThat(sentRequest.getMessages()[1].getContent()).isEqualTo("Fix this Spring service");
    }
    
    @Test
    void shouldRetryOnInvalidDiffAndSucceed() throws Exception {
        // First response: invalid diff (no headers)
        ChatCompletionResponse invalidResponse = new ChatCompletionResponse();
        ChatCompletionResponse.ChatChoice invalidChoice = new ChatCompletionResponse.ChatChoice();
        ChatMessage invalidMessage = new ChatMessage("assistant", "Just add @Autowired annotation");
        invalidChoice.setMessage(invalidMessage);
        invalidResponse.setChoices(new ChatCompletionResponse.ChatChoice[]{invalidChoice});
        
        // Second response: valid diff
        String validDiff = "--- a/src/main/java/com/example/UserService.java\n" +
            "+++ b/src/main/java/com/example/UserService.java\n" +
            "@@ -10,6 +10,7 @@\n" +
            " public class UserService {\n" +
            "+    @Autowired\n" +
            "     private UserRepository userRepository;";
        
        ChatCompletionResponse validResponse = new ChatCompletionResponse();
        ChatCompletionResponse.ChatChoice validChoice = new ChatCompletionResponse.ChatChoice();
        ChatMessage validMessage = new ChatMessage("assistant", validDiff);
        validChoice.setMessage(validMessage);
        validResponse.setChoices(new ChatCompletionResponse.ChatChoice[]{validChoice});
        
        mockWebServer.enqueue(new MockResponse()
                .setBody(objectMapper.writeValueAsString(invalidResponse))
                .addHeader("Content-Type", "application/json"));
        
        mockWebServer.enqueue(new MockResponse()
                .setBody(objectMapper.writeValueAsString(validResponse))
                .addHeader("Content-Type", "application/json"));
        
        String result = llmClient.generatePatch("Fix this", "src/main/java/com/example/UserService.java");
        
        assertThat(result).isEqualTo(validDiff);
        assertThat(mockWebServer.getRequestCount()).isEqualTo(2);
    }
    
    @Test
    void shouldFailAfterMaxRetries() throws Exception {
        // All responses return invalid diffs
        ChatCompletionResponse invalidResponse = new ChatCompletionResponse();
        ChatCompletionResponse.ChatChoice invalidChoice = new ChatCompletionResponse.ChatChoice();
        ChatMessage invalidMessage = new ChatMessage("assistant", "Invalid response without diff");
        invalidChoice.setMessage(invalidMessage);
        invalidResponse.setChoices(new ChatCompletionResponse.ChatChoice[]{invalidChoice});
        
        for (int i = 0; i < 3; i++) {
            mockWebServer.enqueue(new MockResponse()
                    .setBody(objectMapper.writeValueAsString(invalidResponse))
                    .addHeader("Content-Type", "application/json"));
        }
        
        assertThatThrownBy(() -> llmClient.generatePatch("Fix this", "src/main/java/com/example/UserService.java"))
                .isInstanceOf(LlmException.class)
                .hasMessageContaining("No valid unified diff found")
                .satisfies(e -> {
                    LlmException llmEx = (LlmException) e;
                    assertThat(llmEx.getErrorCode()).isEqualTo("NO_DIFF");
                    assertThat(llmEx.isRetryable()).isFalse();
                });
        
        assertThat(mockWebServer.getRequestCount()).isEqualTo(3);
    }
    
    @Test
    void shouldHandleHttpErrors() throws Exception {
        mockWebServer.enqueue(new MockResponse().setResponseCode(500).setBody("Internal Server Error"));
        
        assertThatThrownBy(() -> llmClient.generatePatch("Fix this", "src/main/java/Test.java"))
                .isInstanceOf(LlmException.class)
                .hasMessageContaining("HTTP error 500")
                .satisfies(e -> {
                    LlmException llmEx = (LlmException) e;
                    assertThat(llmEx.getErrorCode()).isEqualTo("HTTP_ERROR");
                    assertThat(llmEx.isRetryable()).isTrue();
                });
    }
    
    @Test
    void shouldHandleApiErrorResponse() throws Exception {
        ChatCompletionResponse errorResponse = new ChatCompletionResponse();
        ChatCompletionResponse.ApiError error = new ChatCompletionResponse.ApiError();
        error.setMessage("Model not found");
        error.setType("invalid_request_error");
        errorResponse.setError(error);
        
        mockWebServer.enqueue(new MockResponse()
                .setBody(objectMapper.writeValueAsString(errorResponse))
                .addHeader("Content-Type", "application/json"));
        
        assertThatThrownBy(() -> llmClient.generatePatch("Fix this", "src/main/java/Test.java"))
                .isInstanceOf(LlmException.class)
                .hasMessageContaining("API returned error: Model not found")
                .satisfies(e -> {
                    LlmException llmEx = (LlmException) e;
                    assertThat(llmEx.getErrorCode()).isEqualTo("LLM_ERROR");
                    assertThat(llmEx.isRetryable()).isTrue();
                });
    }
    
    @Test
    void shouldHandleEmptyResponse() throws Exception {
        ChatCompletionResponse emptyResponse = new ChatCompletionResponse();
        ChatCompletionResponse.ChatChoice emptyChoice = new ChatCompletionResponse.ChatChoice();
        ChatMessage emptyMessage = new ChatMessage("assistant", "");
        emptyChoice.setMessage(emptyMessage);
        emptyResponse.setChoices(new ChatCompletionResponse.ChatChoice[]{emptyChoice});
        
        mockWebServer.enqueue(new MockResponse()
                .setBody(objectMapper.writeValueAsString(emptyResponse))
                .addHeader("Content-Type", "application/json"));
        
        assertThatThrownBy(() -> llmClient.generatePatch("Fix this", "src/main/java/Test.java"))
                .isInstanceOf(LlmException.class)
                .hasMessageContaining("API returned empty response")
                .satisfies(e -> {
                    LlmException llmEx = (LlmException) e;
                    assertThat(llmEx.getErrorCode()).isEqualTo("EMPTY_RESPONSE");
                    assertThat(llmEx.isRetryable()).isTrue();
                });
    }
    
    @Test
    void shouldExtractDiffFromCodeBlock() throws Exception {
        String validDiff = "--- a/src/main/java/com/example/UserService.java\n" +
            "+++ b/src/main/java/com/example/UserService.java\n" +
            "@@ -10,6 +10,7 @@\n" +
            " public class UserService {\n" +
            "+    @Autowired\n" +
            "     private UserRepository userRepository;";
        
        ChatCompletionResponse mockResponse = new ChatCompletionResponse();
        ChatCompletionResponse.ChatChoice choice = new ChatCompletionResponse.ChatChoice();
        ChatMessage message = new ChatMessage("assistant", "Here's the fix:\n\n```diff\n" + validDiff + "\n```\n\nThis should work.");
        choice.setMessage(message);
        mockResponse.setChoices(new ChatCompletionResponse.ChatChoice[]{choice});
        
        mockWebServer.enqueue(new MockResponse()
                .setBody(objectMapper.writeValueAsString(mockResponse))
                .addHeader("Content-Type", "application/json"));
        
        String result = llmClient.generatePatch("Fix this", "src/main/java/com/example/UserService.java");
        
        assertThat(result).isEqualTo(validDiff);
    }
    
    @Test
    void shouldTruncateLongPrompts() throws Exception {
        // Create a very long prompt
        StringBuilder longPrompt = new StringBuilder();
        for (int i = 0; i < 10000; i++) {
            longPrompt.append("This is a very long prompt that should be truncated. ");
        }
        
        String validDiff = "--- a/src/main/java/Test.java\n" +
            "+++ b/src/main/java/Test.java\n" +
            "@@ -1,3 +1,4 @@\n" +
            " public class Test {\n" +
            "+    // Fixed\n" +
            " }";
        
        ChatCompletionResponse mockResponse = new ChatCompletionResponse();
        ChatCompletionResponse.ChatChoice choice = new ChatCompletionResponse.ChatChoice();
        ChatMessage message = new ChatMessage("assistant", validDiff);
        choice.setMessage(message);
        mockResponse.setChoices(new ChatCompletionResponse.ChatChoice[]{choice});
        
        mockWebServer.enqueue(new MockResponse()
                .setBody(objectMapper.writeValueAsString(mockResponse))
                .addHeader("Content-Type", "application/json"));
        
        String result = llmClient.generatePatch(longPrompt.toString(), "src/main/java/Test.java");
        
        assertThat(result).isEqualTo(validDiff);
        
        // Verify the request was truncated
        RecordedRequest request = mockWebServer.takeRequest();
        ChatCompletionRequest sentRequest = objectMapper.readValue(request.getBody().readUtf8(), ChatCompletionRequest.class);
        String userMessage = sentRequest.getMessages()[1].getContent();
        assertThat(userMessage).contains("[TRUNCATED");
        assertThat(userMessage.length()).isLessThan(longPrompt.length());
    }
    
    @Test
    void shouldRejectDangerousDiff() throws Exception {
        String dangerousDiff = "--- a/src/main/java/com/example/UserService.java\n" +
            "+++ b/src/main/java/com/example/UserService.java\n" +
            "@@ -10,6 +10,7 @@\n" +
            " public class UserService {\n" +
            "+    Runtime.getRuntime().exec(\"rm -rf /\");\n" +
            "     private UserRepository userRepository;";
        
        ChatCompletionResponse mockResponse = new ChatCompletionResponse();
        ChatCompletionResponse.ChatChoice choice = new ChatCompletionResponse.ChatChoice();
        ChatMessage message = new ChatMessage("assistant", dangerousDiff);
        choice.setMessage(message);
        mockResponse.setChoices(new ChatCompletionResponse.ChatChoice[]{choice});
        
        mockWebServer.enqueue(new MockResponse()
                .setBody(objectMapper.writeValueAsString(mockResponse))
                .addHeader("Content-Type", "application/json"));
        
        assertThatThrownBy(() -> llmClient.generatePatch("Fix this", "src/main/java/com/example/UserService.java"))
                .isInstanceOf(LlmException.class)
                .hasMessageContaining("Generated diff validation failed")
                .hasMessageContaining("Dangerous operation detected");
    }
    
    @Test
    void shouldCheckHealthEndpoint() throws Exception {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"data\": []}"));
        
        boolean healthy = llmClient.isHealthy();
        
        assertThat(healthy).isTrue();
        
        RecordedRequest request = mockWebServer.takeRequest();
        assertThat(request.getPath()).isEqualTo("/models");
        assertThat(request.getMethod()).isEqualTo("GET");
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer test-api-key");
    }
    
    @Test
    void shouldReturnUnhealthyOnError() throws Exception {
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));
        
        boolean healthy = llmClient.isHealthy();
        
        assertThat(healthy).isFalse();
    }
}