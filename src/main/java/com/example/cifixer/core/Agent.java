package com.example.cifixer.core;

/**
 * Base interface for all agents in the multi-agent CI fixer system.
 * Each agent is responsible for a specific domain of the CI fixing process.
 *
 * @param <T> The type of payload this agent processes
 */
public interface Agent<T> {
    
    /**
     * Handles a task with the given payload.
     *
     * @param task The task to process
     * @param payload The payload containing task-specific data
     * @return The result of processing the task
     */
    TaskResult handle(Task task, T payload);
}