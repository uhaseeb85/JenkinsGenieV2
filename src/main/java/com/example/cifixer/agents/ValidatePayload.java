package com.example.cifixer.agents;

import java.util.Map;

/**
 * Payload for validation tasks containing information needed to validate fixes.
 */
public class ValidatePayload {
    private String workingDirectory;
    private String buildTool;
    private String springBootVersion;
    private Map<String, Object> context;
    
    public ValidatePayload() {
    }
    
    public ValidatePayload(String workingDirectory, String buildTool) {
        this.workingDirectory = workingDirectory;
        this.buildTool = buildTool;
    }
    
    public ValidatePayload(String workingDirectory, String buildTool, String springBootVersion, Map<String, Object> context) {
        this.workingDirectory = workingDirectory;
        this.buildTool = buildTool;
        this.springBootVersion = springBootVersion;
        this.context = context;
    }
    
    public String getWorkingDirectory() {
        return workingDirectory;
    }
    
    public void setWorkingDirectory(String workingDirectory) {
        this.workingDirectory = workingDirectory;
    }
    
    public String getBuildTool() {
        return buildTool;
    }
    
    public void setBuildTool(String buildTool) {
        this.buildTool = buildTool;
    }
    
    public String getSpringBootVersion() {
        return springBootVersion;
    }
    
    public void setSpringBootVersion(String springBootVersion) {
        this.springBootVersion = springBootVersion;
    }
    
    public Map<String, Object> getContext() {
        return context;
    }
    
    public void setContext(Map<String, Object> context) {
        this.context = context;
    }
    
    @Override
    public String toString() {
        return "ValidatePayload{" +
                "workingDirectory='" + workingDirectory + '\'' +
                ", buildTool='" + buildTool + '\'' +
                ", springBootVersion='" + springBootVersion + '\'' +
                ", context=" + context +
                '}';
    }
}