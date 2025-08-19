# Design Document

## Overview

The Multi-Agent CI Fixer is implemented as a single Spring Boot 2.7.x application with modular agent-based architecture, specifically designed to fix Java Spring projects. The system uses PostgreSQL for persistence, integrates with a local LLM for Spring-aware code generation, and orchestrates multiple specialized agents to handle different aspects of Maven/Gradle build failures in Spring applications. The design prioritizes simplicity, Java 8 compatibility, Spring best practices, and self-hosted deployment.

## Architecture

### High-Level Architecture

```mermaid
graph TB
    subgraph "External Systems"
        J[Jenkins] --> WH[Webhook API]
        GH[GitHub API]
        LLM[Local LLM<br/>Ollama/LM Studio]
        SMTP[SMTP Server]
    end
    
    subgraph "Spring Boot Application"
        WH --> O[Orchestrator]
        O --> PA[Planner Agent]
        O --> RA[Repo Agent]
        O --> RET[Retriever Agent]
        O --> CFA[Code-Fix Agent]
        O --> VA[Validator Agent]
        O --> PRA[PR Agent]
        O --> NA[Notification Agent]
        
        CFA --> LLM
        PRA --> GH
        NA --> SMTP
    end
    
    subgraph "Data Layer"
        O --> PG[(PostgreSQL)]
        PA --> PG
        RA --> PG
        RET --> PG
        CFA --> PG
        VA --> PG
        PRA --> PG
        NA --> PG
    end
    
    subgraph "File System"
        RA --> WD[Working Directory<br/>/work/{buildId}]
        CFA --> WD
        VA --> WD
    end
```

### Agent-Based Design

The system implements a multi-agent pattern where each agent is responsible for a specific domain:

- **Orchestrator Agent**: Central coordinator that manages task queues and agent dispatch
- **Planner Agent**: Analyzes Maven/Gradle build logs and creates structured fix plans for Spring projects
- **Repo Agent**: Handles Git operations and Maven/Gradle project structure analysis
- **Retriever Agent**: Identifies and ranks candidate Java files, Spring configurations, and build files
- **Code-Fix Agent**: Generates and applies Spring-aware code patches using LLM with Java/Spring context
- **Validator Agent**: Runs Maven/Gradle compilation and Spring Boot tests to validate fixes
- **PR Agent**: Creates GitHub pull requests with generated fixes
- **Notification Agent**: Sends email notifications to stakeholders

## Components and Interfaces

### Core Interfaces

```java
// Base agent interface
public interface Agent<T> {
    TaskResult handle(Task task, T payload);
}

// Task processing result
public class TaskResult {
    private TaskStatus status;
    private String message;
    private Map<String, Object> metadata;
    // getters/setters
}

// Task queue management
public interface TaskQueue {
    void enqueue(Task task);
    Optional<Task> dequeue(String agentType);
    void updateStatus(Long taskId, TaskStatus status);
}
```

### Web Layer

```java
@RestController
@RequestMapping("/webhooks")
public class WebhookController {
    
    @PostMapping("/jenkins")
    public ResponseEntity<WebhookResponse> handleJenkinsFailure(
        @RequestBody JenkinsWebhookPayload payload,
        @RequestHeader("X-Jenkins-Signature") String signature) {
        // Validate HMAC signature
        // Create Build entity
        // Enqueue PLAN task
        // Return build ID
    }
}

@RestController
@RequestMapping("/builds")
public class BuildController {
    
    @GetMapping("/{id}")
    public ResponseEntity<BuildStatus> getBuildStatus(@PathVariable Long id) {
        // Return build status and timeline
    }
    
    @PostMapping("/admin/retry/{taskId}")
    public ResponseEntity<Void> retryTask(@PathVariable Long taskId) {
        // Manual retry for failed tasks
    }
}
```

### Agent Implementations

#### Orchestrator Agent

```java
@Component
public class Orchestrator {
    
    @Scheduled(fixedDelay = 1000)
    public void processTasks() {
        // SELECT FOR UPDATE SKIP LOCKED pattern
        // Dispatch tasks to appropriate agents
        // Handle task state transitions
        // Implement retry logic with exponential backoff
    }
    
    private void dispatchTask(Task task) {
        switch (task.getType()) {
            case PLAN: plannerAgent.handle(task, task.getPayload());
            case RETRIEVE: retrieverAgent.handle(task, task.getPayload());
            // ... other cases
        }
    }
}
```

#### Planner Agent

```java
@Component
public class PlannerAgent implements Agent<PlanPayload> {
    
    public TaskResult handle(Task task, PlanPayload payload) {
        // Parse Jenkins logs using regex patterns
        // Identify error types: compilation, test failures, dependencies
        // Create structured plan with ordered steps
        // Store plan and create RETRIEVE tasks
    }
    
    private List<ErrorInfo> parseSpringProjectLogs(String logs) {
        // Java stack trace parsing: "at com.example.service.UserService.findUser(UserService.java:123)"
        // Maven compiler error parsing: "[ERROR] /src/main/java/com/example/User.java:[15,8] cannot find symbol"
        // Gradle compiler error parsing: "error: cannot find symbol class UserRepository"
        // Spring context failure parsing: "NoSuchBeanDefinitionException: No qualifying bean of type"
        // Spring Boot test failure parsing: "FAILED: com.example.UserServiceTest.testFindUser"
        // Maven dependency error parsing: "Could not resolve dependencies for project"
    }
}
```

#### Code-Fix Agent

```java
@Component
public class CodeFixAgent implements Agent<PatchPayload> {
    
    public TaskResult handle(Task task, PatchPayload payload) {
        // Build context window for LLM
        // Generate prompt with file content and error info
        // Call LLM API and validate response format
        // Apply unified diff using JGit
        // Create VALIDATE task
    }
    
    private String buildSpringAwarePrompt(String filePath, String fileContent, String errorContext, SpringProjectContext springContext) {
        return String.format(SPRING_PROMPT_TEMPLATE, 
            payload.getRepoName(), 
            payload.getMavenModule(), 
            errorContext, 
            filePath, 
            fileContent,
            springContext.getSpringBootVersion(),
            springContext.getRelevantAnnotations(),
            springContext.getDependencyInfo());
    }
    
    private static final String SPRING_PROMPT_TEMPLATE = """
        System: You are a senior Java Spring Boot developer. Fix this Spring Boot %s project issue.
        
        Project: %s
        Maven Module: %s
        Spring Boot Version: %s
        Error: %s
        
        File (%s):
        %s
        
        Relevant Spring Context:
        - Annotations in scope: %s
        - Dependencies: %s
        
        Instructions:
        - Return a minimal unified diff that fixes the Spring-specific error
        - Follow Spring Boot best practices and conventions
        - Use appropriate Spring annotations (@Service, @Repository, @Controller, @Component, etc.)
        - Handle dependency injection properly
        - Maintain Java 8 compatibility
        - Keep changes minimal and focused
        """;
    }
}
```

### LLM Integration

```java
@Component
public class LlmClient {
    
    public LlmResponse generatePatch(String prompt) {
        // HTTP call to local LLM endpoint
        // Validate response contains unified diff
        // Parse JSON response with error handling
        // Apply token limits and safety checks
    }
    
    private boolean isValidUnifiedDiff(String diff) {
        // Validate diff format: starts with "--- a/" and "+++ b/"
        // Check for proper hunk headers: "@@ -n,m +x,y @@"
        // Ensure no dangerous operations (file deletion, binary changes)
    }
}
```

## Data Models

### Database Schema

```sql
-- Core entities
CREATE TABLE builds (
    id BIGSERIAL PRIMARY KEY,
    job VARCHAR(255) NOT NULL,
    build_number INT NOT NULL,
    branch VARCHAR(255) NOT NULL,
    repo_url TEXT NOT NULL,
    commit_sha VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PROCESSING',
    payload JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE plans (
    id BIGSERIAL PRIMARY KEY,
    build_id BIGINT REFERENCES builds(id),
    plan_json JSONB NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE tasks (
    id BIGSERIAL PRIMARY KEY,
    build_id BIGINT REFERENCES builds(id),
    type VARCHAR(32) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    attempt INT NOT NULL DEFAULT 0,
    max_attempts INT NOT NULL DEFAULT 3,
    payload JSONB,
    error_message TEXT,
    created_at TIMESTAMP DEFAULT now(),
    updated_at TIMESTAMP DEFAULT now()
);

CREATE TABLE candidate_files (
    id BIGSERIAL PRIMARY KEY,
    build_id BIGINT REFERENCES builds(id),
    file_path TEXT NOT NULL,
    rank_score DECIMAL(5,2) NOT NULL,
    reason TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT now()
);

CREATE TABLE patches (
    id BIGSERIAL PRIMARY KEY,
    build_id BIGINT REFERENCES builds(id),
    file_path TEXT NOT NULL,
    unified_diff TEXT NOT NULL,
    applied BOOLEAN DEFAULT FALSE,
    apply_log TEXT,
    created_at TIMESTAMP DEFAULT now()
);

CREATE TABLE validations (
    id BIGSERIAL PRIMARY KEY,
    build_id BIGINT REFERENCES builds(id),
    validation_type VARCHAR(32) NOT NULL, -- COMPILE, TEST
    exit_code INT NOT NULL,
    stdout TEXT,
    stderr TEXT,
    created_at TIMESTAMP DEFAULT now()
);

CREATE TABLE pull_requests (
    id BIGSERIAL PRIMARY KEY,
    build_id BIGINT REFERENCES builds(id),
    pr_number INT,
    pr_url TEXT,
    branch_name VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'CREATED',
    created_at TIMESTAMP DEFAULT now()
);

-- Indexes for performance
CREATE INDEX idx_tasks_status_type ON tasks(status, type);
CREATE INDEX idx_builds_status ON builds(status);
CREATE INDEX idx_candidate_files_build_rank ON candidate_files(build_id, rank_score DESC);
```

### Spring Project Context

```java
@Component
public class SpringProjectAnalyzer {
    
    public SpringProjectContext analyzeProject(String workingDir) {
        return SpringProjectContext.builder()
            .springBootVersion(detectSpringBootVersion(workingDir))
            .buildTool(detectBuildTool(workingDir))
            .mavenModules(findMavenModules(workingDir))
            .springAnnotations(findSpringAnnotations(workingDir))
            .dependencies(parseDependencies(workingDir))
            .build();
    }
    
    private String detectSpringBootVersion(String workingDir) {
        // Parse pom.xml or build.gradle for Spring Boot version
    }
    
    private BuildTool detectBuildTool(String workingDir) {
        if (new File(workingDir, "pom.xml").exists()) return BuildTool.MAVEN;
        if (new File(workingDir, "build.gradle").exists()) return BuildTool.GRADLE;
        throw new IllegalStateException("No Maven or Gradle build file found");
    }
}

@Data
@Builder
public class SpringProjectContext {
    private String springBootVersion;
    private BuildTool buildTool;
    private List<String> mavenModules;
    private Set<String> springAnnotations;
    private Map<String, String> dependencies;
    private List<String> activeProfiles;
}

public enum BuildTool {
    MAVEN("mvn clean compile test"),
    GRADLE("./gradlew clean build test");
    
    private final String testCommand;
    
    BuildTool(String testCommand) {
        this.testCommand = testCommand;
    }
    
    public String getTestCommand() {
        return testCommand;
    }
}
```

### JPA Entities

```java
@Entity
@Table(name = "builds")
public class Build {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String job;
    
    @Column(name = "build_number", nullable = false)
    private Integer buildNumber;
    
    @Column(nullable = false)
    private String branch;
    
    @Column(name = "repo_url", nullable = false)
    private String repoUrl;
    
    @Column(name = "commit_sha", nullable = false)
    private String commitSha;
    
    @Enumerated(EnumType.STRING)
    private BuildStatus status;
    
    @Type(type = "jsonb")
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> payload;
    
    // timestamps, getters, setters
}

@Entity
@Table(name = "tasks")
public class Task {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "build_id")
    private Build build;
    
    @Enumerated(EnumType.STRING)
    private TaskType type;
    
    @Enumerated(EnumType.STRING)
    private TaskStatus status;
    
    private Integer attempt;
    
    @Column(name = "max_attempts")
    private Integer maxAttempts;
    
    @Type(type = "jsonb")
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> payload;
    
    // timestamps, getters, setters
}
```

## Error Handling

### Retry Strategy

```java
@Component
public class RetryHandler {
    
    public void handleTaskFailure(Task task, Exception error) {
        if (task.getAttempt() < task.getMaxAttempts()) {
            // Exponential backoff: 2^attempt seconds
            long delaySeconds = (long) Math.pow(2, task.getAttempt());
            scheduleRetry(task, delaySeconds);
        } else {
            markTaskAsFailed(task, error.getMessage());
            notifyManualIntervention(task);
        }
    }
}
```

### Input Validation

```java
@Component
public class WebhookValidator {
    
    public void validateJenkinsPayload(JenkinsWebhookPayload payload, String signature) {
        // HMAC-SHA256 signature validation
        String expectedSignature = calculateHmac(payload, secretKey);
        if (!MessageDigest.isEqual(signature.getBytes(), expectedSignature.getBytes())) {
            throw new SecurityException("Invalid webhook signature");
        }
        
        // Payload validation
        if (payload.getJob() == null || payload.getBuildNumber() == null) {
            throw new ValidationException("Missing required fields");
        }
    }
}
```

### Safety Guardrails

```java
@Component
public class PatchSafetyValidator {
    
    public void validatePatch(String diff, String filePath) {
        // Prevent dangerous operations
        if (diff.contains("rm -rf") || diff.contains("DELETE FROM")) {
            throw new SecurityException("Dangerous operation detected in patch");
        }
        
        // Limit patch size
        if (diff.split("\n").length > MAX_PATCH_LINES) {
            throw new ValidationException("Patch too large");
        }
        
        // Validate file path is within Spring project structure
        if ((!filePath.startsWith("src/main/java/") && 
             !filePath.startsWith("src/test/java/") && 
             !filePath.equals("pom.xml") && 
             !filePath.equals("build.gradle")) || 
            filePath.contains("..")) {
            throw new SecurityException("Invalid file path for Spring project");
        }
        
        // Validate Spring-specific file types
        if (filePath.endsWith(".java")) {
            validateJavaFile(diff);
        } else if (filePath.equals("pom.xml")) {
            validateMavenPom(diff);
        } else if (filePath.equals("build.gradle")) {
            validateGradleBuild(diff);
        }
    }
    
    private void validateJavaFile(String diff) {
        // Ensure proper Java syntax in diff
        // Check for Spring annotation usage
        // Validate import statements
    }
    }
}
```

## Testing Strategy

### Unit Testing

```java
@ExtendWith(MockitoExtension.class)
class PlannerAgentTest {
    
    @Mock
    private TaskRepository taskRepository;
    
    @InjectMocks
    private PlannerAgent plannerAgent;
    
    @Test
    void shouldParseMavenCompilerError() {
        String logs = "[ERROR] /src/main/java/com/example/service/UserService.java:[15,8] cannot find symbol: class UserRepository";
        
        List<ErrorInfo> errors = plannerAgent.parseSpringProjectLogs(logs);
        
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).getFilePath()).isEqualTo("src/main/java/com/example/service/UserService.java");
        assertThat(errors.get(0).getLineNumber()).isEqualTo(15);
        assertThat(errors.get(0).getErrorType()).isEqualTo(ErrorType.MISSING_DEPENDENCY);
    }
    
    @Test
    void shouldParseSpringContextFailure() {
        String logs = "NoSuchBeanDefinitionException: No qualifying bean of type 'com.example.repository.UserRepository'";
        
        List<ErrorInfo> errors = plannerAgent.parseSpringProjectLogs(logs);
        
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).getErrorType()).isEqualTo(ErrorType.SPRING_CONTEXT_ERROR);
        assertThat(errors.get(0).getMissingBean()).isEqualTo("UserRepository");
    }
}
```

### Integration Testing

```java
@SpringBootTest
@Testcontainers
class WebhookIntegrationTest {
    
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:14");
    
    @Autowired
    private TestRestTemplate restTemplate;
    
    @Test
    void shouldProcessJenkinsWebhook() {
        JenkinsWebhookPayload payload = createTestPayload();
        
        ResponseEntity<WebhookResponse> response = restTemplate.postForEntity(
            "/webhooks/jenkins", payload, WebhookResponse.class);
        
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getBuildId()).isNotNull();
    }
}
```

### End-to-End Testing

```java
@SpringBootTest
@DirtiesContext
class EndToEndTest {
    
    @Test
    void shouldFixSpringBootCompilationError() throws Exception {
        // Setup test Spring Boot repository with known compilation error
        // - Missing @Repository annotation on UserRepository
        // - UserService trying to inject UserRepository
        // Send webhook payload with Maven compilation failure
        // Wait for processing to complete
        // Verify PR was created with @Repository annotation added
        // Verify Spring Boot tests pass after fix
        // Verify notifications were sent to commit author
    }
    
    @Test
    void shouldFixMavenDependencyIssue() throws Exception {
        // Setup test repository with missing Spring Boot starter dependency
        // Send webhook payload with Maven dependency resolution failure
        // Wait for processing to complete
        // Verify PR was created with correct dependency added to pom.xml
        // Verify Maven build succeeds after fix
    }
}
```

## Performance Considerations

### Database Optimization

- Use connection pooling (HikariCP)
- Implement proper indexing on frequently queried columns
- Use `SELECT FOR UPDATE SKIP LOCKED` for task queue processing
- Partition large tables by date if needed

### Memory Management

- Limit LLM context window size to prevent OOM
- Stream large log files instead of loading entirely into memory
- Use lazy loading for JPA relationships
- Implement cleanup jobs for old working directories

### Concurrency

- Process tasks concurrently using thread pools
- Implement proper locking for Git operations
- Use database-level locking for task queue management
- Handle race conditions in PR creation

## Security Considerations

### Secret Management

```java
@Component
public class SecretManager {
    
    @Value("${github.token}")
    private String githubToken;
    
    @Value("${jenkins.token}")
    private String jenkinsToken;
    
    // Never log or persist actual token values
    public String getRedactedToken(String token) {
        return token.substring(0, 4) + "****";
    }
}
```

### Input Sanitization

- Validate all webhook inputs
- Sanitize file paths to prevent directory traversal
- Limit LLM prompt size to prevent injection attacks
- Validate unified diff format before application

### Network Security

- Use HTTPS for all external API calls
- Implement rate limiting on webhook endpoints
- Validate SSL certificates for GitHub API calls
- Use least-privilege tokens for GitHub and Jenkins access