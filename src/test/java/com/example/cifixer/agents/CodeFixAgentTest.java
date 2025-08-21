package com.example.cifixer.agents;

import com.example.cifixer.core.Task;
import com.example.cifixer.core.TaskResult;
import com.example.cifixer.core.TaskStatus;
import com.example.cifixer.core.TaskType;
import com.example.cifixer.llm.LlmClient;
import com.example.cifixer.llm.LlmException;
import com.example.cifixer.store.Build;
import com.example.cifixer.store.CandidateFile;
import com.example.cifixer.store.CandidateFileRepository;
import com.example.cifixer.store.Patch;
import com.example.cifixer.store.PatchRepository;
import com.example.cifixer.util.BuildTool;
import com.example.cifixer.util.SpringProjectAnalyzer;
import com.example.cifixer.util.SpringProjectContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CodeFixAgent.
 */
@ExtendWith(MockitoExtension.class)
class CodeFixAgentTest {
    
    @Mock
    private LlmClient llmClient;
    
    @Mock
    private SpringProjectAnalyzer springProjectAnalyzer;
    
    @Mock
    private PatchRepository patchRepository;
    
    @Mock
    private CandidateFileRepository candidateFileRepository;
    
    private CodeFixAgent codeFixAgent;
    
    @TempDir
    Path tempDir;
    
    private Build testBuild;
    private Task testTask;
    private SpringProjectContext testSpringContext;
    
    @BeforeEach
    void setUp() {
        codeFixAgent = new CodeFixAgent(llmClient, springProjectAnalyzer, patchRepository, candidateFileRepository);
        
        testBuild = new Build();
        testBuild.setId(123L);
        testBuild.setJob("test-job");
        testBuild.setBuildNumber(456);
        
        testTask = new Task(testBuild, TaskType.PATCH);
        testTask.setId(789L);
        
        testSpringContext = new SpringProjectContext();
        testSpringContext.setSpringBootVersion("2.7.0");
        testSpringContext.setBuildTool(BuildTool.MAVEN);
        testSpringContext.setMavenModules(Arrays.asList("root"));
        testSpringContext.setSpringAnnotations(Set.of("@Service", "@Repository", "@Controller"));
        testSpringContext.setDependencies(Map.of("spring-boot-starter", "2.7.0"));
    }
    
    @Test
    void shouldSuccessfullyProcessPatchTask() throws Exception {
        // Setup working directory with Git repo
        Path workingDir = setupWorkingDirectory();
        
        // Setup test Java file
        Path javaFile = workingDir.resolve("src/main/java/com/example/UserService.java");
        Files.createDirectories(javaFile.getParent());
        Files.writeString(javaFile, 
            "package com.example;\n" +
            "public class UserService {\n" +
            "    // Missing @Service annotation\n" +
            "}\n");
        
        // Setup payload
        Map<String, Object> payload = createTestPayload(workingDir);
        
        // Mock dependencies
        when(springProjectAnalyzer.analyzeProject(workingDir.toString()))
            .thenReturn(testSpringContext);
        
        String expectedDiff = 
            "--- a/src/main/java/com/example/UserService.java\n" +
            "+++ b/src/main/java/com/example/UserService.java\n" +
            "@@ -1,4 +1,5 @@\n" +
            " package com.example;\n" +
            "+import org.springframework.stereotype.Service;\n" +
            "+@Service\n" +
            " public class UserService {\n" +
            "     // Missing @Service annotation\n" +
            " }\n";
        
        when(llmClient.generatePatch(anyString(), anyString()))
            .thenReturn(expectedDiff);
        
        when(patchRepository.save(any(Patch.class)))
            .thenAnswer(invocation -> {
                Patch patch = invocation.getArgument(0);
                patch.setId(1L);
                return patch;
            });
        
        // Execute
        TaskResult result = codeFixAgent.handle(testTask, payload);
        
        // Verify
        assertThat(result.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(result.getMessage()).contains("Code fixes generated and applied successfully");
        assertThat(result.getMetadata()).containsKey("patchesGenerated");
        assertThat(result.getMetadata()).containsKey("patchesApplied");
        assertThat(result.getMetadata()).containsKey("commitMessage");
        
        verify(llmClient).generatePatch(anyString(), eq("src/main/java/com/example/UserService.java"));
        verify(patchRepository).save(any(Patch.class));
        verify(springProjectAnalyzer).analyzeProject(workingDir.toString());
    }
    
    @Test
    void shouldHandleLlmFailureWithRetry() throws Exception {
        // Setup working directory
        Path workingDir = setupWorkingDirectory();
        
        // Setup test Java file
        Path javaFile = workingDir.resolve("src/main/java/com/example/UserService.java");
        Files.createDirectories(javaFile.getParent());
        Files.writeString(javaFile, "public class UserService {}");
        
        // Setup payload
        Map<String, Object> payload = createTestPayload(workingDir);
        
        // Mock dependencies
        when(springProjectAnalyzer.analyzeProject(workingDir.toString()))
            .thenReturn(testSpringContext);
        
        // First two calls fail, third succeeds
        when(llmClient.generatePatch(anyString(), anyString()))
            .thenThrow(new LlmException("Network error", "NETWORK_ERROR", true))
            .thenThrow(new LlmException("Temporary failure", "TEMP_ERROR", true))
            .thenReturn("--- a/src/main/java/com/example/UserService.java\n+++ b/src/main/java/com/example/UserService.java\n@@ -1,1 +1,2 @@\n+import org.springframework.stereotype.Service;\n+@Service\n public class UserService {}");
        
        when(patchRepository.save(any(Patch.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        
        // Execute
        TaskResult result = codeFixAgent.handle(testTask, payload);
        
        // Verify
        assertThat(result.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        verify(llmClient, times(3)).generatePatch(anyString(), anyString());
    }
    
    @Test
    void shouldFailWhenWorkingDirectoryDoesNotExist() {
        // Setup payload with non-existent directory
        Map<String, Object> payload = new HashMap<>();
        payload.put("buildId", 123L);
        payload.put("workingDirectory", "/non/existent/path");
        payload.put("candidateFiles", Collections.emptyList());
        
        // Execute
        TaskResult result = codeFixAgent.handle(testTask, payload);
        
        // Verify
        assertThat(result.getStatus()).isEqualTo(TaskStatus.FAILED);
        assertThat(result.getMessage()).contains("Working directory does not exist");
    }
    
    @Test
    void shouldFailWhenNoPatchesCanBeGenerated() throws Exception {
        // Setup working directory
        Path workingDir = setupWorkingDirectory();
        
        // Setup test Java file
        Path javaFile = workingDir.resolve("src/main/java/com/example/UserService.java");
        Files.createDirectories(javaFile.getParent());
        Files.writeString(javaFile, "public class UserService {}");
        
        // Setup payload
        Map<String, Object> payload = createTestPayload(workingDir);
        
        // Mock dependencies
        when(springProjectAnalyzer.analyzeProject(workingDir.toString()))
            .thenReturn(testSpringContext);
        
        // LLM always fails
        when(llmClient.generatePatch(anyString(), anyString()))
            .thenThrow(new LlmException("Permanent failure", "PERMANENT_ERROR", false));
        
        // Execute
        TaskResult result = codeFixAgent.handle(testTask, payload);
        
        // Verify
        assertThat(result.getStatus()).isEqualTo(TaskStatus.FAILED);
        assertThat(result.getMessage()).contains("No patches could be generated or applied successfully");
    }
    
    @Test
    void shouldHandleMavenPomFile() throws Exception {
        // Setup working directory
        Path workingDir = setupWorkingDirectory();
        
        // Setup pom.xml file
        Path pomFile = workingDir.resolve("pom.xml");
        Files.writeString(pomFile, 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<project>\n" +
            "    <dependencies>\n" +
            "    </dependencies>\n" +
            "</project>");
        
        // Setup payload with pom.xml as candidate file
        Map<String, Object> payload = createTestPayloadWithFile(workingDir, "pom.xml");
        
        // Mock dependencies
        when(springProjectAnalyzer.analyzeProject(workingDir.toString()))
            .thenReturn(testSpringContext);
        
        String expectedDiff = 
            "--- a/pom.xml\n" +
            "+++ b/pom.xml\n" +
            "@@ -2,5 +2,8 @@\n" +
            " <project>\n" +
            "     <dependencies>\n" +
            "+        <dependency>\n" +
            "+            <groupId>org.springframework.boot</groupId>\n" +
            "+            <artifactId>spring-boot-starter-web</artifactId>\n" +
            "+        </dependency>\n" +
            "     </dependencies>\n" +
            " </project>";
        
        when(llmClient.generatePatch(anyString(), eq("pom.xml")))
            .thenReturn(expectedDiff);
        
        when(patchRepository.save(any(Patch.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        
        // Execute
        TaskResult result = codeFixAgent.handle(testTask, payload);
        
        // Verify
        assertThat(result.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        verify(llmClient).generatePatch(contains("Maven pom.xml"), eq("pom.xml"));
    }
    
    @Test
    void shouldHandleGradleBuildFile() throws Exception {
        // Setup working directory
        Path workingDir = setupWorkingDirectory();
        
        // Setup build.gradle file
        Path gradleFile = workingDir.resolve("build.gradle");
        Files.writeString(gradleFile, 
            "dependencies {\n" +
            "}");
        
        // Setup payload with build.gradle as candidate file
        Map<String, Object> payload = createTestPayloadWithFile(workingDir, "build.gradle");
        
        // Mock dependencies
        testSpringContext.setBuildTool(BuildTool.GRADLE);
        when(springProjectAnalyzer.analyzeProject(workingDir.toString()))
            .thenReturn(testSpringContext);
        
        String expectedDiff = 
            "--- a/build.gradle\n" +
            "+++ b/build.gradle\n" +
            "@@ -1,2 +1,3 @@\n" +
            " dependencies {\n" +
            "+    implementation 'org.springframework.boot:spring-boot-starter-web'\n" +
            " }";
        
        when(llmClient.generatePatch(anyString(), eq("build.gradle")))
            .thenReturn(expectedDiff);
        
        when(patchRepository.save(any(Patch.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        
        // Execute
        TaskResult result = codeFixAgent.handle(testTask, payload);
        
        // Verify
        assertThat(result.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        verify(llmClient).generatePatch(contains("Gradle build.gradle"), eq("build.gradle"));
    }
    
    @Test
    void shouldSkipFilesExceedingSizeLimit() throws Exception {
        // Setup working directory
        Path workingDir = setupWorkingDirectory();
        
        // Setup large Java file (over 50KB)
        Path javaFile = workingDir.resolve("src/main/java/com/example/LargeService.java");
        Files.createDirectories(javaFile.getParent());
        
        StringBuilder largeContent = new StringBuilder();
        largeContent.append("public class LargeService {\n");
        for (int i = 0; i < 2000; i++) {
            largeContent.append("    // This is a very long comment line to make the file exceed the size limit for LLM context processing\n");
        }
        largeContent.append("}");
        
        Files.writeString(javaFile, largeContent.toString());
        
        // Setup payload
        Map<String, Object> payload = createTestPayloadWithFile(workingDir, "src/main/java/com/example/LargeService.java");
        
        // Mock dependencies
        when(springProjectAnalyzer.analyzeProject(workingDir.toString()))
            .thenReturn(testSpringContext);
        
        // Execute
        TaskResult result = codeFixAgent.handle(testTask, payload);
        
        // Verify - should fail because no patches could be generated
        assertThat(result.getStatus()).isEqualTo(TaskStatus.FAILED);
        assertThat(result.getMessage()).contains("No patches could be generated or applied successfully");
        
        // LLM should not be called for oversized files
        verify(llmClient, never()).generatePatch(anyString(), anyString());
    }
    
    @Test
    void shouldGenerateCommitMessageWithSpringComponents() throws Exception {
        // Setup working directory
        Path workingDir = setupWorkingDirectory();
        
        // Setup test Java file
        Path javaFile = workingDir.resolve("src/main/java/com/example/UserService.java");
        Files.createDirectories(javaFile.getParent());
        Files.writeString(javaFile, "public class UserService {}");
        
        // Setup payload
        Map<String, Object> payload = createTestPayload(workingDir);
        payload.put("projectName", "test-project");
        
        // Mock dependencies
        when(springProjectAnalyzer.analyzeProject(workingDir.toString()))
            .thenReturn(testSpringContext);
        
        when(llmClient.generatePatch(anyString(), anyString()))
            .thenReturn("--- a/src/main/java/com/example/UserService.java\n+++ b/src/main/java/com/example/UserService.java\n@@ -1,1 +1,2 @@\n+@Service\n public class UserService {}");
        
        when(patchRepository.save(any(Patch.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        
        // Execute
        TaskResult result = codeFixAgent.handle(testTask, payload);
        
        // Verify
        assertThat(result.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        
        String commitMessage = (String) result.getMetadata().get("commitMessage");
        assertThat(commitMessage).contains("Fix: Jenkins build #123");
        assertThat(commitMessage).contains("test-project");
        assertThat(commitMessage).contains("Spring Boot 2.7.0");
        assertThat(commitMessage).contains("Build Tool: MAVEN");
        assertThat(commitMessage).contains("Spring Components:");
        assertThat(commitMessage).contains("@Service");
        assertThat(commitMessage).contains("@Repository");
        assertThat(commitMessage).contains("@Controller");
        assertThat(commitMessage).contains("Multi-Agent CI Fixer");
    }
    
    private Path setupWorkingDirectory() throws Exception {
        Path workingDir = tempDir.resolve("build-123");
        Files.createDirectories(workingDir);
        
        // Create .git directory to simulate Git repository
        Path gitDir = workingDir.resolve(".git");
        Files.createDirectories(gitDir);
        
        return workingDir;
    }
    
    private Map<String, Object> createTestPayload(Path workingDir) {
        return createTestPayloadWithFile(workingDir, "src/main/java/com/example/UserService.java");
    }
    
    private Map<String, Object> createTestPayloadWithFile(Path workingDir, String filePath) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("buildId", 123L);
        payload.put("workingDirectory", workingDir.toString());
        payload.put("projectName", "test-project");
        payload.put("errorContext", "Compilation error: cannot find symbol UserService");
        payload.put("repoUrl", "https://github.com/example/test-repo.git");
        payload.put("branch", "main");
        payload.put("commitSha", "abc123");
        
        // Create candidate files list
        List<Map<String, Object>> candidateFiles = new ArrayList<>();
        Map<String, Object> candidateFile = new HashMap<>();
        candidateFile.put("filePath", filePath);
        candidateFile.put("rankScore", 0.95);
        candidateFile.put("reason", "Stack trace points to this file");
        candidateFiles.add(candidateFile);
        
        payload.put("candidateFiles", candidateFiles);
        
        return payload;
    }
}