package com.example.cifixer.web;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    
    private static final Logger logger = LoggerFactory.getLogger(JenkinsWebhookPayload.class);
    
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
    
    @JsonProperty("repoUrl")
    private String repoUrl;
    
    @JsonProperty("branch")
    private String branch;
    
    @JsonProperty("commitSha")
    private String commitSha;
    
    @JsonProperty("job")
    private String job;
    
    @JsonProperty("buildNumber")
    private Integer buildNumber;
    
    @JsonProperty("buildLogs")
    private String buildLogs;
    
    @JsonProperty("metadata")
    private Map<String, Object> metadata;
    
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
        logger.debug("Extracting repository URL from JenkinsWebhookPayload");
        
        // First, check if repoUrl is directly provided in the payload
        if (repoUrl != null && !repoUrl.isEmpty()) {
            logger.debug("Found repository URL directly in payload: {}", repoUrl);
            return repoUrl;
        }
        
        if (build != null && build.getScm() != null) {
            BuildData.ScmData scm = build.getScm();
            logger.debug("SCM data available in JenkinsWebhookPayload: {}", scm);
            
            // Try remoteUrls list
            if (scm.getRemoteUrls() != null && !scm.getRemoteUrls().isEmpty()) {
                String repoUrl = scm.getRemoteUrls().get(0); // Return first URL
                logger.debug("Found repository URL in remoteUrls from JenkinsWebhookPayload: {}", repoUrl);
                return repoUrl;
            } else {
                logger.debug("No remoteUrls found in SCM data in JenkinsWebhookPayload");
            }
            
            // Try to construct URL from other SCM fields
            // This is a fallback approach for cases where remoteUrls is not provided
            String constructedUrl = constructRepoUrlFromScm(scm);
            if (constructedUrl != null) {
                logger.debug("Constructed repository URL from SCM data in JenkinsWebhookPayload: {}", constructedUrl);
                return constructedUrl;
            }
        } else {
            logger.debug("No SCM data available in build in JenkinsWebhookPayload");
        }
        
        // Try to extract repo URL from build URL
        if (build != null && build.getUrl() != null) {
            String buildUrl = build.getUrl();
            logger.debug("Build URL available in JenkinsWebhookPayload: {}", buildUrl);
            
            // Try to get repo URL from Jenkins job URL
            // Pattern: http://jenkins/job/my-job/123/ -> http://jenkins/
            int jobIndex = buildUrl.indexOf("/job/");
            if (jobIndex != -1) {
                String repoUrl = buildUrl.substring(0, jobIndex);
                logger.debug("Extracted repository URL from build URL in JenkinsWebhookPayload: {}", repoUrl);
                return repoUrl;
            } else {
                logger.debug("Could not extract repository URL from build URL in JenkinsWebhookPayload");
            }
        } else {
            logger.debug("No build URL available in JenkinsWebhookPayload");
        }
        
        // Try to extract repo URL from build logs (for cases where git commands are in logs)
        String buildLogs = extractBuildLogs();
        if (buildLogs != null && !buildLogs.isEmpty()) {
            String repoUrlFromLogs = extractUrlFromGitCommand(buildLogs);
            if (repoUrlFromLogs != null) {
                logger.debug("Extracted repository URL from build logs in JenkinsWebhookPayload: {}", repoUrlFromLogs);
                return repoUrlFromLogs;
            }
        }
        
        // Try to construct repo URL from project/job name as a last resort
        String jobName = extractJobName();
        if (jobName != null && !jobName.isEmpty()) {
            String constructedUrl = constructRepoUrlFromProjectName(jobName);
            if (constructedUrl != null) {
                logger.debug("Constructed repository URL from project name: {}", constructedUrl);
                return constructedUrl;
            }
        }
        
        logger.warn("Could not extract repository URL from JenkinsWebhookPayload. This may indicate that the Jenkins webhook is not properly configured to include repository information.");
        return null;
    }
    
    /**
     * Attempts to construct a repository URL from SCM data fields.
     * This is a fallback method when remoteUrls is not provided.
     *
     * @param scm The SCM data
     * @return Constructed repository URL or null if not possible
     */
    private String constructRepoUrlFromScm(BuildData.ScmData scm) {
        // Try to get information from commit message or other fields
        // This is a basic implementation and might not work for all cases
        
        // Check if we have a commit message that might contain repository information
        String commitMessage = scm.getCommitMessage();
        if (commitMessage == null) {
            commitMessage = scm.getMessage();
        }
        
        if (commitMessage != null) {
            // Look for common patterns in commit messages that might contain repo URLs
            String repoUrl = extractUrlFromText(commitMessage);
            if (repoUrl != null) {
                return repoUrl;
            }
        }
        
        // Check if we have an author email that might give us clues about the repository host
        String authorEmail = scm.getAuthorEmail();
        if (authorEmail != null && authorEmail.contains("@")) {
            // Try to construct a basic URL from the email domain
            // This is a very basic approach and might not work for all cases
            String[] parts = authorEmail.split("@");
            if (parts.length == 2) {
                String domain = parts[1];
                // Try common repository hosting services
                if (domain.equals("github.com")) {
                    // We would need more information to construct a GitHub URL
                    // For now, just return a placeholder
                    return "https://github.com";
                } else if (domain.equals("gitlab.com")) {
                    return "https://gitlab.com";
                }
            }
        }
        
        // If we have a branch name, we might be able to infer the repository structure
        // This is highly dependent on the specific setup and conventions used
        
        // For now, let's just return null to indicate we couldn't construct a URL
        // A more complete implementation would analyze the available data
        // and try to construct a URL based on common patterns
        return null;
    }
    
    /**
     * Attempts to construct a repository URL from the project/job name.
     * This is a fallback method when other methods fail to extract the URL.
     *
     * @param projectName The project or job name
     * @return Constructed repository URL or null if not possible
     */
    private String constructRepoUrlFromProjectName(String projectName) {
        if (projectName == null || projectName.isEmpty() || projectName.equals("unknown-job")) {
            return null;
        }
        
        logger.debug("Attempting to construct repository URL from project name: {}", projectName);
        
        // Clean up the project name to make it suitable for a repository name
        String repoName = projectName.trim()
            .replaceAll("[^a-zA-Z0-9._-]", "-") // Replace invalid chars with hyphens
            .replaceAll("-+", "-")              // Replace multiple hyphens with single hyphen
            .replaceAll("^-|-$", "");           // Remove leading/trailing hyphens
        
        if (repoName.isEmpty()) {
            return null;
        }
        
        // Try to extract organization name from project name if it follows org/repo pattern
        String orgName = "uhaseeb85"; // Default organization name from the example URL
        if (repoName.contains("/")) {
            String[] parts = repoName.split("/", 2);
            if (parts.length == 2 && !parts[0].isEmpty() && !parts[1].isEmpty()) {
                orgName = parts[0];
                repoName = parts[1];
            }
        }
        
        // Construct GitHub URL
        return "https://github.com/" + orgName + "/" + repoName + ".git";
    }
    
    /**
     * Extracts a URL from text using basic pattern matching.
     * This is a simple implementation that looks for common URL patterns.
     *
     * @param text The text to search for URLs
     * @return The first URL found or null if none found
     */
    private String extractUrlFromText(String text) {
        if (text == null) {
            return null;
        }
        
        // Simple regex to match URLs - this is a basic implementation
        // A more robust implementation would use a proper URL parsing library
        String[] words = text.split("\\s+");
        for (String word : words) {
            if (word.startsWith("http://") || word.startsWith("https://")) {
                // Basic validation - check if it looks like a URL
                if (word.contains(".") && word.length() > 10) {
                    return word;
                }
            }
        }
        
        return null;
    }
    
    /**
     * Extracts repository URL from git command output in build logs.
     * Looks for patterns like "git.exe config remote.origin.url https://github.com/user/repo.git"
     * or similar git commands that reveal the repository URL.
     *
     * @param logs The build logs to search
     * @return The extracted repository URL or null if not found
     */
    private String extractUrlFromGitCommand(String logs) {
        if (logs == null || logs.isEmpty()) {
            return null;
        }
        
        logger.debug("Attempting to extract repository URL from git command in logs");
        
        // Look for git config remote.origin.url command
        String[] lines = logs.split("\\r?\\n");
        for (String line : lines) {
            // Match patterns like "git.exe config remote.origin.url https://github.com/user/repo.git"
            // or "git config --get remote.origin.url" output
            if ((line.contains("git") && line.contains("config") && line.contains("remote.origin.url")) ||
                (line.contains("git") && line.contains("remote") && line.contains("get-url"))) {
                
                logger.debug("Found potential git remote command: {}", line);
                
                // Extract the URL part - it's typically the last part of the command or output
                String[] parts = line.trim().split("\\s+");
                for (int i = 0; i < parts.length; i++) {
                    String part = parts[i];
                    // Check if this part is a URL
                    if ((part.startsWith("http://") || part.startsWith("https://") ||
                         part.startsWith("git@") || part.startsWith("ssh://")) &&
                        (part.contains(".git") || part.contains("github") ||
                         part.contains("gitlab") || part.contains("bitbucket"))) {
                        
                        logger.debug("Extracted repository URL from git command: {}", part);
                        return part;
                    }
                }
                
                // If we didn't find a URL in the parts but the line contains a URL pattern,
                // try to extract it using a more general approach
                String url = extractUrlFromText(line);
                if (url != null) {
                    logger.debug("Extracted repository URL using general pattern: {}", url);
                    return url;
                }
            }
        }
        
        logger.debug("No repository URL found in git commands in logs");
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
        return job != null ? job : extractJobName();
    }
    
    public Integer getBuildNumber() {
        return buildNumber != null ? buildNumber : extractBuildNumber();
    }
    
    public String getBranch() {
        return branch != null ? branch : extractBranchName();
    }
    
    public String getRepoUrl() {
        return repoUrl != null ? repoUrl : extractRepoUrl();
    }
    
    public String getCommitSha() {
        return commitSha != null ? commitSha : extractCommitSha();
    }
    
    public String getBuildLogs() {
        return buildLogs != null ? buildLogs : extractBuildLogs();
    }
    
    public Map<String, Object> getMetadata() {
        if (metadata != null) {
            return metadata;
        }
        // Return metadata from the build parameters if available
        if (build != null && build.getParameters() != null) {
            return new java.util.HashMap<>(build.getParameters());
        }
        return null;
    }
    
    // Backward compatibility setter methods
    public void setJob(String job) {
        this.job = job;
    }
    
    public void setBuildNumber(Integer buildNumber) {
        this.buildNumber = buildNumber;
    }
    
    public void setBranch(String branch) {
        this.branch = branch;
    }
    
    public void setRepoUrl(String repoUrl) {
        this.repoUrl = repoUrl;
    }
    
    public void setCommitSha(String commitSha) {
        this.commitSha = commitSha;
    }
    
    public void setBuildLogs(String buildLogs) {
        this.buildLogs = buildLogs;
    }
    
    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
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