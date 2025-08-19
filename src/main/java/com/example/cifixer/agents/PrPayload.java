package com.example.cifixer.agents;

import java.util.List;

/**
 * Payload for PR creation tasks containing all necessary information
 * to create a GitHub pull request with the generated fixes.
 */
public class PrPayload {
    
    private String repoUrl;
    private String branchName;
    private String baseBranch;
    private String commitSha;
    private List<String> patchedFiles;
    private String planSummary;
    private String validationResults;
    
    public PrPayload() {
    }
    
    public PrPayload(String repoUrl, String branchName, String baseBranch, String commitSha) {
        this.repoUrl = repoUrl;
        this.branchName = branchName;
        this.baseBranch = baseBranch;
        this.commitSha = commitSha;
    }
    
    // Getters and Setters
    public String getRepoUrl() {
        return repoUrl;
    }
    
    public void setRepoUrl(String repoUrl) {
        this.repoUrl = repoUrl;
    }
    
    public String getBranchName() {
        return branchName;
    }
    
    public void setBranchName(String branchName) {
        this.branchName = branchName;
    }
    
    public String getBaseBranch() {
        return baseBranch;
    }
    
    public void setBaseBranch(String baseBranch) {
        this.baseBranch = baseBranch;
    }
    
    public String getCommitSha() {
        return commitSha;
    }
    
    public void setCommitSha(String commitSha) {
        this.commitSha = commitSha;
    }
    
    public List<String> getPatchedFiles() {
        return patchedFiles;
    }
    
    public void setPatchedFiles(List<String> patchedFiles) {
        this.patchedFiles = patchedFiles;
    }
    
    public String getPlanSummary() {
        return planSummary;
    }
    
    public void setPlanSummary(String planSummary) {
        this.planSummary = planSummary;
    }
    
    public String getValidationResults() {
        return validationResults;
    }
    
    public void setValidationResults(String validationResults) {
        this.validationResults = validationResults;
    }
    
    @Override
    public String toString() {
        return "PrPayload{" +
                "repoUrl='" + repoUrl + '\'' +
                ", branchName='" + branchName + '\'' +
                ", baseBranch='" + baseBranch + '\'' +
                ", commitSha='" + commitSha + '\'' +
                ", patchedFiles=" + patchedFiles +
                '}';
    }
}