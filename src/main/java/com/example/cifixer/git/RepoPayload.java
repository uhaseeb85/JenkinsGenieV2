package com.example.cifixer.git;

import java.util.Map;

/**
 * Payload class for repository operations containing necessary information
 * for cloning, branching, and working directory management.
 */
public class RepoPayload {
    
    private String repoUrl;
    private String branch;
    private String commitSha;
    private Long buildId;
    private String workingDirectory;
    private Map<String, String> credentials;
    
    public RepoPayload() {}
    
    public RepoPayload(String repoUrl, String branch, String commitSha, Long buildId) {
        this.repoUrl = repoUrl;
        this.branch = branch;
        this.commitSha = commitSha;
        this.buildId = buildId;
    }
    
    // Getters and Setters
    public String getRepoUrl() {
        return repoUrl;
    }
    
    public void setRepoUrl(String repoUrl) {
        this.repoUrl = repoUrl;
    }
    
    public String getBranch() {
        return branch;
    }
    
    public void setBranch(String branch) {
        this.branch = branch;
    }
    
    public String getCommitSha() {
        return commitSha;
    }
    
    public void setCommitSha(String commitSha) {
        this.commitSha = commitSha;
    }
    
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
    
    public Map<String, String> getCredentials() {
        return credentials;
    }
    
    public void setCredentials(Map<String, String> credentials) {
        this.credentials = credentials;
    }
    
    @Override
    public String toString() {
        return "RepoPayload{" +
                "repoUrl='" + repoUrl + '\'' +
                ", branch='" + branch + '\'' +
                ", commitSha='" + commitSha + '\'' +
                ", buildId=" + buildId +
                ", workingDirectory='" + workingDirectory + '\'' +
                '}';
    }
}