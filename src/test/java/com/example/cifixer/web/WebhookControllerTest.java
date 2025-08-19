package com.example.cifixer.web;

import com.example.cifixer.core.TaskQueue;
import com.example.cifixer.core.TaskType;
import com.example.cifixer.store.Build;
import com.example.cifixer.store.BuildRepository;
import com.example.cifixer.store.BuildStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(WebhookController.class)
class WebhookControllerTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @MockBean
    private WebhookValidator webhookValidator;
    
    @MockBean
    private BuildRepository buildRepository;
    
    @MockBean
    private TaskQueue taskQueue;
    
    private JenkinsWebhookPayload validPayload;
    private Build savedBuild;
    
    @BeforeEach
    void setUp() {
        validPayload = new JenkinsWebhookPayload();
        validPayload.setJob("test-job");
        validPayload.setBuildNumber(123);
        validPayload.setBranch("main");
        validPayload.setRepoUrl("https://github.com/example/repo.git");
        validPayload.setCommitSha("abc123def456");
        validPayload.setBuildLogs("Build failed with compilation errors");
        
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("jenkinsUrl", "https://jenkins.example.com");
        validPayload.setMetadata(metadata);
        
        savedBuild = new Build(
            validPayload.getJob(),
            validPayload.getBuildNumber(),
            validPayload.getBranch(),
            validPayload.getRepoUrl(),
            validPayload.getCommitSha()
        );
        savedBuild.setId(1L);
        savedBuild.setStatus(BuildStatus.PROCESSING);
    }
    
    @Test
    void handleJenkinsFailure_ValidPayload_ShouldReturnSuccess() throws Exception {
        // Given
        when(buildRepository.save(any(Build.class))).thenReturn(savedBuild);
        doNothing().when(webhookValidator).validateJenkinsPayload(any(), any());
        
        // When & Then
        mockMvc.perform(post("/webhooks/jenkins")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validPayload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.buildId").value(1L))
                .andExpect(jsonPath("$.message").value("Build failure received and queued for processing"));
        
        // Verify interactions
        verify(webhookValidator).validateJenkinsPayload(any(JenkinsWebhookPayload.class), eq(null));
        verify(buildRepository).save(any(Build.class));
        verify(taskQueue).enqueue(any());
    }
    
    @Test
    void handleJenkinsFailure_WithSignature_ShouldPassSignatureToValidator() throws Exception {
        // Given
        String signature = "sha256=test-signature";
        when(buildRepository.save(any(Build.class))).thenReturn(savedBuild);
        doNothing().when(webhookValidator).validateJenkinsPayload(any(), eq(signature));
        
        // When & Then
        mockMvc.perform(post("/webhooks/jenkins")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Jenkins-Signature", signature)
                .content(objectMapper.writeValueAsString(validPayload)))
                .andExpect(status().isOk());
        
        // Verify signature was passed to validator
        verify(webhookValidator).validateJenkinsPayload(any(JenkinsWebhookPayload.class), eq(signature));
    }
    
    @Test
    void handleJenkinsFailure_ValidationException_ShouldReturnBadRequest() throws Exception {
        // Given
        doThrow(new WebhookValidator.ValidationException("Missing required field"))
            .when(webhookValidator).validateJenkinsPayload(any(), any());
        
        // When & Then
        mockMvc.perform(post("/webhooks/jenkins")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validPayload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("Validation failed: Missing required field"));
        
        // Verify no build was saved
        verify(buildRepository, never()).save(any());
        verify(taskQueue, never()).enqueue(any());
    }
    
    @Test
    void handleJenkinsFailure_SecurityException_ShouldReturnUnauthorized() throws Exception {
        // Given
        doThrow(new SecurityException("Invalid signature"))
            .when(webhookValidator).validateJenkinsPayload(any(), any());
        
        // When & Then
        mockMvc.perform(post("/webhooks/jenkins")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validPayload)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("Security validation failed"));
    }
    
    @Test
    void health_ShouldReturnHealthyStatus() throws Exception {
        mockMvc.perform(get("/webhooks/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("healthy"))
                .andExpect(jsonPath("$.service").value("webhook"));
    }
}