package com.example.cifixer.agents;

import com.example.cifixer.core.Task;
import com.example.cifixer.core.TaskResult;
import com.example.cifixer.core.TaskStatus;
import com.example.cifixer.core.TaskType;
import com.example.cifixer.store.Build;
import com.example.cifixer.store.BuildStatus;
import com.example.cifixer.store.Plan;
import com.example.cifixer.store.PlanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlannerAgentTest {
    
    @Mock
    private PlanRepository planRepository;
    
    @InjectMocks
    private PlannerAgent plannerAgent;
    
    private Build testBuild;
    private Task testTask;
    
    @BeforeEach
    void setUp() {
        testBuild = new Build();
        testBuild.setId(1L);
        testBuild.setJob("test-job");
        testBuild.setBuildNumber(123);
        testBuild.setBranch("main");
        testBuild.setRepoUrl("https://github.com/test/repo.git");
        testBuild.setCommitSha("abc123");
        testBuild.setStatus(BuildStatus.PROCESSING);
        
        testTask = new Task(testBuild, TaskType.PLAN);
    }
    
    @Test
    void shouldParseMavenCompilerError() {
        String logs = "[ERROR] /home/user/project/src/main/java/com/example/service/UserService.java:[15,8] cannot find symbol: class UserRepository";
        
        List<ErrorInfo> errors = plannerAgent.parseSpringProjectLogs(logs);
        
        assertThat(errors).hasSize(1);
        ErrorInfo error = errors.get(0);
        assertThat(error.getErrorType()).isEqualTo(ErrorType.MISSING_DEPENDENCY);
        assertThat(error.getFilePath()).isEqualTo("src/main/java/com/example/service/UserService.java");
        assertThat(error.getLineNumber()).isEqualTo(15);
        assertThat(error.getMissingDependency()).isEqualTo("UserRepository");
    }
    
    @Test
    void shouldParseGradleCompilerError() {
        String logs = "/home/user/project/src/main/java/com/example/controller/UserController.java:25: error: cannot find symbol class UserService";
        
        List<ErrorInfo> errors = plannerAgent.parseSpringProjectLogs(logs);
        
        assertThat(errors).hasSize(1);
        ErrorInfo error = errors.get(0);
        assertThat(error.getErrorType()).isEqualTo(ErrorType.MISSING_DEPENDENCY);
        assertThat(error.getFilePath()).isEqualTo("src/main/java/com/example/controller/UserController.java");
        assertThat(error.getLineNumber()).isEqualTo(25);
        assertThat(error.getMissingDependency()).isEqualTo("UserService");
    }
    
    @Test
    void shouldParseSpringContextFailure() {
        String logs = "org.springframework.beans.factory.NoSuchBeanDefinitionException: No qualifying bean of type 'com.example.repository.UserRepository' available";
        
        List<ErrorInfo> errors = plannerAgent.parseSpringProjectLogs(logs);
        
        assertThat(errors).hasSize(1);
        ErrorInfo error = errors.get(0);
        assertThat(error.getErrorType()).isEqualTo(ErrorType.SPRING_CONTEXT_ERROR);
        assertThat(error.getMissingBean()).isEqualTo("com.example.repository.UserRepository");
        assertThat(error.getErrorMessage()).contains("Missing Spring bean");
    }
    
    @Test
    void shouldParseAutowiringError() {
        String logs = "Could not autowire field: private com.example.service.UserService com.example.controller.UserController.userService";
        
        List<ErrorInfo> errors = plannerAgent.parseSpringProjectLogs(logs);
        
        assertThat(errors).hasSize(1);
        ErrorInfo error = errors.get(0);
        assertThat(error.getErrorType()).isEqualTo(ErrorType.SPRING_ANNOTATION_ERROR);
        assertThat(error.getMissingBean()).isEqualTo("com.example.service.UserService");
    }
    
    @Test
    void shouldParseJavaStackTrace() {
        String logs = "at com.example.service.UserService.findUser(UserService.java:123)\n" +
                     "at com.example.controller.UserController.getUser(UserController.java:45)";
        
        List<ErrorInfo> errors = plannerAgent.parseSpringProjectLogs(logs);
        
        assertThat(errors).hasSize(2);
        
        ErrorInfo firstError = errors.get(0);
        assertThat(firstError.getErrorType()).isEqualTo(ErrorType.STACK_TRACE_ERROR);
        assertThat(firstError.getFilePath()).isEqualTo("src/main/java/com/example/service/UserService.java");
        assertThat(firstError.getLineNumber()).isEqualTo(123);
        assertThat(firstError.getStackTrace()).contains("UserService.findUser");
        
        ErrorInfo secondError = errors.get(1);
        assertThat(secondError.getErrorType()).isEqualTo(ErrorType.STACK_TRACE_ERROR);
        assertThat(secondError.getFilePath()).isEqualTo("src/main/java/com/example/controller/UserController.java");
        assertThat(secondError.getLineNumber()).isEqualTo(45);
    }
    
    @Test
    void shouldParseMavenDependencyError() {
        String logs = "Could not resolve dependencies for project com.example:user-service:jar:1.0.0";
        
        List<ErrorInfo> errors = plannerAgent.parseSpringProjectLogs(logs);
        
        assertThat(errors).hasSize(1);
        ErrorInfo error = errors.get(0);
        assertThat(error.getErrorType()).isEqualTo(ErrorType.DEPENDENCY_RESOLUTION_ERROR);
        assertThat(error.getMissingDependency()).isEqualTo("com.example:user-service:jar:1.0.0");
    }
    
    @Test
    void shouldParseGradleDependencyError() {
        String logs = "Could not resolve org.springframework.boot:spring-boot-starter-web:2.7.0";
        
        List<ErrorInfo> errors = plannerAgent.parseSpringProjectLogs(logs);
        
        assertThat(errors).hasSize(1);
        ErrorInfo error = errors.get(0);
        assertThat(error.getErrorType()).isEqualTo(ErrorType.DEPENDENCY_RESOLUTION_ERROR);
        assertThat(error.getMissingDependency()).isEqualTo("org.springframework.boot:spring-boot-starter-web:2.7.0");
    }
    
    @Test
    void shouldParseTestFailure() {
        String logs = "FAILED: com.example.service.UserServiceTest.testFindUser";
        
        List<ErrorInfo> errors = plannerAgent.parseSpringProjectLogs(logs);
        
        assertThat(errors).hasSize(1);
        ErrorInfo error = errors.get(0);
        assertThat(error.getErrorType()).isEqualTo(ErrorType.TEST_FAILURE);
        assertThat(error.getFailedTest()).isEqualTo("com.example.service.UserServiceTest.testFindUser");
        assertThat(error.getFilePath()).isEqualTo("src/main/java/com/example/service/UserServiceTest.java");
    }
    
    @Test
    void shouldHandleMultipleErrorTypes() {
        String logs = "[ERROR] /src/main/java/com/example/User.java:[15,8] cannot find symbol: class UserRepository\n" +
                     "NoSuchBeanDefinitionException: No qualifying bean of type 'com.example.service.UserService'\n" +
                     "FAILED: com.example.UserTest.testCreateUser\n" +
                     "at com.example.service.UserService.save(UserService.java:89)";
        
        List<ErrorInfo> errors = plannerAgent.parseSpringProjectLogs(logs);
        
        assertThat(errors).hasSize(4);
        
        // Verify error types are correctly identified
        assertThat(errors.stream().map(ErrorInfo::getErrorType))
            .containsExactlyInAnyOrder(
                ErrorType.MISSING_DEPENDENCY,
                ErrorType.SPRING_CONTEXT_ERROR,
                ErrorType.TEST_FAILURE,
                ErrorType.STACK_TRACE_ERROR
            );
    }
    
    @Test
    void shouldLimitLogParsingToLast300Lines() {
        // Create logs with more than 300 lines
        StringBuilder logsBuilder = new StringBuilder();
        for (int i = 1; i <= 350; i++) {
            logsBuilder.append("Line ").append(i).append("\n");
        }
        // Add error in the last part (should be detected)
        logsBuilder.append("[ERROR] /src/main/java/User.java:[15,8] cannot find symbol: class UserRepository\n");
        
        // Add error in the first part (should be ignored due to 300-line limit)
        String logs = "[ERROR] /src/main/java/Old.java:[10,5] syntax error\n" + logsBuilder.toString();
        
        List<ErrorInfo> errors = plannerAgent.parseSpringProjectLogs(logs);
        
        // Should only find the error in the last 300 lines
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).getFilePath()).contains("User.java");
    }
    
    @Test
    void shouldCreateSuccessfulPlanWithErrors() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("buildLogs", "[ERROR] /src/main/java/User.java:[15,8] cannot find symbol: class UserRepository");
        
        when(planRepository.save(any(Plan.class))).thenAnswer(invocation -> {
            Plan plan = invocation.getArgument(0);
            plan.setId(1L);
            return plan;
        });
        
        TaskResult result = plannerAgent.handle(testTask, payload);
        
        assertThat(result.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(result.getMessage()).isEqualTo("Plan created successfully");
        assertThat(result.getMetadata()).containsKey("planId");
        assertThat(result.getMetadata()).containsKey("errorCount");
        assertThat(result.getMetadata().get("errorCount")).isEqualTo(1);
        
        // Verify plan was saved
        ArgumentCaptor<Plan> planCaptor = ArgumentCaptor.forClass(Plan.class);
        verify(planRepository).save(planCaptor.capture());
        
        Plan savedPlan = planCaptor.getValue();
        assertThat(savedPlan.getBuild()).isEqualTo(testBuild);
        assertThat(savedPlan.getPlanJson()).containsKey("steps");
        assertThat(savedPlan.getPlanJson()).containsKey("summary");
        assertThat(savedPlan.getPlanJson()).containsKey("errorCount");
    }
    
    @Test
    void shouldCreateManualInvestigationPlanWhenNoErrorsFound() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("buildLogs", "Some generic build output without recognizable errors");
        
        when(planRepository.save(any(Plan.class))).thenAnswer(invocation -> {
            Plan plan = invocation.getArgument(0);
            plan.setId(1L);
            return plan;
        });
        
        TaskResult result = plannerAgent.handle(testTask, payload);
        
        assertThat(result.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(result.getMessage()).isEqualTo("Manual investigation plan created");
        assertThat(result.getMetadata()).containsKey("requiresManualInvestigation");
        assertThat(result.getMetadata().get("requiresManualInvestigation")).isEqualTo(true);
        
        // Verify manual investigation plan was saved
        ArgumentCaptor<Plan> planCaptor = ArgumentCaptor.forClass(Plan.class);
        verify(planRepository).save(planCaptor.capture());
        
        Plan savedPlan = planCaptor.getValue();
        Map<String, Object> planData = savedPlan.getPlanJson();
        assertThat(planData.get("summary")).toString().contains("Manual investigation required");
        assertThat(planData.get("errorCount")).isEqualTo(0);
    }
    
    @Test
    void shouldHandleEmptyBuildLogs() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("buildLogs", "");
        
        TaskResult result = plannerAgent.handle(testTask, payload);
        
        assertThat(result.getStatus()).isEqualTo(TaskStatus.FAILED);
        assertThat(result.getMessage()).isEqualTo("No build logs found in payload");
        
        verify(planRepository, never()).save(any(Plan.class));
    }
    
    @Test
    void shouldHandleMissingBuildLogs() {
        Map<String, Object> payload = new HashMap<>();
        // No buildLogs key
        
        TaskResult result = plannerAgent.handle(testTask, payload);
        
        assertThat(result.getStatus()).isEqualTo(TaskStatus.FAILED);
        assertThat(result.getMessage()).isEqualTo("No build logs found in payload");
        
        verify(planRepository, never()).save(any(Plan.class));
    }
    
    @Test
    void shouldHandleRepositoryException() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("buildLogs", "[ERROR] /src/main/java/User.java:[15,8] cannot find symbol: class UserRepository");
        
        when(planRepository.save(any(Plan.class))).thenThrow(new RuntimeException("Database error"));
        
        TaskResult result = plannerAgent.handle(testTask, payload);
        
        assertThat(result.getStatus()).isEqualTo(TaskStatus.FAILED);
        assertThat(result.getMessage()).contains("Failed to create plan");
    }
    
    @Test
    void shouldPrioritizeSpringContextErrorsFirst() {
        String logs = "FAILED: com.example.UserTest.testCreateUser\n" +
                     "[ERROR] /src/main/java/User.java:[15,8] cannot find symbol: class UserRepository\n" +
                     "NoSuchBeanDefinitionException: No qualifying bean of type 'com.example.service.UserService'";
        
        List<ErrorInfo> errors = plannerAgent.parseSpringProjectLogs(logs);
        
        assertThat(errors).hasSize(3);
        // Spring context error should be first due to priority
        assertThat(errors.get(0).getErrorType()).isEqualTo(ErrorType.SPRING_CONTEXT_ERROR);
        assertThat(errors.get(1).getErrorType()).isEqualTo(ErrorType.MISSING_DEPENDENCY);
        assertThat(errors.get(2).getErrorType()).isEqualTo(ErrorType.TEST_FAILURE);
    }
    
    @Test
    void shouldGenerateSpringSpecificPlanSteps() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("buildLogs", "NoSuchBeanDefinitionException: No qualifying bean of type 'com.example.repository.UserRepository'");
        
        when(planRepository.save(any(Plan.class))).thenAnswer(invocation -> {
            Plan plan = invocation.getArgument(0);
            plan.setId(1L);
            return plan;
        });
        
        TaskResult result = plannerAgent.handle(testTask, payload);
        
        assertThat(result.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        
        ArgumentCaptor<Plan> planCaptor = ArgumentCaptor.forClass(Plan.class);
        verify(planRepository).save(planCaptor.capture());
        
        Plan savedPlan = planCaptor.getValue();
        Map<String, Object> planData = savedPlan.getPlanJson();
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> steps = (List<Map<String, Object>>) planData.get("steps");
        assertThat(steps).hasSize(1);
        
        Map<String, Object> step = steps.get(0);
        assertThat(step.get("action")).isEqualTo("ADD_SPRING_ANNOTATION");
        assertThat(step.get("description")).toString().contains("Spring context error");
        assertThat(step.get("springComponents")).toString().contains("@Component");
    }
}