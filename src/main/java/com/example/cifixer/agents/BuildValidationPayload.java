package com.example.cifixer.agents;

import java.util.List;

/**
 * Payload for build validation tasks containing information needed to validate 
 * code fixes using Maven build commands.
 */
public class BuildValidationPayload {
    private Long buildId;
    private String workingDirectory;
    private List<String> fixedFiles;
    private String commitSha;
    private BuildValidationResult result;
    private String buildOutput;
    private String errorOutput;
    private boolean isRetry;              // Track if this is a retry attempt
    private String previousFailureReason; // Context for LLM retry
    
    public BuildValidationPayload() {}
    
    public BuildValidationPayload(Long buildId, String workingDirectory, List<String> fixedFiles, String commitSha) {
        this.buildId = buildId;
        this.workingDirectory = workingDirectory;
        this.fixedFiles = fixedFiles;
        this.commitSha = commitSha;
        this.isRetry = false;
    }
    
    // Getters and Setters
    public Long getBuildId() {
        return buildId;
    }
    
    public void setBuildId(Long buildId) {
        this.buildId = buildId;
    }
    
    public String getWorkingDirectory() {
        return workingDirectory;
    }
    
    public void setWorkingDirectory(String workingDirectory) {
        this.workingDirectory = workingDirectory;
    }
    
    public List<String> getFixedFiles() {
        return fixedFiles;
    }
    
    public void setFixedFiles(List<String> fixedFiles) {
        this.fixedFiles = fixedFiles;
    }
    
    public String getCommitSha() {
        return commitSha;
    }
    
    public void setCommitSha(String commitSha) {
        this.commitSha = commitSha;
    }
    
    public BuildValidationResult getResult() {
        return result;
    }
    
    public void setResult(BuildValidationResult result) {
        this.result = result;
    }
    
    public String getBuildOutput() {
        return buildOutput;
    }
    
    public void setBuildOutput(String buildOutput) {
        this.buildOutput = buildOutput;
    }
    
    public String getErrorOutput() {
        return errorOutput;
    }
    
    public void setErrorOutput(String errorOutput) {
        this.errorOutput = errorOutput;
    }
    
    public boolean isRetry() {
        return isRetry;
    }
    
    public void setRetry(boolean retry) {
        isRetry = retry;
    }
    
    public String getPreviousFailureReason() {
        return previousFailureReason;
    }
    
    public void setPreviousFailureReason(String previousFailureReason) {
        this.previousFailureReason = previousFailureReason;
    }
    
    @Override
    public String toString() {
        return "BuildValidationPayload{" +
                "buildId=" + buildId +
                ", workingDirectory='" + workingDirectory + '\'' +
                ", fixedFiles=" + (fixedFiles != null ? fixedFiles.size() : 0) +
                ", commitSha='" + commitSha + '\'' +
                ", isRetry=" + isRetry +
                ", result=" + (result != null ? result.isSuccess() : "null") +
                '}';
    }
}
