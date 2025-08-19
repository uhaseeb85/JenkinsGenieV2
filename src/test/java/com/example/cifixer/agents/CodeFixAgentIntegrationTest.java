package com.example.cifixer.agents;

import com.example.cifixer.core.Task;
import com.example.cifixer.core.TaskResult;
import com.example.cifixer.core.TaskStatus;
import com.example.cifixer.core.TaskType;
import com.example.cifixer.llm.LlmClient;
import com.example.cifixer.store.Build;
import com.example.cifixer.store.Patch;
import com.example.cifixer.store.PatchRepository;
import com.example.cifixer.util.SpringProjectAnalyzer;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Integration tests for CodeFixAgent with real patch application scenarios.
 */
@SpringBootTest(classes = com.example.cifixer.MultiAgentCiFixerApplication.class)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.flyway.enabled=false"
})
@Transactional
class CodeFixAgentIntegrationTest {
    
    @Autowired
    private CodeFixAgent codeFixAgent;
    
    @Autowired
    private PatchRepository patchRepository;
    
    @Autowired
    private SpringProjectAnalyzer springProjectAnalyzer;
    
    @MockBean
    private LlmClient llmClient;
    
    @TempDir
    Path tempDir;
    
    private Build testBuild;
    private Task testTask;
    
    @BeforeEach
    void setUp() {
        testBuild = new Build();
        testBuild.setJob("integration-test-job");
        testBuild.setBuildNumber(100);
        testBuild.setBranch("main");
        testBuild.setRepoUrl("https://github.com/example/test-repo.git");
        testBuild.setCommitSha("abc123def456");
        
        testTask = new Task(testBuild, TaskType.PATCH);
    }
    
    @Test
    void shouldApplySpringServiceAnnotationPatch() throws Exception {
        // Setup Spring Boot project structure
        Path workingDir = setupSpringBootProject();
        
        // Create UserService without @Service annotation
        Path userServiceFile = workingDir.resolve("src/main/java/com/example/service/UserService.java");
        Files.createDirectories(userServiceFile.getParent());
        Files.writeString(userServiceFile, 
            "package com.example.service;\n\n" +
            "import com.example.repository.UserRepository;\n\n" +
            "public class UserService {\n" +
            "    \n" +
            "    private final UserRepository userRepository;\n" +
            "    \n" +
            "    public UserService(UserRepository userRepository) {\n" +
            "        this.userRepository = userRepository;\n" +
            "    }\n" +
            "    \n" +
            "    public String findUser(Long id) {\n" +
            "        return \"User: \" + id;\n" +
            "    }\n" +
            "}\n");
        
        // Mock LLM to return a patch that adds @Service annotation
        String expectedPatch = 
            "--- a/src/main/java/com/example/service/UserService.java\n" +
            "+++ b/src/main/java/com/example/service/UserService.java\n" +
            "@@ -1,5 +1,7 @@\n" +
            " package com.example.service;\n" +
            " \n" +
            " import com.example.repository.UserRepository;\n" +
            "+import org.springframework.stereotype.Service;\n" +
            " \n" +
            "+@Service\n" +
            " public class UserService {\n" +
            "     \n" +
            "     private final UserRepository userRepository;\n";
        
        when(llmClient.generatePatch(anyString(), anyString()))
            .thenReturn(expectedPatch);
        
        // Setup payload
        Map<String, Object> payload = createIntegrationTestPayload(workingDir, 
            "src/main/java/com/example/service/UserService.java",
            "NoSuchBeanDefinitionException: No qualifying bean of type 'com.example.service.UserService'");
        
        // Execute
        TaskResult result = codeFixAgent.handle(testTask, payload);
        
        // Verify result
        assertThat(result.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(result.getMessage()).contains("Code fixes generated and applied successfully");
        
        // Verify patch was saved to database
        List<Patch> patches = patchRepository.findByBuildId(testBuild.getId());
        assertThat(patches).hasSize(1);
        
        Patch savedPatch = patches.get(0);
        assertThat(savedPatch.getFilePath()).isEqualTo("src/main/java/com/example/service/UserService.java");
        assertThat(savedPatch.getUnifiedDiff()).isEqualTo(expectedPatch);
        assertThat(savedPatch.getApplied()).isTrue();
        assertThat(savedPatch.getApplyLog()).contains("successfully");
        
        // Verify file was actually modified
        String modifiedContent = Files.readString(userServiceFile);
        assertThat(modifiedContent).contains("import org.springframework.stereotype.Service;");
        assertThat(modifiedContent).contains("@Service");
        assertThat(modifiedContent).contains("public class UserService {");
    }
    
    @Test
    void shouldApplyMavenDependencyPatch() throws Exception {
        // Setup Spring Boot project structure
        Path workingDir = setupSpringBootProject();
        
        // Create pom.xml without required dependency
        Path pomFile = workingDir.resolve("pom.xml");
        Files.writeString(pomFile,
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<project xmlns=\"http://maven.apache.org/POM/4.0.0\"\n" +
            "         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
            "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 \n" +
            "         http://maven.apache.org/xsd/maven-4.0.0.xsd\">\n" +
            "    <modelVersion>4.0.0</modelVersion>\n" +
            "    \n" +
            "    <groupId>com.example</groupId>\n" +
            "    <artifactId>test-project</artifactId>\n" +
            "    <version>1.0.0</version>\n" +
            "    \n" +
            "    <parent>\n" +
            "        <groupId>org.springframework.boot</groupId>\n" +
            "        <artifactId>spring-boot-starter-parent</artifactId>\n" +
            "        <version>2.7.0</version>\n" +
            "    </parent>\n" +
            "    \n" +
            "    <dependencies>\n" +
            "        <dependency>\n" +
            "            <groupId>org.springframework.boot</groupId>\n" +
            "            <artifactId>spring-boot-starter</artifactId>\n" +
            "        </dependency>\n" +
            "    </dependencies>\n" +
            "</project>\n");
        
        // Mock LLM to return a patch that adds web starter dependency
        String expectedPatch = 
            "--- a/pom.xml\n" +
            "+++ b/pom.xml\n" +
            "@@ -17,6 +17,10 @@\n" +
            "         <dependency>\n" +
            "             <groupId>org.springframework.boot</groupId>\n" +
            "             <artifactId>spring-boot-starter</artifactId>\n" +
            "         </dependency>\n" +
            "+        <dependency>\n" +
            "+            <groupId>org.springframework.boot</groupId>\n" +
            "+            <artifactId>spring-boot-starter-web</artifactId>\n" +
            "+        </dependency>\n" +
            "     </dependencies>\n" +
            " </project>";
        
        when(llmClient.generatePatch(anyString(), anyString()))
            .thenReturn(expectedPatch);
        
        // Setup payload
        Map<String, Object> payload = createIntegrationTestPayload(workingDir, 
            "pom.xml",
            "Could not resolve dependencies for project: Missing spring-boot-starter-web");
        
        // Execute
        TaskResult result = codeFixAgent.handle(testTask, payload);
        
        // Verify result
        assertThat(result.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        
        // Verify patch was applied to file
        String modifiedPom = Files.readString(pomFile);
        assertThat(modifiedPom).contains("spring-boot-starter-web");
        
        // Verify patch was saved to database
        List<Patch> patches = patchRepository.findByBuildId(testBuild.getId());
        assertThat(patches).hasSize(1);
        assertThat(patches.get(0).getApplied()).isTrue();
    }
    
    @Test
    void shouldHandleMultipleCandidateFiles() throws Exception {
        // Setup Spring Boot project structure
        Path workingDir = setupSpringBootProject();
        
        // Create UserService without @Service annotation
        Path userServiceFile = workingDir.resolve("src/main/java/com/example/service/UserService.java");
        Files.createDirectories(userServiceFile.getParent());
        Files.writeString(userServiceFile, 
            "package com.example.service;\n\n" +
            "public class UserService {\n" +
            "}\n");
        
        // Create UserRepository without @Repository annotation
        Path userRepositoryFile = workingDir.resolve("src/main/java/com/example/repository/UserRepository.java");
        Files.createDirectories(userRepositoryFile.getParent());
        Files.writeString(userRepositoryFile, 
            "package com.example.repository;\n\n" +
            "public interface UserRepository {\n" +
            "}\n");
        
        // Mock LLM to return different patches for each file
        when(llmClient.generatePatch(anyString(), anyString()))
            .thenAnswer(invocation -> {
                String filePath = invocation.getArgument(1, String.class);
                if (filePath.contains("UserService")) {
                    return "--- a/src/main/java/com/example/service/UserService.java\n" +
                           "+++ b/src/main/java/com/example/service/UserService.java\n" +
                           "@@ -1,3 +1,5 @@\n" +
                           " package com.example.service;\n" +
                           " \n" +
                           "+import org.springframework.stereotype.Service;\n" +
                           "+@Service\n" +
                           " public class UserService {\n";
                } else if (filePath.contains("UserRepository")) {
                    return "--- a/src/main/java/com/example/repository/UserRepository.java\n" +
                           "+++ b/src/main/java/com/example/repository/UserRepository.java\n" +
                           "@@ -1,3 +1,5 @@\n" +
                           " package com.example.repository;\n" +
                           " \n" +
                           "+import org.springframework.stereotype.Repository;\n" +
                           "+@Repository\n" +
                           " public interface UserRepository {\n";
                }
                return null;
            });
        
        // Setup payload with multiple candidate files
        Map<String, Object> payload = createMultiFileTestPayload(workingDir, Arrays.asList(
            new TestCandidateFile("src/main/java/com/example/service/UserService.java", 0.95, "Missing @Service annotation"),
            new TestCandidateFile("src/main/java/com/example/repository/UserRepository.java", 0.90, "Missing @Repository annotation")
        ), "Spring context initialization failed");
        
        // Execute
        TaskResult result = codeFixAgent.handle(testTask, payload);
        
        // Verify result
        assertThat(result.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(result.getMetadata().get("patchesGenerated")).isEqualTo(2);
        assertThat(result.getMetadata().get("patchesApplied")).isEqualTo(2);
        
        // Verify both files were modified
        String userServiceContent = Files.readString(userServiceFile);
        assertThat(userServiceContent).contains("@Service");
        
        String userRepositoryContent = Files.readString(userRepositoryFile);
        assertThat(userRepositoryContent).contains("@Repository");
        
        // Verify both patches were saved to database
        List<Patch> patches = patchRepository.findByBuildId(testBuild.getId());
        assertThat(patches).hasSize(2);
        assertThat(patches).allMatch(Patch::getApplied);
    }
    
    @Test
    void shouldHandlePatchApplicationFailure() throws Exception {
        // Setup Spring Boot project structure
        Path workingDir = setupSpringBootProject();
        
        // Create UserService file
        Path userServiceFile = workingDir.resolve("src/main/java/com/example/service/UserService.java");
        Files.createDirectories(userServiceFile.getParent());
        Files.writeString(userServiceFile, 
            "package com.example.service;\n\n" +
            "public class UserService {\n" +
            "}\n");
        
        // Mock LLM to return an invalid patch that won't apply
        String invalidPatch = 
            "--- a/src/main/java/com/example/service/UserService.java\n" +
            "+++ b/src/main/java/com/example/service/UserService.java\n" +
            "@@ -10,3 +10,5 @@\n" +  // Invalid line numbers
            " package com.example.service;\n" +
            " \n" +
            "+@Service\n" +
            " public class UserService {\n";
        
        when(llmClient.generatePatch(anyString(), anyString()))
            .thenReturn(invalidPatch);
        
        // Setup payload
        Map<String, Object> payload = createIntegrationTestPayload(workingDir, 
            "src/main/java/com/example/service/UserService.java",
            "Missing @Service annotation");
        
        // Execute
        TaskResult result = codeFixAgent.handle(testTask, payload);
        
        // Verify result - should fail because patch couldn't be applied
        assertThat(result.getStatus()).isEqualTo(TaskStatus.FAILED);
        assertThat(result.getMessage()).contains("No patches could be generated or applied successfully");
        
        // Verify patch was saved but marked as not applied
        List<Patch> patches = patchRepository.findByBuildId(testBuild.getId());
        assertThat(patches).hasSize(1);
        
        Patch savedPatch = patches.get(0);
        assertThat(savedPatch.getApplied()).isFalse();
        assertThat(savedPatch.getApplyLog()).contains("Failed to apply patch");
        
        // Verify original file was not modified
        String originalContent = Files.readString(userServiceFile);
        assertThat(originalContent).doesNotContain("@Service");
    }
    
    private Path setupSpringBootProject() throws Exception {
        Path workingDir = tempDir.resolve("build-" + testBuild.getId());
        Files.createDirectories(workingDir);
        
        // Initialize Git repository
        Git.init().setDirectory(workingDir.toFile()).call();
        
        // Create basic Spring Boot project structure
        Files.createDirectories(workingDir.resolve("src/main/java/com/example"));
        Files.createDirectories(workingDir.resolve("src/test/java/com/example"));
        Files.createDirectories(workingDir.resolve("src/main/resources"));
        
        // Create basic pom.xml
        Path pomFile = workingDir.resolve("pom.xml");
        Files.writeString(pomFile,
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<project xmlns=\"http://maven.apache.org/POM/4.0.0\">\n" +
            "    <modelVersion>4.0.0</modelVersion>\n" +
            "    <groupId>com.example</groupId>\n" +
            "    <artifactId>test-project</artifactId>\n" +
            "    <version>1.0.0</version>\n" +
            "    <parent>\n" +
            "        <groupId>org.springframework.boot</groupId>\n" +
            "        <artifactId>spring-boot-starter-parent</artifactId>\n" +
            "        <version>2.7.0</version>\n" +
            "    </parent>\n" +
            "    <dependencies>\n" +
            "        <dependency>\n" +
            "            <groupId>org.springframework.boot</groupId>\n" +
            "            <artifactId>spring-boot-starter</artifactId>\n" +
            "        </dependency>\n" +
            "    </dependencies>\n" +
            "</project>\n");
        
        return workingDir;
    }
    
    private Map<String, Object> createIntegrationTestPayload(Path workingDir, String filePath, String errorContext) {
        return createMultiFileTestPayload(workingDir, 
            Arrays.asList(new TestCandidateFile(filePath, 0.95, "Stack trace points to this file")), 
            errorContext);
    }
    
    private Map<String, Object> createMultiFileTestPayload(Path workingDir, List<TestCandidateFile> candidateFiles, String errorContext) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("buildId", testBuild.getId());
        payload.put("workingDirectory", workingDir.toString());
        payload.put("projectName", "integration-test-project");
        payload.put("errorContext", errorContext);
        payload.put("repoUrl", testBuild.getRepoUrl());
        payload.put("branch", testBuild.getBranch());
        payload.put("commitSha", testBuild.getCommitSha());
        
        // Create candidate files list
        List<Map<String, Object>> candidateFilesList = new ArrayList<>();
        for (TestCandidateFile candidateFile : candidateFiles) {
            Map<String, Object> fileMap = new HashMap<>();
            fileMap.put("filePath", candidateFile.filePath);
            fileMap.put("rankScore", candidateFile.rankScore);
            fileMap.put("reason", candidateFile.reason);
            candidateFilesList.add(fileMap);
        }
        
        payload.put("candidateFiles", candidateFilesList);
        
        return payload;
    }
    
    private static class TestCandidateFile {
        final String filePath;
        final double rankScore;
        final String reason;
        
        TestCandidateFile(String filePath, double rankScore, String reason) {
            this.filePath = filePath;
            this.rankScore = rankScore;
            this.reason = reason;
        }
    }
}