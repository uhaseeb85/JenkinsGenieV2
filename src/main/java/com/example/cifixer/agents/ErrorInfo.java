package com.example.cifixer.agents;

/**
 * Represents information about a build error extracted from logs.
 */
public class ErrorInfo {
    
    private ErrorType errorType;
    private String filePath;
    private Integer lineNumber;
    private String errorMessage;
    private String stackTrace;
    private String missingBean;
    private String missingDependency;
    private String failedTest;
    private String mavenModule;
    
    public ErrorInfo() {}
    
    public ErrorInfo(ErrorType errorType, String errorMessage) {
        this.errorType = errorType;
        this.errorMessage = errorMessage;
    }
    
    // Getters and Setters
    public ErrorType getErrorType() {
        return errorType;
    }
    
    public void setErrorType(ErrorType errorType) {
        this.errorType = errorType;
    }
    
    public String getFilePath() {
        return filePath;
    }
    
    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }
    
    public Integer getLineNumber() {
        return lineNumber;
    }
    
    public void setLineNumber(Integer lineNumber) {
        this.lineNumber = lineNumber;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
    
    public String getStackTrace() {
        return stackTrace;
    }
    
    public void setStackTrace(String stackTrace) {
        this.stackTrace = stackTrace;
    }
    
    public String getMissingBean() {
        return missingBean;
    }
    
    public void setMissingBean(String missingBean) {
        this.missingBean = missingBean;
    }
    
    public String getMissingDependency() {
        return missingDependency;
    }
    
    public void setMissingDependency(String missingDependency) {
        this.missingDependency = missingDependency;
    }
    
    public String getFailedTest() {
        return failedTest;
    }
    
    public void setFailedTest(String failedTest) {
        this.failedTest = failedTest;
    }
    
    public String getMavenModule() {
        return mavenModule;
    }
    
    public void setMavenModule(String mavenModule) {
        this.mavenModule = mavenModule;
    }
    
    @Override
    public String toString() {
        return "ErrorInfo{" +
                "errorType=" + errorType +
                ", filePath='" + filePath + '\'' +
                ", lineNumber=" + lineNumber +
                ", errorMessage='" + errorMessage + '\'' +
                '}';
    }
}