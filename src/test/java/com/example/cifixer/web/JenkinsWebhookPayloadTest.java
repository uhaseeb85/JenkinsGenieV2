package com.example.cifixer.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class JenkinsWebhookPayloadTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void testDeserializeSimplePayload() throws IOException {
        String json = "{\n" +
                "  \"jobName\": \"my-job\",\n" +
                "  \"build\": {\n" +
                "    \"number\": 123,\n" +
                "    \"scm\": {\n" +
                "      \"branch\": \"main\",\n" +
                "      \"commit\": \"abc123def456\"\n" +
                "    }\n" +
                "  }\n" +
                "}";

        JenkinsWebhookPayload payload = objectMapper.readValue(json, JenkinsWebhookPayload.class);
        
        assertNotNull(payload);
        assertEquals("my-job", payload.getJob());
        assertEquals(123, payload.getBuildNumber());
        assertEquals("main", payload.getBranch());
        assertEquals("abc123def456", payload.getCommitSha());
    }

    @Test
    public void testDeserializeComplexPayload() throws IOException {
        String json = "{\n" +
                "  \"name\": \"my-job\",\n" +
                "  \"displayName\": \"My Job\",\n" +
                "  \"build\": {\n" +
                "    \"number\": 456,\n" +
                "    \"url\": \"http://jenkins/job/my-job/456/\",\n" +
                "    \"scm\": {\n" +
                "      \"branch\": \"feature/new-feature\",\n" +
                "      \"commitId\": \"def456ghi789\",\n" +
                "      \"author\": \"John Doe\",\n" +
                "      \"message\": \"Add new feature\"\n" +
                "    }\n" +
                "  }\n" +
                "}";

        JenkinsWebhookPayload payload = objectMapper.readValue(json, JenkinsWebhookPayload.class);
        
        assertNotNull(payload);
        assertEquals("my-job", payload.getJob());
        assertEquals(456, payload.getBuildNumber());
        assertEquals("feature/new-feature", payload.getBranch());
        assertEquals("def456ghi789", payload.getCommitSha());
        assertEquals("John Doe", payload.extractAuthor());
        assertEquals("Add new feature", payload.extractCommitMessage());
    }

    @Test
    public void testExtractJobNameFromUrl() throws IOException {
        String json = "{\n" +
                "  \"build\": {\n" +
                "    \"number\": 789,\n" +
                "    \"url\": \"http://jenkins/job/my-job/789/\",\n" +
                "    \"scm\": {\n" +
                "      \"branch\": \"main\",\n" +
                "      \"commit\": \"jkl012mno345\"\n" +
                "    }\n" +
                "  }\n" +
                "}";

        JenkinsWebhookPayload payload = objectMapper.readValue(json, JenkinsWebhookPayload.class);
        
        assertNotNull(payload);
        assertEquals("my-job", payload.getJob());
        assertEquals(789, payload.getBuildNumber());
        assertEquals("main", payload.getBranch());
        assertEquals("jkl012mno345", payload.getCommitSha());
    }

    @Test
    public void testExtractBranchNameWithPrefix() throws IOException {
        String json = "{\n" +
                "  \"jobName\": \"my-job\",\n" +
                "  \"build\": {\n" +
                "    \"number\": 101,\n" +
                "    \"scm\": {\n" +
                "      \"branch\": \"origin/feature/branch\",\n" +
                "      \"commit\": \"pqr678stu901\"\n" +
                "    }\n" +
                "  }\n" +
                "}";

        JenkinsWebhookPayload payload = objectMapper.readValue(json, JenkinsWebhookPayload.class);
        
        assertNotNull(payload);
        assertEquals("my-job", payload.getJob());
        assertEquals(101, payload.getBuildNumber());
        assertEquals("feature/branch", payload.getBranch()); // Should be cleaned
        assertEquals("pqr678stu901", payload.getCommitSha());
    }

    @Test
    public void testNullPayload() {
        JenkinsWebhookPayload payload = new JenkinsWebhookPayload();
        
        assertNotNull(payload);
        assertEquals("unknown-job", payload.getJob());
        assertEquals(0, payload.getBuildNumber());
        assertEquals("main", payload.getBranch());
        assertNull(payload.getCommitSha());
        assertNull(payload.getRepoUrl());
        assertNull(payload.getBuildLogs());
    }

    @Test
    public void testBackwardCompatibility() {
        JenkinsWebhookPayload payload = new JenkinsWebhookPayload();
        
        // Test that setter methods exist (backward compatibility)
        payload.setJob("test-job");
        payload.setBuildNumber(123);
        payload.setBranch("test-branch");
        payload.setRepoUrl("https://github.com/test/repo.git");
        payload.setCommitSha("test123");
        payload.setBuildLogs("test logs");
        
        // Test that getter methods work with extract methods
        assertEquals("unknown-job", payload.getJob()); // Should return extracted value (default)
        assertEquals(0, payload.getBuildNumber()); // Should return extracted value (default)
        assertEquals("main", payload.getBranch()); // Should return extracted value (default)
        assertNull(payload.getRepoUrl()); // Should return extracted value (default)
        assertNull(payload.getBuildLogs()); // Should return extracted value (default)
        
        // Test that we can set values through the build data structure
        JenkinsWebhookPayload.BuildData buildData = new JenkinsWebhookPayload.BuildData();
        buildData.setNumber(123);
        payload.setBuild(buildData);
        
        // Now getBuildNumber should return the value from the build data
        assertEquals(123, payload.getBuildNumber());
        
        // Test metadata
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("key", "value");
        payload.setMetadata(metadata);
        assertNull(payload.getMetadata()); // Should return null as we don't store it
    }
}