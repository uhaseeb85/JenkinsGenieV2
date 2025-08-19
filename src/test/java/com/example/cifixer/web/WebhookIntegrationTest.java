package com.example.cifixer.web;

import com.example.cifixer.core.TaskStatus;
import com.example.cifixer.core.TaskType;
import com.example.cifixer.store.Build;
import com.example.cifixer.store.BuildRepository;
import com.example.cifixer.store.BuildStatus;
import com.example.cifixer.store.TaskRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;


/**
 * Integration test for webhook processing flow.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
    "webhook.signature.validation.enabled=false",
    "orchestrator.processing.enabled=false",
    "spring.datasource.url=jdbc:h2:mem:testdb",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.flyway.enabled=false"
})
class WebhookIntegrationTest {
    
    @Autowired
    private TestRestTemplate restTemplate;
    
    @LocalServerPort
    private int port;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Autowired
    private BuildRepository buildRepository;
    
    @Autowired
    private TaskRepository taskRepository;
    
    @Test
    void webhookToTaskFlow_ShouldCreateBuildAndTask() throws Exception {
        // Given
        JenkinsWebhookPayload payload = new JenkinsWebhookPayload();
        payload.setJob("integration-test-job");
        payload.setBuildNumber(456);
        payload.setBranch("feature/test");
        payload.setRepoUrl("https://github.com/example/integration-test.git");
        payload.setCommitSha("integration123");
        payload.setBuildLogs("Integration test build failure logs");
        
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("testType", "integration");
        payload.setMetadata(metadata);
        
        // When
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<JenkinsWebhookPayload> request = new HttpEntity<>(payload, headers);
        
        ResponseEntity<WebhookResponse> response = restTemplate.exchange(
            "/api/webhooks/jenkins", 
            HttpMethod.POST, 
            request, 
            WebhookResponse.class);
        
        // Then - Verify response
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo("success");
        assertThat(response.getBody().getBuildId()).isNotNull();
        
        // Then - Verify build was created
        Build createdBuild = buildRepository.findByJobAndBuildNumber(
            "integration-test-job", 456).orElse(null);
        
        assertThat(createdBuild).isNotNull();
        assertThat(createdBuild.getJob()).isEqualTo("integration-test-job");
        assertThat(createdBuild.getBuildNumber()).isEqualTo(456);
        assertThat(createdBuild.getBranch()).isEqualTo("feature/test");
        assertThat(createdBuild.getRepoUrl()).isEqualTo("https://github.com/example/integration-test.git");
        assertThat(createdBuild.getCommitSha()).isEqualTo("integration123");
        assertThat(createdBuild.getStatus()).isEqualTo(BuildStatus.PROCESSING);
        
        // Verify payload was stored
        assertThat(createdBuild.getPayload()).isNotNull();
        assertThat(createdBuild.getPayload().get("buildLogs")).isEqualTo("Integration test build failure logs");
        assertThat(createdBuild.getPayload().get("metadata")).isNotNull();
        
        // Verify PLAN task was created
        java.util.List<com.example.cifixer.core.Task> tasks = taskRepository.findByBuildIdOrderByCreatedAtAsc(createdBuild.getId());
        assertThat(tasks).hasSize(1);
        
        com.example.cifixer.core.Task planTask = tasks.get(0);
        assertThat(planTask.getType()).isEqualTo(TaskType.PLAN);
        assertThat(planTask.getStatus()).isEqualTo(TaskStatus.PENDING);
        assertThat(planTask.getAttempt()).isEqualTo(0);
        assertThat(planTask.getMaxAttempts()).isEqualTo(3);
        
        // Verify task payload
        assertThat(planTask.getPayload()).isNotNull();
        assertThat(planTask.getPayload().get("buildLogs")).isEqualTo("Integration test build failure logs");
        assertThat(planTask.getPayload().get("repoUrl")).isEqualTo("https://github.com/example/integration-test.git");
        assertThat(planTask.getPayload().get("branch")).isEqualTo("feature/test");
        assertThat(planTask.getPayload().get("commitSha")).isEqualTo("integration123");
    }
}