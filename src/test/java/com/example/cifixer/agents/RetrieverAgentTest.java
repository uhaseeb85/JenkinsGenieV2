package com.example.cifixer.agents;

import com.example.cifixer.core.Task;
import com.example.cifixer.core.TaskResult;
import com.example.cifixer.core.TaskStatus;
import com.example.cifixer.core.TaskType;
import com.example.cifixer.store.*;
import com.example.cifixer.util.BuildTool;
import com.example.cifixer.util.SpringProjectAnalyzer;
import com.example.cifixer.util.SpringProjectContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RetrieverAgentTest {
    
    @Mock
    private CandidateFileRepository candidateFileRepository;
    
    @Mock
    private PlanRepository planRepository;
    
    @Mock
    private SpringProjectAnalyzer springProjectAnalyzer;
    
    private RetrieverAgent retrieverAgent;
    
    @TempDir
    Path tempDir;
    
    private Build testBuild;
    private Task testTask;
    
    @BeforeEach
    void setUp() {
        retrieverAgent = new RetrieverAgent(candidateFileRepository, planRepository, springProjectAnalyzer);
        ReflectionTestUtils.setField(retrieverAgent, "workingDirBase", tempDir.toString());
        
        testBuild = new Build();
        testBuild.setId(1L);
        testBuild.setJob("test-job");
        testBuild.setBuildNumber(123);
        testBuild.setBranch("main");
        testBuild.setRepoUrl("https://github.com/test/repo.git");
        testBuild.setCommitSha("abc123");
        testBuild.setStatus(BuildStatus.PROCESSING);
        
        testTask = new Task();
        testTask.setId(1L);
        testTask.setBuild(testBuild);
        testTask.setType(TaskType.RETRIEVE);
        testTask.setStatus(TaskStatus.PENDING);
    }
    
    @Test
    void shouldFailWhenNoPlanExists() {
        // Given
        when(planRepository.findByBuildId(1L)).thenReturn(Optional.empty());
        
        Map<String, Object> payload = new HashMap<>();
        
        // When
        TaskResult result = retrieverAgent.handle(testTask, payload);
        
        // Then
        assertThat(result.getStatus()).isEqualTo(TaskStatus.FAILED);
        assertThat(result.getMessage()).contains("No plan found");
    }
    
    @Test
    void shouldSuccessfullyProcessRetrievalTask() throws IOException {
        // Given
        setupTestProject();
        setupMockPlan();
        setupMockSpringContext();
        
        Map<String, Object> payload = new HashMap<>();
        
        // When
        TaskResult result = retrieverAgent.handle(testTask, payload);
        
        // Then
        assertThat(result.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(result.getMessage()).contains("Retrieved");
        assertThat(result.getMetadata()).containsKey("candidateCount");
        assertThat(result.getMetadata()).containsKey("contextWindow");
        
        verify(candidateFileRepository).deleteByBuildId(1L);
        verify(candidateFileRepository, atLeastOnce()).save(any(CandidateFile.class));
    }
    
    @Test
    void shouldPrioritizeSpringAnnotatedFiles() throws IOException {
        // Given
        setupTestProjectWithSpringComponents();
        setupMockPlan();
        setupMockSpringContext();
        
        Map<String, Object> payload = new HashMap<>();
        
        // When
        TaskResult result = retrieverAgent.handle(testTask, payload);
        
        // Then
        assertThat(result.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        
        ArgumentCaptor<CandidateFile> candidateCaptor = ArgumentCaptor.forClass(CandidateFile.class);
        verify(candidateFileRepository, atLeastOnce()).save(candidateCaptor.capture());
        
        List<CandidateFile> savedCandidates = candidateCaptor.getAllValues();
        
        // Find the Spring Boot application file
        Optional<CandidateFile> springBootApp = savedCandidates.stream()
            .filter(c -> c.getFilePath().equals("src/main/java/com/example/Application.java"))
            .findFirst();
        
        assertThat(springBootApp).isPresent();
        assertThat(springBootApp.get().getRankScore()).isGreaterThan(new BigDecimal("50.0"));
        assertThat(springBootApp.get().getReason()).contains("Spring component");
        assertThat(springBootApp.get().getReason()).contains("@SpringBootApplication");
    }
    
    private void setupTestProject() throws IOException {
        Path buildDir = tempDir.resolve("1");
        Files.createDirectories(buildDir);
        
        // Create pom.xml
        Files.write(buildDir.resolve("pom.xml"), 
            "<?xml version=\"1.0\"?><project></project>".getBytes());
        
        // Create src structure
        Path srcDir = buildDir.resolve("src/main/java/com/example");
        Files.createDirectories(srcDir);
        
        // Create a simple Java file
        Files.write(srcDir.resolve("Application.java"), 
            "package com.example;\npublic class Application {}".getBytes());
    }
    
    private void setupTestProjectWithSpringComponents() throws IOException {
        Path buildDir = tempDir.resolve("1");
        Files.createDirectories(buildDir);
        
        // Create pom.xml
        Files.write(buildDir.resolve("pom.xml"), 
            "<?xml version=\"1.0\"?><project></project>".getBytes());
        
        // Create src structure
        Path srcDir = buildDir.resolve("src/main/java/com/example");
        Files.createDirectories(srcDir);
        
        // Create Spring Boot application
        Files.write(srcDir.resolve("Application.java"), 
            ("package com.example;\n" +
             "import org.springframework.boot.SpringApplication;\n" +
             "import org.springframework.boot.autoconfigure.SpringBootApplication;\n" +
             "@SpringBootApplication\n" +
             "public class Application {}").getBytes());
    }
    
    private void setupMockPlan() {
        Plan plan = new Plan();
        plan.setId(1L);
        plan.setBuild(testBuild);
        
        Map<String, Object> planData = new HashMap<>();
        List<Map<String, Object>> steps = new ArrayList<>();
        
        Map<String, Object> step1 = new HashMap<>();
        step1.put("description", "Fix compilation error");
        step1.put("action", "FIX_COMPILATION_ERROR");
        step1.put("targetFile", "src/main/java/com/example/Application.java");
        step1.put("reasoning", "Missing import statement");
        steps.add(step1);
        
        planData.put("steps", steps);
        plan.setPlanJson(planData);
        
        when(planRepository.findByBuildId(1L)).thenReturn(Optional.of(plan));
    }
    
    private void setupMockSpringContext() {
        SpringProjectContext context = new SpringProjectContext();
        context.setSpringBootVersion("2.7.0");
        context.setBuildTool(BuildTool.MAVEN);
        context.setMavenModules(Arrays.asList("core", "web"));
        context.setSpringAnnotations(Set.of("@SpringBootApplication", "@Service", "@Repository"));
        context.setDependencies(Map.of("org.springframework.boot:spring-boot-starter", "2.7.0"));
        context.setActiveProfiles(Arrays.asList("dev"));
        
        when(springProjectAnalyzer.analyzeProject(any())).thenReturn(context);
    }
}