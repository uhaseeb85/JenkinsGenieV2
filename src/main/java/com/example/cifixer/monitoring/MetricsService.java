package com.example.cifixer.monitoring;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Service for recording custom metrics related to task processing and system operations.
 */
@Service
public class MetricsService {

    private final MeterRegistry meterRegistry;
    
    // Task processing metrics
    private final Timer taskProcessingTimer;
    private final Counter taskSuccessCounter;
    private final Counter taskFailureCounter;
    private final Counter taskRetryCounter;
    
    // Agent-specific metrics
    private final Timer plannerAgentTimer;
    private final Timer retrieverAgentTimer;
    private final Timer codeFixAgentTimer;
    private final Timer validatorAgentTimer;
    private final Timer prAgentTimer;
    private final Timer notificationAgentTimer;
    
    // Build processing metrics
    private final Counter buildProcessedCounter;
    private final Counter buildSuccessCounter;
    private final Counter buildFailureCounter;
    private final Timer buildProcessingTimer;
    
    // External API metrics
    private final Timer llmApiTimer;
    private final Counter llmApiSuccessCounter;
    private final Counter llmApiFailureCounter;
    private final Timer githubApiTimer;
    private final Counter githubApiSuccessCounter;
    private final Counter githubApiFailureCounter;

    public MetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        
        // Initialize task processing metrics
        this.taskProcessingTimer = Timer.builder("cifixer.task.processing.duration")
                .description("Time taken to process tasks")
                .register(meterRegistry);
        
        this.taskSuccessCounter = Counter.builder("cifixer.task.success")
                .description("Number of successfully processed tasks")
                .register(meterRegistry);
        
        this.taskFailureCounter = Counter.builder("cifixer.task.failure")
                .description("Number of failed tasks")
                .register(meterRegistry);
        
        this.taskRetryCounter = Counter.builder("cifixer.task.retry")
                .description("Number of task retries")
                .register(meterRegistry);
        
        // Initialize agent-specific metrics
        this.plannerAgentTimer = Timer.builder("cifixer.agent.planner.duration")
                .description("Time taken by planner agent")
                .register(meterRegistry);
        
        this.retrieverAgentTimer = Timer.builder("cifixer.agent.retriever.duration")
                .description("Time taken by retriever agent")
                .register(meterRegistry);
        
        this.codeFixAgentTimer = Timer.builder("cifixer.agent.codefix.duration")
                .description("Time taken by code-fix agent")
                .register(meterRegistry);
        
        this.validatorAgentTimer = Timer.builder("cifixer.agent.validator.duration")
                .description("Time taken by validator agent")
                .register(meterRegistry);
        
        this.prAgentTimer = Timer.builder("cifixer.agent.pr.duration")
                .description("Time taken by PR agent")
                .register(meterRegistry);
        
        this.notificationAgentTimer = Timer.builder("cifixer.agent.notification.duration")
                .description("Time taken by notification agent")
                .register(meterRegistry);
        
        // Initialize build processing metrics
        this.buildProcessedCounter = Counter.builder("cifixer.build.processed")
                .description("Number of builds processed")
                .register(meterRegistry);
        
        this.buildSuccessCounter = Counter.builder("cifixer.build.success")
                .description("Number of successfully processed builds")
                .register(meterRegistry);
        
        this.buildFailureCounter = Counter.builder("cifixer.build.failure")
                .description("Number of failed builds")
                .register(meterRegistry);
        
        this.buildProcessingTimer = Timer.builder("cifixer.build.processing.duration")
                .description("Total time to process a build from start to finish")
                .register(meterRegistry);
        
        // Initialize external API metrics
        this.llmApiTimer = Timer.builder("cifixer.external.llm.duration")
                .description("Time taken for LLM API calls")
                .register(meterRegistry);
        
        this.llmApiSuccessCounter = Counter.builder("cifixer.external.llm.success")
                .description("Number of successful LLM API calls")
                .register(meterRegistry);
        
        this.llmApiFailureCounter = Counter.builder("cifixer.external.llm.failure")
                .description("Number of failed LLM API calls")
                .register(meterRegistry);
        
        this.githubApiTimer = Timer.builder("cifixer.external.github.duration")
                .description("Time taken for GitHub API calls")
                .register(meterRegistry);
        
        this.githubApiSuccessCounter = Counter.builder("cifixer.external.github.success")
                .description("Number of successful GitHub API calls")
                .register(meterRegistry);
        
        this.githubApiFailureCounter = Counter.builder("cifixer.external.github.failure")
                .description("Number of failed GitHub API calls")
                .register(meterRegistry);
    }

    // Task processing metrics
    public Timer.Sample startTaskTimer(String taskType) {
        return Timer.start(meterRegistry);
    }

    public void recordTaskSuccess(Timer.Sample sample, String taskType) {
        sample.stop(Timer.builder("cifixer.task.processing.duration")
                .tags(Tags.of("task.type", taskType))
                .register(meterRegistry));
        Counter.builder("cifixer.task.success")
                .tags(Tags.of("task.type", taskType))
                .register(meterRegistry)
                .increment();
    }

    public void recordTaskFailure(String taskType, String errorType) {
        Counter.builder("cifixer.task.failure")
                .tags(Tags.of("task.type", taskType, "error.type", errorType))
                .register(meterRegistry)
                .increment();
    }

    public void recordTaskRetry(String taskType, int attemptNumber) {
        Counter.builder("cifixer.task.retry")
                .tags(Tags.of("task.type", taskType, "attempt", String.valueOf(attemptNumber)))
                .register(meterRegistry)
                .increment();
    }

    // Agent-specific metrics
    public Timer.Sample startPlannerAgent() {
        return Timer.start(meterRegistry);
    }

    public void recordPlannerAgentDuration(Timer.Sample sample) {
        sample.stop(plannerAgentTimer);
    }

    public Timer.Sample startRetrieverAgent() {
        return Timer.start(meterRegistry);
    }

    public void recordRetrieverAgentDuration(Timer.Sample sample) {
        sample.stop(retrieverAgentTimer);
    }

    public Timer.Sample startCodeFixAgent() {
        return Timer.start(meterRegistry);
    }

    public void recordCodeFixAgentDuration(Timer.Sample sample) {
        sample.stop(codeFixAgentTimer);
    }

    public Timer.Sample startValidatorAgent() {
        return Timer.start(meterRegistry);
    }

    public void recordValidatorAgentDuration(Timer.Sample sample) {
        sample.stop(validatorAgentTimer);
    }

    public Timer.Sample startPrAgent() {
        return Timer.start(meterRegistry);
    }

    public void recordPrAgentDuration(Timer.Sample sample) {
        sample.stop(prAgentTimer);
    }

    public Timer.Sample startNotificationAgent() {
        return Timer.start(meterRegistry);
    }

    public void recordNotificationAgentDuration(Timer.Sample sample) {
        sample.stop(notificationAgentTimer);
    }

    // Build processing metrics
    public void recordBuildProcessed(String jobName, String branch) {
        Counter.builder("cifixer.build.processed")
                .tags(Tags.of("job", jobName, "branch", branch))
                .register(meterRegistry)
                .increment();
    }

    public Timer.Sample startBuildProcessing() {
        return Timer.start(meterRegistry);
    }

    public void recordBuildSuccess(Timer.Sample sample, String jobName, String branch) {
        sample.stop(Timer.builder("cifixer.build.processing.duration")
                .tags(Tags.of("job", jobName, "branch", branch))
                .register(meterRegistry));
        Counter.builder("cifixer.build.success")
                .tags(Tags.of("job", jobName, "branch", branch))
                .register(meterRegistry)
                .increment();
    }

    public void recordBuildFailure(String jobName, String branch, String errorType) {
        Counter.builder("cifixer.build.failure")
                .tags(Tags.of("job", jobName, "branch", branch, "error.type", errorType))
                .register(meterRegistry)
                .increment();
    }

    // External API metrics
    public Timer.Sample startLlmApiCall() {
        return Timer.start(meterRegistry);
    }

    public void recordLlmApiSuccess(Timer.Sample sample, String provider, String model) {
        sample.stop(Timer.builder("cifixer.external.llm.duration")
                .tags(Tags.of("provider", provider, "model", model))
                .register(meterRegistry));
        Counter.builder("cifixer.external.llm.success")
                .tags(Tags.of("provider", provider, "model", model))
                .register(meterRegistry)
                .increment();
    }

    public void recordLlmApiFailure(String provider, String model, String errorType) {
        Counter.builder("cifixer.external.llm.failure")
                .tags(Tags.of("provider", provider, "model", model, "error.type", errorType))
                .register(meterRegistry)
                .increment();
    }

    public Timer.Sample startGithubApiCall() {
        return Timer.start(meterRegistry);
    }

    public void recordGithubApiSuccess(Timer.Sample sample, String operation) {
        sample.stop(Timer.builder("cifixer.external.github.duration")
                .tags(Tags.of("operation", operation))
                .register(meterRegistry));
        Counter.builder("cifixer.external.github.success")
                .tags(Tags.of("operation", operation))
                .register(meterRegistry)
                .increment();
    }

    public void recordGithubApiFailure(String operation, String errorType) {
        Counter.builder("cifixer.external.github.failure")
                .tags(Tags.of("operation", operation, "error.type", errorType))
                .register(meterRegistry)
                .increment();
    }

    // Custom gauge metrics
    public void recordQueueSize(String queueType, int size) {
        Gauge.builder("cifixer.queue.size", () -> size)
                .tags(Tags.of("queue.type", queueType))
                .register(meterRegistry);
    }

    public void recordActiveConnections(int count) {
        Gauge.builder("cifixer.database.connections.active", () -> count)
                .register(meterRegistry);
    }

    public void recordWorkingDirectorySize(long sizeBytes) {
        Gauge.builder("cifixer.working.directory.size.bytes", () -> sizeBytes)
                .register(meterRegistry);
    }
}