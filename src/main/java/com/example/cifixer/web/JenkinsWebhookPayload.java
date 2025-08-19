package com.example.cifixer.web;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * Represents the payload received from Jenkins webhook when a build fails.
 */
public class JenkinsWebhookPayload {
    
    @JsonProperty("job")
    private String job;
    
    @JsonProperty("build_number")
    private Integer buildNumber;
    
    @JsonProperty("branch")
    private String branch;
    
    @JsonProperty("repo_url")
    private String repoUrl;
    
    @JsonProperty("commit_sha")
    private String commitSha;
    
    @JsonProperty("build_logs")
    private String buildLogs;
    
    @JsonProperty("metadata")
    private Map<String, Object> metadata;
    
    public JenkinsWebhookPayload() {
    }
    
    public String getJob() {
        return job;
    }
    
    public void setJob(String job) {
        this.job = job;
    }
    
    public Integer getBuildNumber() {
        return buildNumber;
    }
    
    public void setBuildNumber(Integer buildNumber) {
        this.buildNumber = buildNumber;
    }
    
    public String getBranch() {
        return branch;
    }
    
    public void setBranch(String branch) {
        this.branch = branch;
    }
    
    public String getRepoUrl() {
        return repoUrl;
    }
    
    public void setRepoUrl(String repoUrl) {
        this.repoUrl = repoUrl;
    }
    
    public String getCommitSha() {
        return commitSha;
    }
    
    public void setCommitSha(String commitSha) {
        this.commitSha = commitSha;
    }
    
    public String getBuildLogs() {
        return buildLogs;
    }
    
    public void setBuildLogs(String buildLogs) {
        this.buildLogs = buildLogs;
    }
    
    public Map<String, Object> getMetadata() {
        return metadata;
    }
    
    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }
    
    @Override
    public String toString() {
        return "JenkinsWebhookPayload{" +
                "job='" + job + '\'' +
                ", buildNumber=" + buildNumber +
                ", branch='" + branch + '\'' +
                ", repoUrl='" + repoUrl + '\'' +
                ", commitSha='" + commitSha + '\'' +
                '}';
    }
}