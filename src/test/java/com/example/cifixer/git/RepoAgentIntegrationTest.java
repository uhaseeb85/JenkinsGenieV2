package com.example.cifixer.git;

import com.example.cifixer.core.Task;
import com.example.cifixer.core.TaskResult;
import com.example.cifixer.core.TaskStatus;
import com.example.cifixer.core.TaskType;
import com.example.cifixer.store.Build;
import com.example.cifixer.store.BuildRepository;
import com.example.cifixer.util.SpringProjectAnalyzer;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration test for RepoAgent with real Git repositories.
 */
@SpringBootTest
@ActiveProfiles("test")
class RepoAgentIntegrationTest {
    
    @Autowired
    private BuildRepository buildRepository;
    
    @Autowired
    private SpringProjectAnalyzer springProjectAnalyzer;
    
    private RepoAgent repoAgent;
    
    @TempDir
    Path tempDir;
    
    private Path testRepoPath;
    private String testRepoUrl;
    
    @BeforeEach
    void setUp() throws IOException, GitAPIException {
        // Create RepoAgent with test configuration
        repoAgent = new RepoAgent(springProjectAnalyzer);
        
        // Set up test repository
        testRepoPath = tempDir.resolve("test-repo");
        createTestSpringRepository();
        testRepoUrl = testRepoPath.toUri().toString();
    }
    
    @AfterEach
    void tearDown() {
        // Cleanup is handled by @TempDir
    }
    
    @Test
    void shouldCloneRepositoryAndCreateWorkingDirectory() {
        // Given
        Build build = buildRepository.save(new Build("test-job", 100, "main", testRepoUrl, "HEAD"));
        Task task = new Task(build, TaskType.REPO);
        
        Map<String, Object> payload = createRepoPayload(testRepoUrl, "main", null, build.getId());
        
        // When
        TaskResult result = repoAgent.handle(task, payload);
        
        // Then
        assertThat(result.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(result.getMessage()).isEqualTo("Repository prepared successfully");
        
        Map<String, Object> metadata = result.getMetadata();
        assertThat(metadata).containsKey("workingDirectory");
        assertThat(metadata).containsKey("fixBranch");
        assertThat(metadata).containsKey("buildTool");
        assertThat(metadata).containsKey("springBootVersion");
        
        String workingDir = (String) metadata.get("workingDirectory");
        assertThat(Files.exists(Paths.get(workingDir))).isTrue();
        assertThat(Files.exists(Paths.get(workingDir, "pom.xml"))).isTrue();
        assertThat(Files.exists(Paths.get(workingDir, ".git"))).isTrue();
        
        String fixBranch = (String) metadata.get("fixBranch");
        assertThat(fixBranch).isEqualTo("ci-fix/" + build.getId());
    }
    
    @Test
    void shouldValidateSpringProjectStructure() {
        // Given
        Build build = buildRepository.save(new Build("test-job", 101, "main", testRepoUrl, "HEAD"));
        Task task = new Task(build, TaskType.REPO);
        
        Map<String, Object> payload = createRepoPayload(testRepoUrl, "main", null, build.getId());
        
        // When
        TaskResult result = repoAgent.handle(task, payload);
        
        // Then
        assertThat(result.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        
        Map<String, Object> metadata = result.getMetadata();
        assertThat(metadata.get("buildTool")).isEqualTo("MAVEN");
        assertThat(metadata.get("springBootVersion")).isNotNull();
        assertThat(metadata).containsKey("mavenModules");
    }
    
    @Test
    void shouldCreateFixBranchWithCorrectNaming() throws IOException, GitAPIException {
        // Given
        Build build = buildRepository.save(new Build("test-job", 102, "main", testRepoUrl, "HEAD"));
        Task task = new Task(build, TaskType.REPO);
        
        Map<String, Object> payload = createRepoPayload(testRepoUrl, "main", null, build.getId());
        
        // When
        TaskResult result = repoAgent.handle(task, payload);
        
        // Then
        assertThat(result.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        
        String workingDir = (String) result.getMetadata().get("workingDirectory");
        String expectedBranch = "ci-fix/" + build.getId();
        
        // Verify branch exists and is checked out
        Repository repository = new FileRepositoryBuilder()
                .setGitDir(new File(workingDir, ".git"))
                .build();
        
        try (Git git = new Git(repository)) {
            String currentBranch = git.getRepository().getBranch();
            assertThat(currentBranch).isEqualTo(expectedBranch);
        }
    }
    
    @Test
    void shouldHandleNonExistentRepository() {
        // Given
        Build build = buildRepository.save(new Build("test-job", 103, "main", "https://github.com/nonexistent/repo.git", "HEAD"));
        Task task = new Task(build, TaskType.REPO);
        
        Map<String, Object> payload = createRepoPayload("https://github.com/nonexistent/repo.git", "main", null, build.getId());
        
        // When
        TaskResult result = repoAgent.handle(task, payload);
        
        // Then
        assertThat(result.getStatus()).isEqualTo(TaskStatus.FAILED);
        assertThat(result.getMessage()).contains("Failed to prepare repository");
    }
    
    @Test
    void shouldHandleInvalidProjectStructure() throws IOException, GitAPIException {
        // Given - Create a repository without Maven/Gradle files
        Path invalidRepoPath = tempDir.resolve("invalid-repo");
        Git.init().setDirectory(invalidRepoPath.toFile()).call();
        
        // Create a simple text file instead of build files
        Files.write(invalidRepoPath.resolve("README.txt"), "This is not a Spring project".getBytes());
        
        Build build = buildRepository.save(new Build("test-job", 104, "main", invalidRepoPath.toUri().toString(), "HEAD"));
        Task task = new Task(build, TaskType.REPO);
        
        Map<String, Object> payload = createRepoPayload(invalidRepoPath.toUri().toString(), "main", null, build.getId());
        
        // When
        TaskResult result = repoAgent.handle(task, payload);
        
        // Then
        assertThat(result.getStatus()).isEqualTo(TaskStatus.FAILED);
        assertThat(result.getMessage()).contains("No Maven (pom.xml) or Gradle (build.gradle) build file found");
    }
    
    @Test
    void shouldCommitAndPushChanges() throws IOException, GitAPIException {
        // Given
        Build build = buildRepository.save(new Build("test-job", 105, "main", testRepoUrl, "HEAD"));
        Task task = new Task(build, TaskType.REPO);
        
        Map<String, Object> payload = createRepoPayload(testRepoUrl, "main", null, build.getId());
        TaskResult result = repoAgent.handle(task, payload);
        
        String workingDir = (String) result.getMetadata().get("workingDirectory");
        String fixBranch = (String) result.getMetadata().get("fixBranch");
        
        // Make a change to the repository
        Path testFile = Paths.get(workingDir, "test-change.txt");
        Files.write(testFile, "This is a test change".getBytes());
        
        // When
        repoAgent.commitChanges(workingDir, "Test commit message", "Test Author", "test@example.com");
        
        // Then
        Repository repository = new FileRepositoryBuilder()
                .setGitDir(new File(workingDir, ".git"))
                .build();
        
        try (Git git = new Git(repository)) {
            // Verify commit was created
            assertThat(git.log().setMaxCount(1).call().iterator().hasNext()).isTrue();
            
            // Verify the commit message
            String lastCommitMessage = git.log().setMaxCount(1).call().iterator().next().getFullMessage();
            assertThat(lastCommitMessage).isEqualTo("Test commit message");
        }
    }
    
    @Test
    void shouldCleanupWorkingDirectory() {
        // Given
        Build build = buildRepository.save(new Build("test-job", 106, "main", testRepoUrl, "HEAD"));
        Task task = new Task(build, TaskType.REPO);
        
        Map<String, Object> payload = createRepoPayload(testRepoUrl, "main", null, build.getId());
        TaskResult result = repoAgent.handle(task, payload);
        
        String workingDir = (String) result.getMetadata().get("workingDirectory");
        assertThat(Files.exists(Paths.get(workingDir))).isTrue();
        
        // When
        repoAgent.cleanupWorkingDirectory(build.getId());
        
        // Then
        assertThat(Files.exists(Paths.get(workingDir))).isFalse();
    }
    
    @Test
    void shouldCheckWorkingDirectoryExists() {
        // Given
        Build build = buildRepository.save(new Build("test-job", 107, "main", testRepoUrl, "HEAD"));
        
        // Initially should not exist
        assertThat(repoAgent.workingDirectoryExists(build.getId())).isFalse();
        
        // After processing task, should exist
        Task task = new Task(build, TaskType.REPO);
        Map<String, Object> payload = createRepoPayload(testRepoUrl, "main", null, build.getId());
        repoAgent.handle(task, payload);
        
        assertThat(repoAgent.workingDirectoryExists(build.getId())).isTrue();
    }
    
    @Test
    void shouldGetWorkingDirectoryPath() {
        // Given
        Long buildId = 999L;
        
        // When
        String workingDir = repoAgent.getWorkingDirectory(buildId);
        
        // Then
        assertThat(workingDir).contains("build-" + buildId);
        assertThat(workingDir).contains("cifixer");
    }
    
    /**
     * Creates a test Spring Boot repository with proper structure.
     */
    private void createTestSpringRepository() throws IOException, GitAPIException {
        // Initialize Git repository
        Git.init().setDirectory(testRepoPath.toFile()).call();
        
        // Create Maven pom.xml
        String pomContent = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<project xmlns=\"http://maven.apache.org/POM/4.0.0\"\n" +
            "         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
            "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 \n" +
            "         http://maven.apache.org/xsd/maven-4.0.0.xsd\">\n" +
            "    <modelVersion>4.0.0</modelVersion>\n" +
            "    \n" +
            "    <parent>\n" +
            "        <groupId>org.springframework.boot</groupId>\n" +
            "        <artifactId>spring-boot-starter-parent</artifactId>\n" +
            "        <version>2.7.18</version>\n" +
            "        <relativePath/>\n" +
            "    </parent>\n" +
            "    \n" +
            "    <groupId>com.example</groupId>\n" +
            "    <artifactId>test-spring-project</artifactId>\n" +
            "    <version>1.0.0</version>\n" +
            "    \n" +
            "    <properties>\n" +
            "        <java.version>1.8</java.version>\n" +
            "    </properties>\n" +
            "    \n" +
            "    <dependencies>\n" +
            "        <dependency>\n" +
            "            <groupId>org.springframework.boot</groupId>\n" +
            "            <artifactId>spring-boot-starter-web</artifactId>\n" +
            "        </dependency>\n" +
            "    </dependencies>\n" +
            "</project>\n";
        
        Files.write(testRepoPath.resolve("pom.xml"), pomContent.getBytes());
        
        // Create source directory structure
        Path srcMainJava = testRepoPath.resolve("src/main/java/com/example");
        Files.createDirectories(srcMainJava);
        
        // Create a simple Spring Boot application class
        String appContent = "package com.example;\n" +
            "\n" +
            "import org.springframework.boot.SpringApplication;\n" +
            "import org.springframework.boot.autoconfigure.SpringBootApplication;\n" +
            "\n" +
            "@SpringBootApplication\n" +
            "public class TestApplication {\n" +
            "    public static void main(String[] args) {\n" +
            "        SpringApplication.run(TestApplication.class, args);\n" +
            "    }\n" +
            "}\n";
        
        Files.write(srcMainJava.resolve("TestApplication.java"), appContent.getBytes());
        
        // Create a simple service class
        String serviceContent = "package com.example;\n" +
            "\n" +
            "import org.springframework.stereotype.Service;\n" +
            "\n" +
            "@Service\n" +
            "public class TestService {\n" +
            "    public String getMessage() {\n" +
            "        return \"Hello from TestService\";\n" +
            "    }\n" +
            "}\n";
        
        Files.write(srcMainJava.resolve("TestService.java"), serviceContent.getBytes());
        
        // Create test directory structure
        Path srcTestJava = testRepoPath.resolve("src/test/java/com/example");
        Files.createDirectories(srcTestJava);
        
        String testContent = "package com.example;\n" +
            "\n" +
            "import org.junit.jupiter.api.Test;\n" +
            "import org.springframework.boot.test.context.SpringBootTest;\n" +
            "\n" +
            "@SpringBootTest\n" +
            "class TestApplicationTests {\n" +
            "    @Test\n" +
            "    void contextLoads() {\n" +
            "    }\n" +
            "}\n";
        
        Files.write(srcTestJava.resolve("TestApplicationTests.java"), testContent.getBytes());
        
        // Commit initial files
        try (Git git = Git.open(testRepoPath.toFile())) {
            git.add().addFilepattern(".").call();
            git.commit()
                .setMessage("Initial commit")
                .setAuthor("Test Author", "test@example.com")
                .call();
        }
    }
    
    /**
     * Creates a payload map for repository operations.
     */
    private Map<String, Object> createRepoPayload(String repoUrl, String branch, String commitSha, Long buildId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("repoUrl", repoUrl);
        payload.put("branch", branch);
        payload.put("commitSha", commitSha);
        payload.put("buildId", buildId);
        return payload;
    }
}