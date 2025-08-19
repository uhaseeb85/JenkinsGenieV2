package com.example.cifixer.agents;

import java.util.List;
import java.util.Map;

/**
 * Payload class for patch operations containing necessary information
 * for generating and applying code fixes.
 */
public class PatchPayload {
    
    private String repoUrl;
    private String branch;
    private String commitSha;
    private Long buildId;
    private String workingDirectory;
    private String projectName;
    private String errorContext;
    private List<CandidateFile> candidateFiles;
    private Map<String, Object> springContext;
    private Map<String, String> credentials;
    
    public PatchPayload() {}
    
    public PatchPayload(Long buildId, String workingDirectory, String errorContext) {
        this.buildId = buildId;
        this.workingDirectory = workingDirectory;
        this.errorContext = errorContext;
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
    
    public String getProjectName() {
        return projectName;
    }
    
    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }
    
    public String getErrorContext() {
        return errorContext;
    }
    
    public void setErrorContext(String errorContext) {
        this.errorContext = errorContext;
    }
    
    public List<CandidateFile> getCandidateFiles() {
        return candidateFiles;
    }
    
    public void setCandidateFiles(List<CandidateFile> candidateFiles) {
        this.candidateFiles = candidateFiles;
    }
    
    public Map<String, Object> getSpringContext() {
        return springContext;
    }
    
    public void setSpringContext(Map<String, Object> springContext) {
        this.springContext = springContext;
    }
    
    public Map<String, String> getCredentials() {
        return credentials;
    }
    
    public void setCredentials(Map<String, String> credentials) {
        this.credentials = credentials;
    }
    
    @Override
    public String toString() {
        return "PatchPayload{" +
                "buildId=" + buildId +
                ", workingDirectory='" + workingDirectory + '\'' +
                ", projectName='" + projectName + '\'' +
                ", errorContext='" + errorContext + '\'' +
                ", candidateFiles=" + (candidateFiles != null ? candidateFiles.size() : 0) +
                '}';
    }
    
    /**
     * Represents a candidate file for patching with its ranking and reason.
     */
    public static class CandidateFile {
        private String filePath;
        private double rankScore;
        private String reason;
        
        public CandidateFile() {}
        
        public CandidateFile(String filePath, double rankScore, String reason) {
            this.filePath = filePath;
            this.rankScore = rankScore;
            this.reason = reason;
        }
        
        public String getFilePath() {
            return filePath;
        }
        
        public void setFilePath(String filePath) {
            this.filePath = filePath;
        }
        
        public double getRankScore() {
            return rankScore;
        }
        
        public void setRankScore(double rankScore) {
            this.rankScore = rankScore;
        }
        
        public String getReason() {
            return reason;
        }
        
        public void setReason(String reason) {
            this.reason = reason;
        }
        
        @Override
        public String toString() {
            return "CandidateFile{" +
                    "filePath='" + filePath + '\'' +
                    ", rankScore=" + rankScore +
                    ", reason='" + reason + '\'' +
                    '}';
        }
    }
}