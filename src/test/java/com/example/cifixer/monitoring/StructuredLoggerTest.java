package com.example.cifixer.monitoring;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class StructuredLoggerTest {

    private ObjectMapper objectMapper;
    private StructuredLogger structuredLogger;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        structuredLogger = new StructuredLogger(objectMapper);
        MDC.clear();
    }
    
    private JsonNode extractJsonFromLog(String logOutput) throws Exception {
        // Extract JSON from log output (skip timestamp and logger prefix)
        String jsonPart = logOutput.substring(logOutput.indexOf('{'));
        return objectMapper.readTree(jsonPart.trim());
    }

    @Test
    void shouldLogInfoMessage(CapturedOutput output) throws Exception {
        // When
        structuredLogger.info("Test message", "key1", "value1", "key2", "value2");
        
        // Then
        String logOutput = output.getOut();
        assertThat(logOutput).isNotEmpty();
        
        JsonNode logEntry = extractJsonFromLog(logOutput);
        assertThat(logEntry.get("level").asText()).isEqualTo("INFO");
        assertThat(logEntry.get("message").asText()).isEqualTo("Test message");
        assertThat(logEntry.get("service").asText()).isEqualTo("multi-agent-ci-fixer");
        assertThat(logEntry.get("context").get("key1").asText()).isEqualTo("value1");
        assertThat(logEntry.get("context").get("key2").asText()).isEqualTo("value2");
    }

    @Test
    void shouldLogErrorWithException(CapturedOutput output) throws Exception {
        // Given
        Exception testException = new RuntimeException("Test error");
        
        // When
        structuredLogger.error("Error occurred", testException, "errorCode", "E001");
        
        // Then
        String logOutput = output.getOut();
        JsonNode logEntry = extractJsonFromLog(logOutput);
        
        assertThat(logEntry.get("level").asText()).isEqualTo("ERROR");
        assertThat(logEntry.get("message").asText()).isEqualTo("Error occurred");
        assertThat(logEntry.get("exception").get("class").asText()).isEqualTo("RuntimeException");
        assertThat(logEntry.get("exception").get("message").asText()).isEqualTo("Test error");
        assertThat(logEntry.get("context").get("errorCode").asText()).isEqualTo("E001");
    }

    @Test
    void shouldIncludeCorrelationIdFromMDC(CapturedOutput output) throws Exception {
        // Given
        String correlationId = "test-correlation-id";
        MDC.put("correlationId", correlationId);
        
        // When
        structuredLogger.info("Test message with correlation ID");
        
        // Then
        String logOutput = output.getOut();
        JsonNode logEntry = extractJsonFromLog(logOutput);
        
        assertThat(logEntry.get("correlationId").asText()).isEqualTo(correlationId);
    }

    @Test
    void shouldIncludeBuildAndTaskIdFromMDC(CapturedOutput output) throws Exception {
        // Given
        MDC.put("buildId", "12345");
        MDC.put("taskId", "67890");
        
        // When
        structuredLogger.info("Test message with IDs");
        
        // Then
        String logOutput = output.getOut();
        JsonNode logEntry = extractJsonFromLog(logOutput);
        
        assertThat(logEntry.get("buildId").asText()).isEqualTo("12345");
        assertThat(logEntry.get("taskId").asText()).isEqualTo("67890");
    }

    @Test
    void shouldLogTaskStartedEvent(CapturedOutput output) throws Exception {
        // When
        structuredLogger.taskStarted("PLAN", 123L, 456L);
        
        // Then
        String logOutput = output.getOut();
        JsonNode logEntry = extractJsonFromLog(logOutput);
        
        assertThat(logEntry.get("level").asText()).isEqualTo("INFO");
        assertThat(logEntry.get("message").asText()).isEqualTo("Task started");
        assertThat(logEntry.get("context").get("event").asText()).isEqualTo("task_started");
        assertThat(logEntry.get("context").get("taskType").asText()).isEqualTo("PLAN");
        assertThat(logEntry.get("context").get("taskId").asLong()).isEqualTo(123L);
        assertThat(logEntry.get("context").get("buildId").asLong()).isEqualTo(456L);
    }

    @Test
    void shouldLogTaskCompletedEvent(CapturedOutput output) throws Exception {
        // When
        structuredLogger.taskCompleted("PLAN", 123L, 456L, 5000L);
        
        // Then
        String logOutput = output.getOut();
        JsonNode logEntry = extractJsonFromLog(logOutput);
        
        assertThat(logEntry.get("level").asText()).isEqualTo("INFO");
        assertThat(logEntry.get("message").asText()).isEqualTo("Task completed successfully");
        assertThat(logEntry.get("context").get("event").asText()).isEqualTo("task_completed");
        assertThat(logEntry.get("context").get("durationMs").asLong()).isEqualTo(5000L);
    }

    @Test
    void shouldLogTaskFailedEvent(CapturedOutput output) throws Exception {
        // When
        structuredLogger.taskFailed("PLAN", 123L, 456L, "Validation failed", 2);
        
        // Then
        String logOutput = output.getOut();
        JsonNode logEntry = extractJsonFromLog(logOutput);
        
        assertThat(logEntry.get("level").asText()).isEqualTo("ERROR");
        assertThat(logEntry.get("message").asText()).isEqualTo("Task failed");
        assertThat(logEntry.get("context").get("event").asText()).isEqualTo("task_failed");
        assertThat(logEntry.get("context").get("errorMessage").asText()).isEqualTo("Validation failed");
        assertThat(logEntry.get("context").get("attemptNumber").asInt()).isEqualTo(2);
    }

    @Test
    void shouldLogTaskRetryEvent(CapturedOutput output) throws Exception {
        // When
        structuredLogger.taskRetry("PLAN", 123L, 456L, 3, 4000L);
        
        // Then
        String logOutput = output.getOut();
        JsonNode logEntry = extractJsonFromLog(logOutput);
        
        assertThat(logEntry.get("level").asText()).isEqualTo("WARN");
        assertThat(logEntry.get("message").asText()).isEqualTo("Task scheduled for retry");
        assertThat(logEntry.get("context").get("event").asText()).isEqualTo("task_retry");
        assertThat(logEntry.get("context").get("attemptNumber").asInt()).isEqualTo(3);
        assertThat(logEntry.get("context").get("delayMs").asLong()).isEqualTo(4000L);
    }

    @Test
    void shouldLogBuildProcessingEvents(CapturedOutput output) throws Exception {
        // When
        structuredLogger.buildProcessingStarted(789L, "test-job", "main", "abc123");
        
        // Then
        String logOutput = output.getOut();
        JsonNode logEntry = extractJsonFromLog(logOutput);
        
        assertThat(logEntry.get("level").asText()).isEqualTo("INFO");
        assertThat(logEntry.get("message").asText()).isEqualTo("Build processing started");
        assertThat(logEntry.get("context").get("event").asText()).isEqualTo("build_processing_started");
        assertThat(logEntry.get("context").get("buildId").asLong()).isEqualTo(789L);
        assertThat(logEntry.get("context").get("jobName").asText()).isEqualTo("test-job");
        assertThat(logEntry.get("context").get("branch").asText()).isEqualTo("main");
        assertThat(logEntry.get("context").get("commitSha").asText()).isEqualTo("abc123");
    }

    @Test
    void shouldLogExternalApiEvents(CapturedOutput output) throws Exception {
        // When
        structuredLogger.externalApiCall("LLM", "generate_patch", "https://api.openrouter.ai/v1/chat/completions");
        
        // Then
        String logOutput = output.getOut();
        JsonNode logEntry = extractJsonFromLog(logOutput);
        
        assertThat(logEntry.get("level").asText()).isEqualTo("DEBUG");
        assertThat(logEntry.get("message").asText()).isEqualTo("External API call initiated");
        assertThat(logEntry.get("context").get("event").asText()).isEqualTo("external_api_call");
        assertThat(logEntry.get("context").get("apiType").asText()).isEqualTo("LLM");
        assertThat(logEntry.get("context").get("operation").asText()).isEqualTo("generate_patch");
    }

    @Test
    void shouldLogExternalApiResponse(CapturedOutput output) throws Exception {
        // When
        structuredLogger.externalApiResponse("LLM", "generate_patch", 200, 1500L);
        
        // Then
        String logOutput = output.getOut();
        JsonNode logEntry = extractJsonFromLog(logOutput);
        
        assertThat(logEntry.get("level").asText()).isEqualTo("DEBUG");
        assertThat(logEntry.get("message").asText()).isEqualTo("External API call completed");
        assertThat(logEntry.get("context").get("statusCode").asInt()).isEqualTo(200);
        assertThat(logEntry.get("context").get("durationMs").asLong()).isEqualTo(1500L);
    }

    @Test
    void shouldLogErrorLevelForFailedApiResponse(CapturedOutput output) throws Exception {
        // When
        structuredLogger.externalApiResponse("LLM", "generate_patch", 500, 1500L);
        
        // Then
        String logOutput = output.getOut();
        JsonNode logEntry = extractJsonFromLog(logOutput);
        
        assertThat(logEntry.get("level").asText()).isEqualTo("ERROR");
    }

    @Test
    void shouldHandleMapContext(CapturedOutput output) throws Exception {
        // Given
        java.util.Map<String, Object> contextMap = new java.util.HashMap<>();
        contextMap.put("operation", "test");
        contextMap.put("count", 42);
        
        // When
        structuredLogger.info("Test with map context", contextMap);
        
        // Then
        String logOutput = output.getOut();
        JsonNode logEntry = extractJsonFromLog(logOutput);
        
        assertThat(logEntry.get("context").get("operation").asText()).isEqualTo("test");
        assertThat(logEntry.get("context").get("count").asInt()).isEqualTo(42);
    }

    @Test
    void shouldSetAndClearMDCContext() {
        // When
        StructuredLogger.setBuildId(12345L);
        StructuredLogger.setTaskId(67890L);
        
        // Then
        assertThat(MDC.get("buildId")).isEqualTo("12345");
        assertThat(MDC.get("taskId")).isEqualTo("67890");
        
        // When
        StructuredLogger.clearContext();
        
        // Then
        assertThat(MDC.get("buildId")).isNull();
        assertThat(MDC.get("taskId")).isNull();
    }

    @Test
    void shouldHandleNullValues() {
        // When/Then - Should not throw exception
        StructuredLogger.setBuildId(null);
        StructuredLogger.setTaskId(null);
        
        assertThat(MDC.get("buildId")).isNull();
        assertThat(MDC.get("taskId")).isNull();
    }

    @Test
    void shouldTruncateStackTrace(CapturedOutput output) throws Exception {
        // Given
        Exception deepException = createDeepStackTraceException(20);
        
        // When
        structuredLogger.error("Deep stack trace test", deepException);
        
        // Then
        String logOutput = output.getOut();
        JsonNode logEntry = extractJsonFromLog(logOutput);
        
        String stackTrace = logEntry.get("exception").get("stackTrace").asText();
        long lineCount = stackTrace.lines().count();
        
        // Should be truncated to ~12 lines (exception + 10 stack trace lines + "... more")
        assertThat(lineCount).isLessThanOrEqualTo(15);
        assertThat(stackTrace).contains("... 10 more");
    }

    private Exception createDeepStackTraceException(int depth) {
        if (depth <= 0) {
            return new RuntimeException("Deep exception");
        }
        try {
            throw createDeepStackTraceException(depth - 1);
        } catch (Exception e) {
            return new RuntimeException("Level " + depth, e);
        }
    }
}
