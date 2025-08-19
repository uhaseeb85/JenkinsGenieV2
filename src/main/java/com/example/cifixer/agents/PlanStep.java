package com.example.cifixer.agents;

import java.util.List;

/**
 * Represents a single step in a fix plan with Spring-specific recommendations.
 */
public class PlanStep {
    
    private String description;
    private String action;
    private String targetFile;
    private List<String> springComponents;
    private List<String> dependencies;
    private String reasoning;
    private int priority;
    
    public PlanStep() {}
    
    public PlanStep(String description, String action) {
        this.description = description;
        this.action = action;
    }
    
    // Getters and Setters
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public String getAction() {
        return action;
    }
    
    public void setAction(String action) {
        this.action = action;
    }
    
    public String getTargetFile() {
        return targetFile;
    }
    
    public void setTargetFile(String targetFile) {
        this.targetFile = targetFile;
    }
    
    public List<String> getSpringComponents() {
        return springComponents;
    }
    
    public void setSpringComponents(List<String> springComponents) {
        this.springComponents = springComponents;
    }
    
    public List<String> getDependencies() {
        return dependencies;
    }
    
    public void setDependencies(List<String> dependencies) {
        this.dependencies = dependencies;
    }
    
    public String getReasoning() {
        return reasoning;
    }
    
    public void setReasoning(String reasoning) {
        this.reasoning = reasoning;
    }
    
    public int getPriority() {
        return priority;
    }
    
    public void setPriority(int priority) {
        this.priority = priority;
    }
    
    @Override
    public String toString() {
        return "PlanStep{" +
                "description='" + description + '\'' +
                ", action='" + action + '\'' +
                ", targetFile='" + targetFile + '\'' +
                ", priority=" + priority +
                '}';
    }
}