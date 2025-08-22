package com.example.cifixer.agents;

/**
 * Result of build validation containing success status, build logs, 
 * and error information for Maven compilation attempts.
 */
public class BuildValidationResult {
    private boolean success;
    private String buildLogs;
    private String errorMessage;
    private int exitCode;
    private String compilationErrors; // Specific compilation error details
    
    public BuildValidationResult() {}
    
    public BuildValidationResult(boolean success, int exitCode, String buildLogs, String errorMessage) {
        this.success = success;
        this.exitCode = exitCode;
        this.buildLogs = buildLogs;
        this.errorMessage = errorMessage;
    }
    
    /**
     * Static builder method for fluent construction.
     */
    public static BuildValidationResultBuilder builder() {
        return new BuildValidationResultBuilder();
    }
    
    // Getters and Setters
    public boolean isSuccess() {
        return success;
    }
    
    public void setSuccess(boolean success) {
        this.success = success;
    }
    
    public String getBuildLogs() {
        return buildLogs;
    }
    
    public void setBuildLogs(String buildLogs) {
        this.buildLogs = buildLogs;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
    
    public int getExitCode() {
        return exitCode;
    }
    
    public void setExitCode(int exitCode) {
        this.exitCode = exitCode;
    }
    
    public String getCompilationErrors() {
        return compilationErrors;
    }
    
    public void setCompilationErrors(String compilationErrors) {
        this.compilationErrors = compilationErrors;
    }
    
    @Override
    public String toString() {
        return "BuildValidationResult{" +
                "success=" + success +
                ", exitCode=" + exitCode +
                ", buildLogsLength=" + (buildLogs != null ? buildLogs.length() : 0) +
                ", errorMessage='" + errorMessage + '\'' +
                ", compilationErrorsLength=" + (compilationErrors != null ? compilationErrors.length() : 0) +
                '}';
    }
    
    /**
     * Builder class for BuildValidationResult creation.
     */
    public static class BuildValidationResultBuilder {
        private boolean success;
        private String buildLogs;
        private String errorMessage;
        private int exitCode;
        private String compilationErrors;
        
        public BuildValidationResultBuilder success(boolean success) {
            this.success = success;
            return this;
        }
        
        public BuildValidationResultBuilder buildLogs(String buildLogs) {
            this.buildLogs = buildLogs;
            return this;
        }
        
        public BuildValidationResultBuilder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }
        
        public BuildValidationResultBuilder exitCode(int exitCode) {
            this.exitCode = exitCode;
            return this;
        }
        
        public BuildValidationResultBuilder compilationErrors(String compilationErrors) {
            this.compilationErrors = compilationErrors;
            return this;
        }
        
        public BuildValidationResult build() {
            BuildValidationResult result = new BuildValidationResult();
            result.setSuccess(success);
            result.setBuildLogs(buildLogs);
            result.setErrorMessage(errorMessage);
            result.setExitCode(exitCode);
            result.setCompilationErrors(compilationErrors);
            return result;
        }
    }
}
