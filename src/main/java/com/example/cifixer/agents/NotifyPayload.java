package com.example.cifixer.agents;

import com.example.cifixer.store.NotificationType;

import java.util.List;

/**
 * Payload for notification tasks containing all necessary information
 * to send notifications to stakeholders about build fix results.
 */
public class NotifyPayload {
    
    private NotificationType notificationType;
    private List<String> recipients;
    private String prUrl;
    private Integer prNumber;
    private String branchName;
    private String commitSha;
    private String planSummary;
    private String validationResults;
    private String errorMessage;
    private List<String> patchedFiles;
    
    public NotifyPayload() {
    }
    
    public NotifyPayload(NotificationType notificationType, List<String> recipients) {
        this.notificationType = notificationType;
        this.recipients = recipients;
    }
    
    // Getters and Setters
    public NotificationType getNotificationType() {
        return notificationType;
    }
    
    public void setNotificationType(NotificationType notificationType) {
        this.notificationType = notificationType;
    }
    
    public List<String> getRecipients() {
        return recipients;
    }
    
    public void setRecipients(List<String> recipients) {
        this.recipients = recipients;
    }
    
    public String getPrUrl() {
        return prUrl;
    }
    
    public void setPrUrl(String prUrl) {
        this.prUrl = prUrl;
    }
    
    public Integer getPrNumber() {
        return prNumber;
    }
    
    public void setPrNumber(Integer prNumber) {
        this.prNumber = prNumber;
    }
    
    public String getBranchName() {
        return branchName;
    }
    
    public void setBranchName(String branchName) {
        this.branchName = branchName;
    }
    
    public String getCommitSha() {
        return commitSha;
    }
    
    public void setCommitSha(String commitSha) {
        this.commitSha = commitSha;
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
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
    
    public List<String> getPatchedFiles() {
        return patchedFiles;
    }
    
    public void setPatchedFiles(List<String> patchedFiles) {
        this.patchedFiles = patchedFiles;
    }
    
    @Override
    public String toString() {
        return "NotifyPayload{" +
                "notificationType=" + notificationType +
                ", recipients=" + recipients +
                ", prUrl='" + prUrl + '\'' +
                ", prNumber=" + prNumber +
                ", branchName='" + branchName + '\'' +
                ", commitSha='" + commitSha + '\'' +
                '}';
    }
}