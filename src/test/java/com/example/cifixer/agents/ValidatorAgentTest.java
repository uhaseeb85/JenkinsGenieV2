package com.example.cifixer.agents;

import com.example.cifixer.core.Task;
import com.example.cifixer.core.TaskResult;
import com.example.cifixer.core.TaskStatus;
import com.example.cifixer.core.TaskType;
import com.example.cifixer.store.Build;
import com.example.cifixer.store.Validation;
import com.example.cifixer.store.ValidationRepository;
import com.example.cifixer.store.ValidationType;
import com.example.cifixer.util.BuildTool;
import com.example.cifixer.util.CommandExecutor;
import com.example.cifixer.util.SpringProjectAnalyzer;
import com.example.cifixer.util.SpringProjectContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ValidatorAgentTest {
    
    @Mock
    private ValidationRepository validationRepository;
    
    @Mock
    private CommandExecutor commandExecutor;
    
    @Mock
    private SpringProjectAnalyzer springProjectAnalyzer;
    
    @InjectMocks
    private ValidatorAgent validatorAgent;
    
    @TempDir
    Path tempDir;
    
    private Build testBuild;
    private Task testTask;
    private ValidatePayload testPayload;
    private SpringProjectContext springContext;
    
    @BeforeEach
    void setUp() {
        testBuild = new Build("test-job", 123, "main", "https://github.com/test/repo", "abc123");
        testBuild.setId(1L);
        
        testTask = new Task(testBuild, TaskType.VALIDATE);
        testTask.setId(1L);
        
        testPayload = new ValidatePayload(tempDir.toString(), "MAVEN");
        
        springContext = new SpringProjectContext();
        springContext.setBuildTool(BuildTool.MAVEN);
        springContext.setSpringBootVersion("2.7.0");
        springContext.setMavenModules(Collections.singletonList("root"));
        springContext.setDependencies(new HashMap<>());
    }
    
    @Test
    void shouldSuccessfullyValidateWhenCompilationAndTestsPass() {
        // Given
        when(springProjectAnalyzer.analyzeProject(anyString())).thenReturn(springContext);
        
        CommandExecutor.CommandResult successResult = new CommandExecutor.CommandResult(0, "BUILD SUCCESS", "", false);
        when(commandExecutor.executeMaven(eq("clean compile -DskipTests"), any(File.class))).thenReturn(successResult);
        when(commandExecutor.executeMaven(eq("test -Dspring.profiles.active=test"), any(File.class))).thenReturn(successResult);
        
        when(validationRepository.save(any(Validation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        
        // When
        TaskResult result = validatorAgent.handle(testTask, testPayload);
        
        // Then
        assertThat(result.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(result.getMessage()).contains("Validation completed successfully");
        assertThat(result.getMetadata()).containsEntry("compilationSuccessful", true);
        assertThat(result.getMetadata()).containsEntry("testsSuccessful", true);
        assertThat(result.getMetadata()).containsEntry("buildTool", "MAVEN");
        
        verify(validationRepository, times(2)).save(any(Validation.class));
        verify(commandExecutor).executeMaven("clean compile -DskipTests", tempDir.toFile());
        verify(commandExecutor).executeMaven("test -Dspring.profiles.active=test", tempDir.toFile());
    }
    
    @Test
    void shouldFailWhenCompilationFails() {
        // Given
        when(springProjectAnalyzer.analyzeProject(anyString())).thenReturn(springContext);
        
        CommandExecutor.CommandResult failureResult = new CommandExecutor.CommandResult(1, "", 
            "[ERROR] /src/main/java/User.java:[15,8] cannot find symbol: class UserRepository", false);
        when(commandExecutor.executeMaven(eq("clean compile -DskipTests"), any(File.class))).thenReturn(failureResult);
        
        when(validationRepository.save(any(Validation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        
        // When
        TaskResult result = validatorAgent.handle(testTask, testPayload);
        
        // Then
        assertThat(result.getStatus()).isEqualTo(TaskStatus.FAILED);
        assertThat(result.getMessage()).contains("Validation failed");
        assertThat(result.getMetadata()).containsEntry("validationType", "COMPILE");
        assertThat(result.getMetadata()).containsEntry("exitCode", 1);
        assertThat(result.getMetadata()).containsEntry("retryable", false);
        
        verify(validationRepository).save(any(Validation.class));
        verify(commandExecutor).executeMaven("clean compile -DskipTests", tempDir.toFile());
        verify(commandExecutor, never()).executeMaven(eq("test -Dspring.profiles.active=test"), any(File.class));
    }
    
    @Test
    void shouldRetryWhenSpringContextFailureOccurs() {
        // Given
        testTask.setAttempt(1);
        testTask.setMaxAttempts(3);
        
        when(springProjectAnalyzer.analyzeProject(anyString())).thenReturn(springContext);
        
        CommandExecutor.CommandResult compileSuccess = new CommandExecutor.CommandResult(0, "BUILD SUCCESS", "", false);
        CommandExecutor.CommandResult testFailure = new CommandExecutor.CommandResult(1, "", 
            "NoSuchBeanDefinitionException: No qualifying bean of type 'UserRepository'", false);
        
        when(commandExecutor.executeMaven(eq("clean compile -DskipTests"), any(File.class))).thenReturn(compileSuccess);
        when(commandExecutor.executeMaven(eq("test -Dspring.profiles.active=test"), any(File.class))).thenReturn(testFailure);
        
        when(validationRepository.save(any(Validation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        
        // When
        TaskResult result = validatorAgent.handle(testTask, testPayload);
        
        // Then
        assertThat(result.getStatus()).isEqualTo(TaskStatus.RETRY);
        assertThat(result.getMessage()).contains("Validation failed but retryable");
        
        verify(validationRepository, times(2)).save(any(Validation.class));
    }
    
    @Test
    void shouldUseGradleCommandsForGradleProjects() {
        // Given
        springContext.setBuildTool(BuildTool.GRADLE);
        when(springProjectAnalyzer.analyzeProject(anyString())).thenReturn(springContext);
        
        CommandExecutor.CommandResult successResult = new CommandExecutor.CommandResult(0, "BUILD SUCCESSFUL", "", false);
        when(commandExecutor.executeGradle(eq("clean compileJava -x test"), any(File.class))).thenReturn(successResult);
        when(commandExecutor.executeGradle(eq("test --info"), any(File.class))).thenReturn(successResult);
        
        when(validationRepository.save(any(Validation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        
        // When
        TaskResult result = validatorAgent.handle(testTask, testPayload);
        
        // Then
        assertThat(result.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(result.getMetadata()).containsEntry("buildTool", "GRADLE");
        
        verify(commandExecutor).executeGradle("clean compileJava -x test", tempDir.toFile());
        verify(commandExecutor).executeGradle("test --info", tempDir.toFile());
    }
    
    @Test
    void shouldFailWhenWorkingDirectoryDoesNotExist() {
        // Given
        ValidatePayload invalidPayload = new ValidatePayload("/nonexistent/directory", "MAVEN");
        
        // When
        TaskResult result = validatorAgent.handle(testTask, invalidPayload);
        
        // Then
        assertThat(result.getStatus()).isEqualTo(TaskStatus.FAILED);
        assertThat(result.getMessage()).contains("Working directory does not exist");
        
        verifyNoInteractions(commandExecutor);
        verifyNoInteractions(validationRepository);
    }
    
    @Test
    void shouldHandleExceptionsDuringValidation() {
        // Given
        when(springProjectAnalyzer.analyzeProject(anyString())).thenThrow(new RuntimeException("Analysis failed"));
        
        // When
        TaskResult result = validatorAgent.handle(testTask, testPayload);
        
        // Then
        assertThat(result.getStatus()).isEqualTo(TaskStatus.FAILED);
        assertThat(result.getMessage()).contains("Validation failed due to error");
        
        verifyNoInteractions(commandExecutor);
        verifyNoInteractions(validationRepository);
    }
    
    @Test
    void shouldExtractMavenErrorContext() {
        // Given
        when(springProjectAnalyzer.analyzeProject(anyString())).thenReturn(springContext);
        
        CommandExecutor.CommandResult failureResult = new CommandExecutor.CommandResult(1, "", 
            "[ERROR] /src/main/java/User.java:[15,8] cannot find symbol\n[ERROR] symbol: class UserRepository", false);
        when(commandExecutor.executeMaven(eq("clean compile -DskipTests"), any(File.class))).thenReturn(failureResult);
        
        when(validationRepository.save(any(Validation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        
        // When
        TaskResult result = validatorAgent.handle(testTask, testPayload);
        
        // Then
        assertThat(result.getStatus()).isEqualTo(TaskStatus.FAILED);
        String errorContext = (String) result.getMetadata().get("errorContext");
        assertThat(errorContext).contains("cannot find symbol");
        assertThat(errorContext).contains("UserRepository");
    }
    
    @Test
    void shouldMarkNonRetryableFailuresCorrectly() {
        // Given
        testTask.setAttempt(1);
        testTask.setMaxAttempts(3);
        
        when(springProjectAnalyzer.analyzeProject(anyString())).thenReturn(springContext);
        
        CommandExecutor.CommandResult failureResult = new CommandExecutor.CommandResult(1, "", 
            "[ERROR] incompatible types: String cannot be converted to Integer", false);
        when(commandExecutor.executeMaven(eq("clean compile -DskipTests"), any(File.class))).thenReturn(failureResult);
        
        when(validationRepository.save(any(Validation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        
        // When
        TaskResult result = validatorAgent.handle(testTask, testPayload);
        
        // Then
        assertThat(result.getStatus()).isEqualTo(TaskStatus.FAILED);
        assertThat(result.getMetadata()).containsEntry("retryable", false);
    }
}