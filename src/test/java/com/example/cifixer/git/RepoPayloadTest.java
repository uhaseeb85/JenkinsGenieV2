package com.example.cifixer.git;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for RepoPayload.
 */
class RepoPayloadTest {
    
    @Test
    void shouldCreateEmptyRepoPayload() {
        // When
        RepoPayload payload = new RepoPayload();
        
        // Then
        assertThat(payload.getRepoUrl()).isNull();
        assertThat(payload.getBranch()).isNull();
        assertThat(payload.getCommitSha()).isNull();
        assertThat(payload.getBuildId()).isNull();
        assertThat(payload.getWorkingDirectory()).isNull();
        assertThat(payload.getCredentials()).isNull();
    }
    
    @Test
    void shouldCreateRepoPayloadWithParameters() {
        // Given
        String repoUrl = "https://github.com/test/repo.git";
        String branch = "main";
        String commitSha = "abc123";
        Long buildId = 100L;
        
        // When
        RepoPayload payload = new RepoPayload(repoUrl, branch, commitSha, buildId);
        
        // Then
        assertThat(payload.getRepoUrl()).isEqualTo(repoUrl);
        assertThat(payload.getBranch()).isEqualTo(branch);
        assertThat(payload.getCommitSha()).isEqualTo(commitSha);
        assertThat(payload.getBuildId()).isEqualTo(buildId);
        assertThat(payload.getWorkingDirectory()).isNull();
        assertThat(payload.getCredentials()).isNull();
    }
    
    @Test
    void shouldSetAndGetAllProperties() {
        // Given
        RepoPayload payload = new RepoPayload();
        
        String repoUrl = "https://github.com/test/repo.git";
        String branch = "develop";
        String commitSha = "def456";
        Long buildId = 200L;
        String workingDirectory = "/tmp/work/build-200";
        
        Map<String, String> credentials = new HashMap<>();
        credentials.put("username", "testuser");
        credentials.put("token", "testtoken");
        
        // When
        payload.setRepoUrl(repoUrl);
        payload.setBranch(branch);
        payload.setCommitSha(commitSha);
        payload.setBuildId(buildId);
        payload.setWorkingDirectory(workingDirectory);
        payload.setCredentials(credentials);
        
        // Then
        assertThat(payload.getRepoUrl()).isEqualTo(repoUrl);
        assertThat(payload.getBranch()).isEqualTo(branch);
        assertThat(payload.getCommitSha()).isEqualTo(commitSha);
        assertThat(payload.getBuildId()).isEqualTo(buildId);
        assertThat(payload.getWorkingDirectory()).isEqualTo(workingDirectory);
        assertThat(payload.getCredentials()).isEqualTo(credentials);
        assertThat(payload.getCredentials()).containsEntry("username", "testuser");
        assertThat(payload.getCredentials()).containsEntry("token", "testtoken");
    }
    
    @Test
    void shouldGenerateCorrectToString() {
        // Given
        RepoPayload payload = new RepoPayload("https://github.com/test/repo.git", "main", "abc123", 100L);
        payload.setWorkingDirectory("/tmp/work/build-100");
        
        // When
        String toString = payload.toString();
        
        // Then
        assertThat(toString).contains("RepoPayload{");
        assertThat(toString).contains("repoUrl='https://github.com/test/repo.git'");
        assertThat(toString).contains("branch='main'");
        assertThat(toString).contains("commitSha='abc123'");
        assertThat(toString).contains("buildId=100");
        assertThat(toString).contains("workingDirectory='/tmp/work/build-100'");
    }
    
    @Test
    void shouldHandleNullCredentials() {
        // Given
        RepoPayload payload = new RepoPayload();
        
        // When
        payload.setCredentials(null);
        
        // Then
        assertThat(payload.getCredentials()).isNull();
    }
    
    @Test
    void shouldHandleEmptyCredentials() {
        // Given
        RepoPayload payload = new RepoPayload();
        Map<String, String> emptyCredentials = new HashMap<>();
        
        // When
        payload.setCredentials(emptyCredentials);
        
        // Then
        assertThat(payload.getCredentials()).isNotNull();
        assertThat(payload.getCredentials()).isEmpty();
    }
}