package com.example.cifixer.monitoring;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Structured logger that outputs JSON formatted logs with correlation IDs and context.
 */
@Component
public class StructuredLogger {

    private static final Logger logger = LoggerFactory.getLogger(StructuredLogger.class);
    private final ObjectMapper objectMapper;

    public StructuredLogger(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void info(String message, Object... context) {
        logStructured("INFO", message, null, context);
    }

    public void warn(String message, Object... context) {
        logStructured("WARN", message, null, context);
    }

    public void error(String message, Throwable throwable, Object... context) {
        logStructured("ERROR", message, throwable, context);
    }

    public void debug(String message, Object... context) {
        logStructured("DEBUG", message, null, context);
    }

    public void taskStarted(String taskType, Long taskId, Long buildId) {
        Map<String, Object> context = new HashMap<>();
        context.put("event", "task_started");
        context.put("taskType", taskType);
        context.put("taskId", taskId);
        context.put("buildId", buildId);
        
        logStructured("INFO", "Task started", null, context);
    }

    public void taskCompleted(String taskType, Long taskId, Long buildId, long durationMs) {
        Map<String, Object> context = new HashMap<>();
        context.put("event", "task_completed");
        context.put("taskType", taskType);
        context.put("taskId", taskId);
        context.put("buildId", buildId);
        context.put("durationMs", durationMs);
        
        logStructured("INFO", "Task completed successfully", null, context);
    }

    public void taskFailed(String taskType, Long taskId, Long buildId, String errorMessage, int attemptNumber) {
        Map<String, Object> context = new HashMap<>();
        context.put("event", "task_failed");
        context.put("taskType", taskType);
        context.put("taskId", taskId);
        context.put("buildId", buildId);
        context.put("errorMessage", errorMessage);
        context.put("attemptNumber", attemptNumber);
        
        logStructured("ERROR", "Task failed", null, context);
    }

    public void taskRetry(String taskType, Long taskId, Long buildId, int attemptNumber, long delayMs) {
        Map<String, Object> context = new HashMap<>();
        context.put("event", "task_retry");
        context.put("taskType", taskType);
        context.put("taskId", taskId);
        context.put("buildId", buildId);
        context.put("attemptNumber", attemptNumber);
        context.put("delayMs", delayMs);
        
        logStructured("WARN", "Task scheduled for retry", null, context);
    }

    public void buildProcessingStarted(Long buildId, String jobName, String branch, String commitSha) {
        Map<String, Object> context = new HashMap<>();
        context.put("event", "build_processing_started");
        context.put("buildId", buildId);
        context.put("jobName", jobName);
        context.put("branch", branch);
        context.put("commitSha", commitSha);
        
        logStructured("INFO", "Build processing started", null, context);
    }

    public void buildProcessingCompleted(Long buildId, String status, long durationMs) {
        Map<String, Object> context = new HashMap<>();
        context.put("event", "build_processing_completed");
        context.put("buildId", buildId);
        context.put("status", status);
        context.put("durationMs", durationMs);
        
        logStructured("INFO", "Build processing completed", null, context);
    }

    public void externalApiCall(String apiType, String operation, String endpoint) {
        Map<String, Object> context = new HashMap<>();
        context.put("event", "external_api_call");
        context.put("apiType", apiType);
        context.put("operation", operation);
        context.put("endpoint", endpoint);
        
        logStructured("DEBUG", "External API call initiated", null, context);
    }

    public void externalApiResponse(String apiType, String operation, int statusCode, long durationMs) {
        Map<String, Object> context = new HashMap<>();
        context.put("event", "external_api_response");
        context.put("apiType", apiType);
        context.put("operation", operation);
        context.put("statusCode", statusCode);
        context.put("durationMs", durationMs);
        
        String level = statusCode >= 400 ? "ERROR" : "DEBUG";
        logStructured(level, "External API call completed", null, context);
    }

    private void logStructured(String level, String message, Throwable throwable, Object... contextItems) {
        try {
            Map<String, Object> logEntry = new HashMap<>();
            logEntry.put("timestamp", Instant.now().toString());
            logEntry.put("level", level);
            logEntry.put("message", message);
            logEntry.put("service", "multi-agent-ci-fixer");
            
            // Add correlation ID from MDC
            String correlationId = MDC.get("correlationId");
            if (correlationId != null) {
                logEntry.put("correlationId", correlationId);
            }
            
            // Add build ID from MDC if available
            String buildId = MDC.get("buildId");
            if (buildId != null) {
                logEntry.put("buildId", buildId);
            }
            
            // Add task ID from MDC if available
            String taskId = MDC.get("taskId");
            if (taskId != null) {
                logEntry.put("taskId", taskId);
            }
            
            // Add context items
            if (contextItems != null && contextItems.length > 0) {
                Map<String, Object> context = new HashMap<>();
                
                for (int i = 0; i < contextItems.length; i++) {
                    Object item = contextItems[i];
                    if (item instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> mapItem = (Map<String, Object>) item;
                        context.putAll(mapItem);
                    } else if (i + 1 < contextItems.length && contextItems[i] instanceof String) {
                        // Key-value pair
                        context.put((String) contextItems[i], contextItems[i + 1]);
                        i++; // Skip next item as it's the value
                    }
                }
                
                if (!context.isEmpty()) {
                    logEntry.put("context", context);
                }
            }
            
            // Add exception details if present
            if (throwable != null) {
                Map<String, Object> exceptionInfo = new HashMap<>();
                exceptionInfo.put("class", throwable.getClass().getSimpleName());
                exceptionInfo.put("message", throwable.getMessage());
                exceptionInfo.put("stackTrace", getStackTraceAsString(throwable));
                logEntry.put("exception", exceptionInfo);
            }
            
            String jsonLog = objectMapper.writeValueAsString(logEntry);
            
            // Log using appropriate level
            switch (level) {
                case "DEBUG":
                    logger.debug(jsonLog);
                    break;
                case "INFO":
                    logger.info(jsonLog);
                    break;
                case "WARN":
                    logger.warn(jsonLog);
                    break;
                case "ERROR":
                    logger.error(jsonLog);
                    break;
                default:
                    logger.info(jsonLog);
            }
            
        } catch (Exception e) {
            // Fallback to regular logging if JSON serialization fails
            logger.error("Failed to create structured log entry: {}", e.getMessage());
            logger.info("Original message: {}", message);
        }
    }

    private String getStackTraceAsString(Throwable throwable) {
        StringBuilder sb = new StringBuilder();
        sb.append(throwable.toString()).append("\n");
        
        StackTraceElement[] elements = throwable.getStackTrace();
        for (int i = 0; i < Math.min(elements.length, 10); i++) { // Limit to first 10 lines
            sb.append("\tat ").append(elements[i]).append("\n");
        }
        
        if (elements.length > 10) {
            sb.append("\t... ").append(elements.length - 10).append(" more\n");
        }
        
        return sb.toString();
    }

    /**
     * Utility method to set build ID in MDC for all subsequent logs in this thread
     */
    public static void setBuildId(Long buildId) {
        if (buildId != null) {
            MDC.put("buildId", buildId.toString());
        }
    }

    /**
     * Utility method to set task ID in MDC for all subsequent logs in this thread
     */
    public static void setTaskId(Long taskId) {
        if (taskId != null) {
            MDC.put("taskId", taskId.toString());
        }
    }

    /**
     * Utility method to clear build and task IDs from MDC
     */
    public static void clearContext() {
        MDC.remove("buildId");
        MDC.remove("taskId");
    }
}