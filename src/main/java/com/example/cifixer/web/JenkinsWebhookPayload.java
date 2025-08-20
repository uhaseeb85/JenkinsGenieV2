package com.example.cifixer.web;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Objects;

/**
 * DTO for Jenkins webhook payload with nested build and SCM data structures.
 * Uses @JsonIgnoreProperties for flexibility with different Jenkins webhook formats.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class JenkinsWebhookPayload {
    
    private String name;
    
    @JsonProperty("jobName")
    private String jobName;
    
    private BuildData build;
    
    private String status;
    
    @JsonProperty("displayName")
    private String displayName;
    
    @JsonProperty("fullDisplayName")
    private String fullDisplayName;
    
    private String url;
    
    public JenkinsWebhookPayload() {
    }
    
    public JenkinsWebhookPayload(String name, String jobName, BuildData build, String status, 
                                String displayName, String fullDisplayName, String url) {
        this.name = name;
        this.jobName = jobName;
        this.build = build;
        this.status = status;
        this.displayName = displayName;
        this.fullDisplayName = fullDisplayName;
        this.url = url;
    }
    
    // Getters and Setters
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getJobName() {
        return jobName;
    }
    
    public void setJobName(String jobName) {
        this.jobName = jobName;
    }
    
    public BuildData getBuild() {
        return build;
    }
    
    public void setBuild(BuildData build) {
        this.build = build;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }
    
    public String getFullDisplayName() {
        return fullDisplayName;
    }
    
    public void setFullDisplayName(String fullDisplayName) {
        this.fullDisplayName = fullDisplayName;
    }
    
    public String getUrl() {
        return url;
    }
    
    public void setUrl(String url) {
        this.url = url;
    }
    
    /**
     * Nested class for build-specific data from Jenkins webhook
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BuildData {
        
        private Integer number;
        
        private String status;
        
        @JsonProperty("result")
        private String result;
        
        private Long timestamp;
        
        private Integer duration;
        
        private String url;
        
        @JsonProperty("fullUrl")
        private String fullUrl;
        
        private String log;
        
        private String error;
        
        @JsonProperty("scm")
        private ScmData scm;
        
        private Map<String, String> parameters;
        
        @JsonProperty("causes")
        private List<CauseData> causes;
        
        @JsonProperty("artifacts")
        @JsonIgnoreProperties(ignoreUnknown = true)
        private JsonNode artifacts;
        
        public BuildData() {
        }
        
        public BuildData(Integer number, String status, String result, Long timestamp, 
                        Integer duration, String url, String fullUrl, String log, String error,
                        ScmData scm, Map<String, String> parameters, List<CauseData> causes,
                        JsonNode artifacts) {
            this.number = number;
            this.status = status;
            this.result = result;
            this.timestamp = timestamp;
            this.duration = duration;
            this.url = url;
            this.fullUrl = fullUrl;
            this.log = log;
            this.error = error;
            this.scm = scm;
            this.parameters = parameters;
            this.causes = causes;
            this.artifacts = artifacts;
        }
        
        // Getters and Setters
        public Integer getNumber() {
            return number;
        }
        
        public void setNumber(Integer number) {
            this.number = number;
        }
        
        public String getStatus() {
            return status;
        }
        
        public void setStatus(String status) {
            this.status = status;
        }
        
        public String getResult() {
            return result;
        }
        
        public void setResult(String result) {
            this.result = result;
        }
        
        public Long getTimestamp() {
            return timestamp;
        }
        
        public void setTimestamp(Long timestamp) {
            this.timestamp = timestamp;
        }
        
        public Integer getDuration() {
            return duration;
        }
        
        public void setDuration(Integer duration) {
            this.duration = duration;
        }
        
        public String getUrl() {
            return url;
        }
        
        public void setUrl(String url) {
            this.url = url;
        }
        
        public String getFullUrl() {
            return fullUrl;
        }
        
        public void setFullUrl(String fullUrl) {
            this.fullUrl = fullUrl;
        }
        
        public String getLog() {
            return log;
        }
        
        public void setLog(String log) {
            this.log = log;
        }
        
        public String getError() {
            return error;
        }
        
        public void setError(String error) {
            this.error = error;
        }
        
        public ScmData getScm() {
            return scm;
        }
        
        public void setScm(ScmData scm) {
            this.scm = scm;
        }
        
        public Map<String, String> getParameters() {
            return parameters;
        }
        
        public void setParameters(Map<String, String> parameters) {
            this.parameters = parameters;
        }
        
        public List<CauseData> getCauses() {
            return causes;
        }
        
        public void setCauses(List<CauseData> causes) {
            this.causes = causes;
        }
        
        public JsonNode getArtifacts() {
            return artifacts;
        }
        
        public void setArtifacts(JsonNode artifacts) {
            this.artifacts = artifacts;
        }
        
        /**
         * Nested class for SCM (Source Control Management) data
         */
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class ScmData {
            
            private String branch;
            
            @JsonProperty("commit")
            private String commit;
            
            @JsonProperty("commitId")
            private String commitId;
            
            @JsonProperty("message")
            private String message;
            
            @JsonProperty("commitMessage")
            private String commitMessage;
            
            private String author;
            
            @JsonProperty("authorName")
            private String authorName;
            
            @JsonProperty("authorEmail")
            private String authorEmail;
            
            @JsonProperty("branches")
            private List<BranchData> branches;
            
            @JsonProperty("remoteUrls")
            private List<String> remoteUrls;
            
            public ScmData() {
            }
            
            public ScmData(String branch, String commit, String commitId, String message,
                          String commitMessage, String author, String authorName, 
                          String authorEmail, List<BranchData> branches, List<String> remoteUrls) {
                this.branch = branch;
                this.commit = commit;
                this.commitId = commitId;
                this.message = message;
                this.commitMessage = commitMessage;
                this.author = author;
                this.authorName = authorName;
                this.authorEmail = authorEmail;
                this.branches = branches;
                this.remoteUrls = remoteUrls;
            }
            
            // Getters and Setters
            public String getBranch() {
                return branch;
            }
            
            public void setBranch(String branch) {
                this.branch = branch;
            }
            
            public String getCommit() {
                return commit;
            }
            
            public void setCommit(String commit) {
                this.commit = commit;
            }
            
            public String getCommitId() {
                return commitId;
            }
            
            public void setCommitId(String commitId) {
                this.commitId = commitId;
            }
            
            public String getMessage() {
                return message;
            }
            
            public void setMessage(String message) {
                this.message = message;
            }
            
            public String getCommitMessage() {
                return commitMessage;
            }
            
            public void setCommitMessage(String commitMessage) {
                this.commitMessage = commitMessage;
            }
            
            public String getAuthor() {
                return author;
            }
            
            public void setAuthor(String author) {
                this.author = author;
            }
            
            public String getAuthorName() {
                return authorName;
            }
            
            public void setAuthorName(String authorName) {
                this.authorName = authorName;
            }
            
            public String getAuthorEmail() {
                return authorEmail;
            }
            
            public void setAuthorEmail(String authorEmail) {
                this.authorEmail = authorEmail;
            }
            
            public List<BranchData> getBranches() {
                return branches;
            }
            
            public void setBranches(List<BranchData> branches) {
                this.branches = branches;
            }
            
            public List<String> getRemoteUrls() {
                return remoteUrls;
            }
            
            public void setRemoteUrls(List<String> remoteUrls) {
                this.remoteUrls = remoteUrls;
            }
            
            /**
             * Nested class for branch data
             */
            @JsonIgnoreProperties(ignoreUnknown = true)
            public static class BranchData {
                
                private String name;
                
                @JsonProperty("SHA1")
                private String sha1;
                
                @JsonProperty("commit")
                private String commit;
                
                public BranchData() {
                }
                
                public BranchData(String name, String sha1, String commit) {
                    this.name = name;
                    this.sha1 = sha1;
                    this.commit = commit;
                }
                
                // Getters and Setters
                public String getName() {
                    return name;
                }
                
                public void setName(String name) {
                    this.name = name;
                }
                
                public String getSha1() {
                    return sha1;
                }
                
                public void setSha1(String sha1) {
                    this.sha1 = sha1;
                }
                
                public String getCommit() {
                    return commit;
                }
                
                public void setCommit(String commit) {
                    this.commit = commit;
                }
                
                @Override
                public boolean equals(Object o) {
                    if (this == o) return true;
                    if (o == null || getClass() != o.getClass()) return false;
                    BranchData that = (BranchData) o;
                    return Objects.equals(name, that.name) &&
                           Objects.equals(sha1, that.sha1) &&
                           Objects.equals(commit, that.commit);
                }
                
                @Override
                public int hashCode() {
                    return Objects.hash(name, sha1, commit);
                }
                
                @Override
                public String toString() {
                    return "BranchData{" +
                           "name='" + name + '\'' +
                           ", sha1='" + sha1 + '\'' +
                           ", commit='" + commit + '\'' +
                           '}';
                }
            }
            
            @Override
            public boolean equals(Object o) {
                if (this == o) return true;
                if (o == null || getClass() != o.getClass()) return false;
                ScmData scmData = (ScmData) o;
                return Objects.equals(branch, scmData.branch) &&
                       Objects.equals(commit, scmData.commit) &&
                       Objects.equals(commitId, scmData.commitId) &&
                       Objects.equals(message, scmData.message) &&
                       Objects.equals(commitMessage, scmData.commitMessage) &&
                       Objects.equals(author, scmData.author) &&
                       Objects.equals(authorName, scmData.authorName) &&
                       Objects.equals(authorEmail, scmData.authorEmail) &&
                       Objects.equals(branches, scmData.branches) &&
                       Objects.equals(remoteUrls, scmData.remoteUrls);
            }
            
            @Override
            public int hashCode() {
                return Objects.hash(branch, commit, commitId, message, commitMessage,
                                   author, authorName, authorEmail, branches, remoteUrls);
            }
            
            @Override
            public String toString() {
                return "ScmData{" +
                       "branch='" + branch + '\'' +
                       ", commit='" + commit + '\'' +
                       ", commitId='" + commitId + '\'' +
                       ", message='" + message + '\'' +
                       ", commitMessage='" + commitMessage + '\'' +
                       ", author='" + author + '\'' +
                       ", authorName='" + authorName + '\'' +
                       ", authorEmail='" + authorEmail + '\'' +
                       ", branches=" + branches +
                       ", remoteUrls=" + remoteUrls +
                       '}';
            }
        }
        
        /**
         * Nested class for build cause data
         */
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class CauseData {
            
            @JsonProperty("_class")
            private String className;
            
            @JsonProperty("shortDescription")
            private String shortDescription;
            
            @JsonProperty("userId")
            private String userId;
            
            @JsonProperty("userName")
            private String userName;
            
            public CauseData() {
            }
            
            public CauseData(String className, String shortDescription, String userId, String userName) {
                this.className = className;
                this.shortDescription = shortDescription;
                this.userId = userId;
                this.userName = userName;
            }
            
            // Getters and Setters
            public String getClassName() {
                return className;
            }
            
            public void setClassName(String className) {
                this.className = className;
            }
            
            public String getShortDescription() {
                return shortDescription;
            }
            
            public void setShortDescription(String shortDescription) {
                this.shortDescription = shortDescription;
            }
            
            public String getUserId() {
                return userId;
            }
            
            public void setUserId(String userId) {
                this.userId = userId;
            }
            
            public String getUserName() {
                return userName;
            }
            
            public void setUserName(String userName) {
                this.userName = userName;
            }
            
            @Override
            public boolean equals(Object o) {
                if (this == o) return true;
                if (o == null || getClass() != o.getClass()) return false;
                CauseData causeData = (CauseData) o;
                return Objects.equals(className, causeData.className) &&
                       Objects.equals(shortDescription, causeData.shortDescription) &&
                       Objects.equals(userId, causeData.userId) &&
                       Objects.equals(userName, causeData.userName);
            }
            
            @Override
            public int hashCode() {
                return Objects.hash(className, shortDescription, userId, userName);
            }
            
            @Override
            public String toString() {
                return "CauseData{" +
                       "className='" + className + '\'' +
                       ", shortDescription='" + shortDescription + '\'' +
                       ", userId='" + userId + '\'' +
                       ", userName='" + userName + '\'' +
                       '}';
            }
        }
        
        /**
         * Nested class for build artifact data
         */
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class ArtifactData {
            
            @JsonProperty("displayPath")
            private String displayPath;
            
            @JsonProperty("fileName")
            private String fileName;
            
            @JsonProperty("relativePath")
            private String relativePath;
            
            public ArtifactData() {
            }
            
            public ArtifactData(String displayPath, String fileName, String relativePath) {
                this.displayPath = displayPath;
                this.fileName = fileName;
                this.relativePath = relativePath;
            }
            
            // Getters and Setters
            public String getDisplayPath() {
                return displayPath;
            }
            
            public void setDisplayPath(String displayPath) {
                this.displayPath = displayPath;
            }
            
            public String getFileName() {
                return fileName;
            }
            
            public void setFileName(String fileName) {
                this.fileName = fileName;
            }
            
            public String getRelativePath() {
                return relativePath;
            }
            
            public void setRelativePath(String relativePath) {
                this.relativePath = relativePath;
            }
            
            @Override
            public boolean equals(Object o) {
                if (this == o) return true;
                if (o == null || getClass() != o.getClass()) return false;
                ArtifactData that = (ArtifactData) o;
                return Objects.equals(displayPath, that.displayPath) &&
                       Objects.equals(fileName, that.fileName) &&
                       Objects.equals(relativePath, that.relativePath);
            }
            
            @Override
            public int hashCode() {
                return Objects.hash(displayPath, fileName, relativePath);
            }
            
            @Override
            public String toString() {
                return "ArtifactData{" +
                       "displayPath='" + displayPath + '\'' +
                       ", fileName='" + fileName + '\'' +
                       ", relativePath='" + relativePath + '\'' +
                       '}';
            }
        }
        
        /**
         * Parse artifacts JsonNode into a List of ArtifactData objects.
         * Handles both single object and array formats.
         *
         * @return List of ArtifactData objects, empty list if null or invalid
         */
        public List<ArtifactData> getArtifactsList() {
            List<ArtifactData> result = new ArrayList<>();
            
            if (artifacts == null || artifacts.isNull()) {
                return result;
            }
            
            try {
                if (artifacts.isArray()) {
                    // Handle array format: "artifacts": [...]
                    for (JsonNode elementNode : artifacts) {
                        ArtifactData artifact = new ArtifactData();
                        artifact.setDisplayPath(elementNode.path("displayPath").asText(null));
                        artifact.setFileName(elementNode.path("fileName").asText(null));
                        artifact.setRelativePath(elementNode.path("relativePath").asText(null));
                        if (artifact.getFileName() != null) {
                            result.add(artifact);
                        }
                    }
                } else if (artifacts.isObject()) {
                    // Handle single object format: "artifacts": {...}
                    ArtifactData artifact = new ArtifactData();
                    artifact.setDisplayPath(artifacts.path("displayPath").asText(null));
                    artifact.setFileName(artifacts.path("fileName").asText(null));
                    artifact.setRelativePath(artifacts.path("relativePath").asText(null));
                    if (artifact.getFileName() != null) {
                        result.add(artifact);
                    }
                }
            } catch (Exception e) {
                // Log error but don't fail the entire deserialization
                System.err.println("Error parsing artifacts: " + e.getMessage());
            }
            
            return result;
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            BuildData buildData = (BuildData) o;
            return Objects.equals(number, buildData.number) &&
                   Objects.equals(status, buildData.status) &&
                   Objects.equals(result, buildData.result) &&
                   Objects.equals(timestamp, buildData.timestamp) &&
                   Objects.equals(duration, buildData.duration) &&
                   Objects.equals(url, buildData.url) &&
                   Objects.equals(fullUrl, buildData.fullUrl) &&
                   Objects.equals(log, buildData.log) &&
                   Objects.equals(error, buildData.error) &&
                   Objects.equals(scm, buildData.scm) &&
                   Objects.equals(parameters, buildData.parameters) &&
                   Objects.equals(causes, buildData.causes) &&
                   Objects.equals(artifacts, buildData.artifacts);
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(number, status, result, timestamp, duration, url, fullUrl,
                               log, error, scm, parameters, causes, artifacts);
        }
        
        @Override
        public String toString() {
            return "BuildData{" +
                   "number=" + number +
                   ", status='" + status + '\'' +
                   ", result='" + result + '\'' +
                   ", timestamp=" + timestamp +
                   ", duration=" + duration +
                   ", url='" + url + '\'' +
                   ", fullUrl='" + fullUrl + '\'' +
                   ", log='" + log + '\'' +
                   ", error='" + error + '\'' +
                   ", scm=" + scm +
                   ", parameters=" + parameters +
                   ", causes=" + causes +
                   ", artifacts=" + artifacts +
                   '}';
        }
    }
    
    /**
     * Extract job name from various Jenkins webhook formats.
     * 
     * @return The extracted job name
     */
    public String extractJobName() {
        if (jobName != null && !jobName.isEmpty()) {
            return jobName;
        }
        
        if (name != null && !name.isEmpty()) {
            return name;
        }
        
        if (displayName != null && !displayName.isEmpty()) {
            return displayName;
        }
        
        if (fullDisplayName != null && !fullDisplayName.isEmpty()) {
            return fullDisplayName;
        }
        
        if (build != null && build.getUrl() != null) {
            // Try to extract job name from URL
            String buildUrl = build.getUrl();
            // Extract job name from URL like http://jenkins/job/my-job/123/
            int jobIndex = buildUrl.indexOf("/job/");
            if (jobIndex != -1) {
                int start = jobIndex + 5; // "/job/".length()
                int end = buildUrl.indexOf("/", start);
                if (end != -1) {
                    return buildUrl.substring(start, end);
                }
            }
        }
        
        return "unknown-job";
    }
    
    /**
     * Extract branch name from various Jenkins webhook formats.
     * Handles different SCM configurations and branch naming conventions.
     * 
     * @return The extracted branch name, defaults to "main" if not found
     */
    public String extractBranchName() {
        if (build != null && build.getScm() != null) {
            BuildData.ScmData scm = build.getScm();
            
            // Try direct branch field first
            if (scm.getBranch() != null && !scm.getBranch().isEmpty()) {
                return cleanBranchName(scm.getBranch());
            }
            
            // Try branches list
            if (scm.getBranches() != null && !scm.getBranches().isEmpty()) {
                for (BuildData.ScmData.BranchData branchData : scm.getBranches()) {
                    if (branchData.getName() != null && !branchData.getName().isEmpty()) {
                        return cleanBranchName(branchData.getName());
                    }
                }
            }
        }
        
        // Fallback to main branch
        return "main";
    }
    
    /**
     * Clean branch name by removing common prefixes like "origin/" or "refs/heads/"
     * 
     * @param branchName The raw branch name from Jenkins
     * @return Cleaned branch name
     */
    private String cleanBranchName(String branchName) {
        if (branchName == null || branchName.isEmpty()) {
            return "main";
        }
        
        // Remove common prefixes
        String cleaned = branchName;
        if (cleaned.startsWith("origin/")) {
            cleaned = cleaned.substring(7);
        } else if (cleaned.startsWith("refs/heads/")) {
            cleaned = cleaned.substring(11);
        } else if (cleaned.startsWith("refs/remotes/origin/")) {
            cleaned = cleaned.substring(20);
        }
        
        return cleaned.isEmpty() ? "main" : cleaned;
    }
    
    /**
     * Extract commit SHA from various SCM data fields
     * 
     * @return The commit SHA or null if not found
     */
    public String extractCommitSha() {
        if (build != null && build.getScm() != null) {
            BuildData.ScmData scm = build.getScm();
            
            // Try commit field first
            if (scm.getCommit() != null && !scm.getCommit().isEmpty()) {
                return scm.getCommit();
            }
            
            // Try commitId field
            if (scm.getCommitId() != null && !scm.getCommitId().isEmpty()) {
                return scm.getCommitId();
            }
            
            // Try branches list
            if (scm.getBranches() != null && !scm.getBranches().isEmpty()) {
                for (BuildData.ScmData.BranchData branchData : scm.getBranches()) {
                    if (branchData.getSha1() != null && !branchData.getSha1().isEmpty()) {
                        return branchData.getSha1();
                    }
                    if (branchData.getCommit() != null && !branchData.getCommit().isEmpty()) {
                        return branchData.getCommit();
                    }
                }
            }
        }
        
        return null;
    }
    
    /**
     * Extract commit message from SCM data
     * 
     * @return The commit message or null if not found
     */
    public String extractCommitMessage() {
        if (build != null && build.getScm() != null) {
            BuildData.ScmData scm = build.getScm();
            
            // Try message field first
            if (scm.getMessage() != null && !scm.getMessage().isEmpty()) {
                return scm.getMessage();
            }
            
            // Try commitMessage field
            if (scm.getCommitMessage() != null && !scm.getCommitMessage().isEmpty()) {
                return scm.getCommitMessage();
            }
        }
        
        return null;
    }
    
    /**
     * Extract author name from SCM data
     * 
     * @return The author name or null if not found
     */
    public String extractAuthor() {
        if (build != null && build.getScm() != null) {
            BuildData.ScmData scm = build.getScm();
            
            // Try author field first
            if (scm.getAuthor() != null && !scm.getAuthor().isEmpty()) {
                return scm.getAuthor();
            }
            
            // Try authorName field
            if (scm.getAuthorName() != null && !scm.getAuthorName().isEmpty()) {
                return scm.getAuthorName();
            }
        }
        
        return null;
    }
    
    /**
     * Extract build number from various Jenkins webhook formats.
     * 
     * @return The extracted build number or 0 if not found
     */
    public Integer extractBuildNumber() {
        if (build != null && build.getNumber() != null) {
            return build.getNumber();
        }
        
        return 0;
    }
    
    /**
     * Extract repository URL from various Jenkins webhook formats.
     * 
     * @return The extracted repository URL or null if not found
     */
    public String extractRepoUrl() {
        if (build != null && build.getScm() != null) {
            BuildData.ScmData scm = build.getScm();
            
            // Try remoteUrls list
            if (scm.getRemoteUrls() != null && !scm.getRemoteUrls().isEmpty()) {
                return scm.getRemoteUrls().get(0); // Return first URL
            }
        }
        
        if (build != null && build.getUrl() != null) {
            // Try to extract repo URL from build URL
            String buildUrl = build.getUrl();
            // Extract base URL like http://jenkins/job/my-job/
            int jobIndex = buildUrl.indexOf("/job/");
            if (jobIndex != -1) {
                return buildUrl.substring(0, jobIndex);
            }
        }
        
        return null;
    }
    
    /**
     * Extract build logs from various Jenkins webhook formats.
     *
     * @return The extracted build logs or null if not found
     */
    public String extractBuildLogs() {
        if (build != null) {
            if (build.getLog() != null && !build.getLog().isEmpty()) {
                return build.getLog();
            }
            
            if (build.getError() != null && !build.getError().isEmpty()) {
                return build.getError();
            }
        }
        
        return null;
    }
    
    // Backward compatibility methods
    public String getJob() {
        return extractJobName();
    }
    
    public Integer getBuildNumber() {
        return extractBuildNumber();
    }
    
    public String getBranch() {
        return extractBranchName();
    }
    
    public String getRepoUrl() {
        return extractRepoUrl();
    }
    
    public String getCommitSha() {
        return extractCommitSha();
    }
    
    public String getBuildLogs() {
        return extractBuildLogs();
    }
    
    public Map<String, Object> getMetadata() {
        // Return metadata from the build parameters if available
        if (build != null && build.getParameters() != null) {
            return new java.util.HashMap<>((Map<String, Object>) (Map<?, ?>) build.getParameters());
        }
        return null;
    }
    
    // Backward compatibility setter methods (no-op implementations)
    public void setJob(String job) {
        // No-op for backward compatibility
        // In a real implementation, we might set this in a field or convert to the new structure
    }
    
    public void setBuildNumber(Integer buildNumber) {
        // No-op for backward compatibility
        // In a real implementation, we might set this in a field or convert to the new structure
    }
    
    public void setBranch(String branch) {
        // No-op for backward compatibility
        // In a real implementation, we might set this in a field or convert to the new structure
    }
    
    public void setRepoUrl(String repoUrl) {
        // No-op for backward compatibility
        // In a real implementation, we might set this in a field or convert to the new structure
    }
    
    public void setCommitSha(String commitSha) {
        // No-op for backward compatibility
        // In a real implementation, we might set this in a field or convert to the new structure
    }
    
    public void setBuildLogs(String buildLogs) {
        // No-op for backward compatibility
        // In a real implementation, we might set this in a field or convert to the new structure
    }
    
    public void setMetadata(Map<String, Object> metadata) {
        // No-op for backward compatibility
        // In a real implementation, we might set this in a field or convert to the new structure
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JenkinsWebhookPayload that = (JenkinsWebhookPayload) o;
        return Objects.equals(name, that.name) &&
               Objects.equals(jobName, that.jobName) &&
               Objects.equals(build, that.build) &&
               Objects.equals(status, that.status) &&
               Objects.equals(displayName, that.displayName) &&
               Objects.equals(fullDisplayName, that.fullDisplayName) &&
               Objects.equals(url, that.url);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(name, jobName, build, status, displayName, fullDisplayName, url);
    }
    
    @Override
    public String toString() {
        return "JenkinsWebhookPayload{" +
               "name='" + name + '\'' +
               ", jobName='" + jobName + '\'' +
               ", build=" + build +
               ", status='" + status + '\'' +
               ", displayName='" + displayName + '\'' +
               ", fullDisplayName='" + fullDisplayName + '\'' +
               ", url='" + url + '\'' +
               '}';
    }
}