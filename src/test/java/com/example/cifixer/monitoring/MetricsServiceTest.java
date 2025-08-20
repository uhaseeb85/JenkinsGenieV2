package com.example.cifixer.monitoring;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MetricsServiceTest {

    private MeterRegistry meterRegistry;
    private MetricsService metricsService;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        metricsService = new MetricsService(meterRegistry);
    }

    @Test
    void shouldRecordTaskSuccess() {
        // Given
        Timer.Sample sample = metricsService.startTaskTimer("PLAN");
        
        // When
        metricsService.recordTaskSuccess(sample, "PLAN");
        
        // Then
        Counter successCounter = meterRegistry.find("cifixer.task.success").counter();
        assertThat(successCounter).isNotNull();
        assertThat(successCounter.count()).isEqualTo(1.0);
        
        Timer processingTimer = meterRegistry.find("cifixer.task.processing.duration").timer();
        assertThat(processingTimer).isNotNull();
        assertThat(processingTimer.count()).isEqualTo(1);
    }

    @Test
    void shouldRecordTaskFailure() {
        // When
        metricsService.recordTaskFailure("PLAN", "VALIDATION_ERROR");
        
        // Then
        Counter failureCounter = meterRegistry.find("cifixer.task.failure").counter();
        assertThat(failureCounter).isNotNull();
        assertThat(failureCounter.count()).isEqualTo(1.0);
    }

    @Test
    void shouldRecordTaskRetry() {
        // When
        metricsService.recordTaskRetry("PLAN", 2);
        
        // Then
        Counter retryCounter = meterRegistry.find("cifixer.task.retry").counter();
        assertThat(retryCounter).isNotNull();
        assertThat(retryCounter.count()).isEqualTo(1.0);
    }

    @Test
    void shouldRecordAgentDuration() {
        // Given
        Timer.Sample sample = metricsService.startPlannerAgent();
        
        // When
        metricsService.recordPlannerAgentDuration(sample);
        
        // Then
        Timer plannerTimer = meterRegistry.find("cifixer.agent.planner.duration").timer();
        assertThat(plannerTimer).isNotNull();
        assertThat(plannerTimer.count()).isEqualTo(1);
    }

    @Test
    void shouldRecordBuildProcessing() {
        // Given
        Timer.Sample sample = metricsService.startBuildProcessing();
        
        // When
        metricsService.recordBuildProcessed("test-job", "main");
        metricsService.recordBuildSuccess(sample, "test-job", "main");
        
        // Then
        Counter processedCounter = meterRegistry.find("cifixer.build.processed").counter();
        assertThat(processedCounter).isNotNull();
        assertThat(processedCounter.count()).isEqualTo(1.0);
        
        Counter successCounter = meterRegistry.find("cifixer.build.success").counter();
        assertThat(successCounter).isNotNull();
        assertThat(successCounter.count()).isEqualTo(1.0);
        
        Timer processingTimer = meterRegistry.find("cifixer.build.processing.duration").timer();
        assertThat(processingTimer).isNotNull();
        assertThat(processingTimer.count()).isEqualTo(1);
    }

    @Test
    void shouldRecordBuildFailure() {
        // When
        metricsService.recordBuildFailure("test-job", "main", "COMPILATION_ERROR");
        
        // Then
        Counter failureCounter = meterRegistry.find("cifixer.build.failure").counter();
        assertThat(failureCounter).isNotNull();
        assertThat(failureCounter.count()).isEqualTo(1.0);
    }

    @Test
    void shouldRecordLlmApiMetrics() {
        // Given
        Timer.Sample sample = metricsService.startLlmApiCall();
        
        // When
        metricsService.recordLlmApiSuccess(sample, "openrouter", "claude-3.5-sonnet");
        
        // Then
        Counter successCounter = meterRegistry.find("cifixer.external.llm.success").counter();
        assertThat(successCounter).isNotNull();
        assertThat(successCounter.count()).isEqualTo(1.0);
        
        Timer apiTimer = meterRegistry.find("cifixer.external.llm.duration").timer();
        assertThat(apiTimer).isNotNull();
        assertThat(apiTimer.count()).isEqualTo(1);
    }

    @Test
    void shouldRecordLlmApiFailure() {
        // When
        metricsService.recordLlmApiFailure("openrouter", "claude-3.5-sonnet", "RATE_LIMIT");
        
        // Then
        Counter failureCounter = meterRegistry.find("cifixer.external.llm.failure").counter();
        assertThat(failureCounter).isNotNull();
        assertThat(failureCounter.count()).isEqualTo(1.0);
    }

    @Test
    void shouldRecordGithubApiMetrics() {
        // Given
        Timer.Sample sample = metricsService.startGithubApiCall();
        
        // When
        metricsService.recordGithubApiSuccess(sample, "create_pr");
        
        // Then
        Counter successCounter = meterRegistry.find("cifixer.external.github.success").counter();
        assertThat(successCounter).isNotNull();
        assertThat(successCounter.count()).isEqualTo(1.0);
        
        Timer apiTimer = meterRegistry.find("cifixer.external.github.duration").timer();
        assertThat(apiTimer).isNotNull();
        assertThat(apiTimer.count()).isEqualTo(1);
    }

    @Test
    void shouldRecordGithubApiFailure() {
        // When
        metricsService.recordGithubApiFailure("create_pr", "AUTHENTICATION_ERROR");
        
        // Then
        Counter failureCounter = meterRegistry.find("cifixer.external.github.failure").counter();
        assertThat(failureCounter).isNotNull();
        assertThat(failureCounter.count()).isEqualTo(1.0);
    }

    @Test
    void shouldRecordGaugeMetrics() {
        // When
        metricsService.recordQueueSize("pending", 25);
        metricsService.recordActiveConnections(8);
        metricsService.recordWorkingDirectorySize(1024 * 1024 * 100); // 100MB
        
        // Then
        assertThat(meterRegistry.find("cifixer.queue.size").gauge()).isNotNull();
        assertThat(meterRegistry.find("cifixer.database.connections.active").gauge()).isNotNull();
        assertThat(meterRegistry.find("cifixer.working.directory.size.bytes").gauge()).isNotNull();
    }

    @Test
    void shouldHandleMultipleTaskTypes() {
        // When
        metricsService.recordTaskFailure("PLAN", "PARSING_ERROR");
        metricsService.recordTaskFailure("RETRIEVE", "GIT_ERROR");
        metricsService.recordTaskFailure("PLAN", "TIMEOUT_ERROR");
        
        // Then
        Counter failureCounter = meterRegistry.find("cifixer.task.failure").counter();
        assertThat(failureCounter.count()).isEqualTo(3.0);
        
        // Verify tags are properly set
        assertThat(meterRegistry.find("cifixer.task.failure")
                .tag("task.type", "PLAN")
                .tag("error.type", "PARSING_ERROR")
                .counter().count()).isEqualTo(1.0);
        
        assertThat(meterRegistry.find("cifixer.task.failure")
                .tag("task.type", "PLAN")
                .tag("error.type", "TIMEOUT_ERROR")
                .counter().count()).isEqualTo(1.0);
        
        assertThat(meterRegistry.find("cifixer.task.failure")
                .tag("task.type", "RETRIEVE")
                .tag("error.type", "GIT_ERROR")
                .counter().count()).isEqualTo(1.0);
    }
}