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
        llmClient = new LlmClient(objectMapper, diffValidator);
        
        // Set test configuration
        ReflectionTestUtils.setField(llmClient, "llmEndpoint", mockWebServer.url("/api/generate").toString());
        ReflectionTestUtils.setField(llmClient, "defaultModel", "test-model");
        ReflectionTestUtils.setField(llmClient, "maxTokens", 1000);
        ReflectionTestUtils.setField(llmClient, "temperature", 0.1);
        ReflectionTestUtils.setField(llmClient, "timeoutSeconds", 10);
    }
    
    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }
    
    @Test
    void shouldGenerateValidPatch() throws Exception {
        // Mock successful LLM response with valid diff
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
        
        LlmResponse mockResponse = new LlmResponse();
        mockResponse.setText(validDiff);
        
        mockWebServer.enqueue(new MockResponse()
                .setBody(objectMapper.writeValueAsString(mockResponse))
                .addHeader("Content-Type", "application/json"));
        
        String result = llmClient.generatePatch("Fix this Spring service", "src/main/java/com/example/UserService.java");
        
        assertThat(result).isEqualTo(validDiff);
        
        // Verify request
        RecordedRequest request = mockWebServer.takeRequest();
        assertThat(request.getPath()).isEqualTo("/api/generate");
        assertThat(request.getMethod()).isEqualTo("POST");
        
        LlmRequest sentRequest = objectMapper.readValue(request.getBody().readUtf8(), LlmRequest.class);
        assertThat(sentRequest.getModel()).isEqualTo("test-model");
        assertThat(sentRequest.getPrompt()).isEqualTo("Fix this Spring service");
        assertThat(sentRequest.getMaxTokens()).isEqualTo(1000);
        assertThat(sentRequest.getTemperature()).isEqualTo(0.1);
    }
    
    @Test
    void shouldRetryOnInvalidDiffAndSucceed() throws Exception {
        // First response: invalid diff (no headers)
        LlmResponse invalidResponse = new LlmResponse();
        invalidResponse.setText("Just add @Autowired annotation");
        
        // Second response: valid diff
        String validDiff = "--- a/src/main/java/com/example/UserService.java\n" +
            "+++ b/src/main/java/com/example/UserService.java\n" +
            "@@ -10,6 +10,7 @@\n" +
            " public class UserService {\n" +
            "+    @Autowired\n" +
            "     private UserRepository userRepository;";
        
        LlmResponse validResponse = new LlmResponse();
        validResponse.setText(validDiff);
        
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
        LlmResponse invalidResponse = new LlmResponse();
        invalidResponse.setText("Invalid response without diff");
        
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
    void shouldHandleLlmErrorResponse() throws Exception {
        LlmResponse errorResponse = new LlmResponse();
        errorResponse.setError("Model not found");
        
        mockWebServer.enqueue(new MockResponse()
                .setBody(objectMapper.writeValueAsString(errorResponse))
                .addHeader("Content-Type", "application/json"));
        
        assertThatThrownBy(() -> llmClient.generatePatch("Fix this", "src/main/java/Test.java"))
                .isInstanceOf(LlmException.class)
                .hasMessageContaining("LLM returned error: Model not found")
                .satisfies(e -> {
                    LlmException llmEx = (LlmException) e;
                    assertThat(llmEx.getErrorCode()).isEqualTo("LLM_ERROR");
                    assertThat(llmEx.isRetryable()).isTrue();
                });
    }
    
    @Test
    void shouldHandleEmptyResponse() throws Exception {
        LlmResponse emptyResponse = new LlmResponse();
        emptyResponse.setText("");
        
        mockWebServer.enqueue(new MockResponse()
                .setBody(objectMapper.writeValueAsString(emptyResponse))
                .addHeader("Content-Type", "application/json"));
        
        assertThatThrownBy(() -> llmClient.generatePatch("Fix this", "src/main/java/Test.java"))
                .isInstanceOf(LlmException.class)
                .hasMessageContaining("LLM returned empty response")
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
        
        LlmResponse mockResponse = new LlmResponse();
        mockResponse.setText("Here's the fix:\n\n```diff\n" + validDiff + "\n```\n\nThis should work.");
        
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
        
        LlmResponse mockResponse = new LlmResponse();
        mockResponse.setText(validDiff);
        
        mockWebServer.enqueue(new MockResponse()
                .setBody(objectMapper.writeValueAsString(mockResponse))
                .addHeader("Content-Type", "application/json"));
        
        String result = llmClient.generatePatch(longPrompt.toString(), "src/main/java/Test.java");
        
        assertThat(result).isEqualTo(validDiff);
        
        // Verify the request was truncated
        RecordedRequest request = mockWebServer.takeRequest();
        LlmRequest sentRequest = objectMapper.readValue(request.getBody().readUtf8(), LlmRequest.class);
        assertThat(sentRequest.getPrompt()).contains("[TRUNCATED");
        assertThat(sentRequest.getPrompt().length()).isLessThan(longPrompt.length());
    }
    
    @Test
    void shouldRejectDangerousDiff() throws Exception {
        String dangerousDiff = "--- a/src/main/java/com/example/UserService.java\n" +
            "+++ b/src/main/java/com/example/UserService.java\n" +
            "@@ -10,6 +10,7 @@\n" +
            " public class UserService {\n" +
            "+    Runtime.getRuntime().exec(\"rm -rf /\");\n" +
            "     private UserRepository userRepository;";
        
        LlmResponse mockResponse = new LlmResponse();
        mockResponse.setText(dangerousDiff);
        
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
                .setBody("{\"models\": []}"));
        
        boolean healthy = llmClient.isHealthy();
        
        assertThat(healthy).isTrue();
        
        RecordedRequest request = mockWebServer.takeRequest();
        assertThat(request.getPath()).isEqualTo("/api/tags");
        assertThat(request.getMethod()).isEqualTo("GET");
    }
    
    @Test
    void shouldReturnUnhealthyOnError() throws Exception {
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));
        
        boolean healthy = llmClient.isHealthy();
        
        assertThat(healthy).isFalse();
    }
}