package com.example.cifixer.store;

import com.example.cifixer.core.Task;
import com.example.cifixer.core.TaskStatus;
import com.example.cifixer.core.TaskType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for database schema and JPA entities.
 */
@DataJpaTest
@ActiveProfiles("test")
class DatabaseIntegrationTest {
    
    @Autowired
    private BuildRepository buildRepository;
    
    @Autowired
    private TaskRepository taskRepository;
    
    @Autowired
    private PlanRepository planRepository;
    
    @Autowired
    private CandidateFileRepository candidateFileRepository;
    
    @Autowired
    private PatchRepository patchRepository;
    
    @Autowired
    private ValidationRepository validationRepository;
    
    @Autowired
    private PullRequestRepository pullRequestRepository;
    
    @Test
    void shouldCreateAndRetrieveBuild() {
        // Given
        Build build = new Build("test-job", 123, "main", "https://github.com/test/repo.git", "abc123");
        build.setStatus(BuildStatus.PROCESSING);
        
        Map<String, Object> payload = new HashMap<>();
        payload.put("logs", "Build failed with compilation error");
        build.setPayload(payload);
        
        // When
        Build savedBuild = buildRepository.save(build);
        
        // Then
        assertThat(savedBuild.getId()).isNotNull();
        assertThat(savedBuild.getJob()).isEqualTo("test-job");
        assertThat(savedBuild.getBuildNumber()).isEqualTo(123);
        assertThat(savedBuild.getStatus()).isEqualTo(BuildStatus.PROCESSING);
        assertThat(savedBuild.getPayload()).containsEntry("logs", "Build failed with compilation error");
        
        Optional<Build> retrieved = buildRepository.findByJobAndBuildNumber("test-job", 123);
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().getCommitSha()).isEqualTo("abc123");
    }
    
    @Test
    void shouldCreateAndRetrieveTask() {
        // Given
        Build build = buildRepository.save(new Build("test-job", 124, "main", "https://github.com/test/repo.git", "def456"));
        
        Task task = new Task(build, TaskType.PLAN);
        task.setStatus(TaskStatus.PENDING);
        
        Map<String, Object> payload = new HashMap<>();
        payload.put("errorType", "compilation");
        task.setPayload(payload);
        
        // When
        Task savedTask = taskRepository.save(task);
        
        // Then
        assertThat(savedTask.getId()).isNotNull();
        assertThat(savedTask.getBuild().getId()).isEqualTo(build.getId());
        assertThat(savedTask.getType()).isEqualTo(TaskType.PLAN);
        assertThat(savedTask.getStatus()).isEqualTo(TaskStatus.PENDING);
        assertThat(savedTask.getAttempt()).isEqualTo(0);
        assertThat(savedTask.getMaxAttempts()).isEqualTo(3);
        
        Optional<Task> retrieved = taskRepository.findByBuildIdAndType(build.getId(), TaskType.PLAN);
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().getPayload()).containsEntry("errorType", "compilation");
    }
    
    @Test
    void shouldCreateAndRetrievePlan() {
        // Given
        Build build = buildRepository.save(new Build("test-job", 125, "main", "https://github.com/test/repo.git", "ghi789"));
        
        Map<String, Object> planJson = new HashMap<>();
        planJson.put("steps", "Fix compilation error in UserService.java");
        planJson.put("priority", "high");
        
        Plan plan = new Plan(build, planJson);
        
        // When
        Plan savedPlan = planRepository.save(plan);
        
        // Then
        assertThat(savedPlan.getId()).isNotNull();
        assertThat(savedPlan.getBuild().getId()).isEqualTo(build.getId());
        assertThat(savedPlan.getPlanJson()).containsEntry("steps", "Fix compilation error in UserService.java");
        
        Optional<Plan> retrieved = planRepository.findByBuildId(build.getId());
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().getPlanJson()).containsEntry("priority", "high");
    }
    
    @Test
    void shouldCreateAndRetrieveCandidateFile() {
        // Given
        Build build = buildRepository.save(new Build("test-job", 126, "main", "https://github.com/test/repo.git", "jkl012"));
        
        CandidateFile candidateFile = new CandidateFile(
            build, 
            "src/main/java/com/example/UserService.java", 
            new BigDecimal("95.50"), 
            "Stack trace points to this file"
        );
        
        // When
        CandidateFile savedFile = candidateFileRepository.save(candidateFile);
        
        // Then
        assertThat(savedFile.getId()).isNotNull();
        assertThat(savedFile.getBuild().getId()).isEqualTo(build.getId());
        assertThat(savedFile.getFilePath()).isEqualTo("src/main/java/com/example/UserService.java");
        assertThat(savedFile.getRankScore()).isEqualByComparingTo(new BigDecimal("95.50"));
        assertThat(savedFile.getReason()).isEqualTo("Stack trace points to this file");
        
        List<CandidateFile> retrieved = candidateFileRepository.findByBuildIdOrderByRankScoreDesc(build.getId());
        assertThat(retrieved).hasSize(1);
        assertThat(retrieved.get(0).getFilePath()).contains("UserService.java");
    }
    
    @Test
    void shouldCreateAndRetrievePatch() {
        // Given
        Build build = buildRepository.save(new Build("test-job", 127, "main", "https://github.com/test/repo.git", "mno345"));
        
        String unifiedDiff = "--- a/src/main/java/com/example/UserService.java\n" +
            "+++ b/src/main/java/com/example/UserService.java\n" +
            "@@ -10,6 +10,7 @@\n" +
            " \n" +
            " @Service\n" +
            " public class UserService {\n" +
            "+    @Autowired\n" +
            "     private UserRepository userRepository;";
        
        Patch patch = new Patch(build, "src/main/java/com/example/UserService.java", unifiedDiff);
        patch.setApplied(true);
        patch.setApplyLog("Patch applied successfully");
        
        // When
        Patch savedPatch = patchRepository.save(patch);
        
        // Then
        assertThat(savedPatch.getId()).isNotNull();
        assertThat(savedPatch.getBuild().getId()).isEqualTo(build.getId());
        assertThat(savedPatch.getFilePath()).isEqualTo("src/main/java/com/example/UserService.java");
        assertThat(savedPatch.getUnifiedDiff()).contains("@Autowired");
        assertThat(savedPatch.getApplied()).isTrue();
        
        List<Patch> retrieved = patchRepository.findByBuildIdAndAppliedTrue(build.getId());
        assertThat(retrieved).hasSize(1);
        assertThat(retrieved.get(0).getApplyLog()).isEqualTo("Patch applied successfully");
    }
    
    @Test
    void shouldCreateAndRetrieveValidation() {
        // Given
        Build build = buildRepository.save(new Build("test-job", 128, "main", "https://github.com/test/repo.git", "pqr678"));
        
        Validation validation = new Validation(build, ValidationType.COMPILE, 0, "Compilation successful", "");
        
        // When
        Validation savedValidation = validationRepository.save(validation);
        
        // Then
        assertThat(savedValidation.getId()).isNotNull();
        assertThat(savedValidation.getBuild().getId()).isEqualTo(build.getId());
        assertThat(savedValidation.getValidationType()).isEqualTo(ValidationType.COMPILE);
        assertThat(savedValidation.getExitCode()).isEqualTo(0);
        assertThat(savedValidation.isSuccessful()).isTrue();
        
        List<Validation> retrieved = validationRepository.findSuccessfulByBuildId(build.getId());
        assertThat(retrieved).hasSize(1);
        assertThat(retrieved.get(0).getStdout()).isEqualTo("Compilation successful");
    }
    
    @Test
    void shouldCreateAndRetrievePullRequest() {
        // Given
        Build build = buildRepository.save(new Build("test-job", 129, "main", "https://github.com/test/repo.git", "stu901"));
        
        PullRequest pullRequest = new PullRequest(build, "ci-fix/129", 42, "https://github.com/test/repo/pull/42");
        pullRequest.setStatus(PullRequestStatus.CREATED);
        
        // When
        PullRequest savedPr = pullRequestRepository.save(pullRequest);
        
        // Then
        assertThat(savedPr.getId()).isNotNull();
        assertThat(savedPr.getBuild().getId()).isEqualTo(build.getId());
        assertThat(savedPr.getBranchName()).isEqualTo("ci-fix/129");
        assertThat(savedPr.getPrNumber()).isEqualTo(42);
        assertThat(savedPr.getPrUrl()).isEqualTo("https://github.com/test/repo/pull/42");
        assertThat(savedPr.getStatus()).isEqualTo(PullRequestStatus.CREATED);
        
        Optional<PullRequest> retrieved = pullRequestRepository.findByBuildId(build.getId());
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().getBranchName()).isEqualTo("ci-fix/129");
    }
    
    @Test
    void shouldTestRepositoryQueries() {
        // Given
        Build build1 = buildRepository.save(new Build("job1", 100, "main", "https://github.com/test/repo1.git", "abc"));
        Build build2 = buildRepository.save(new Build("job2", 101, "develop", "https://github.com/test/repo2.git", "def"));
        build1.setStatus(BuildStatus.PROCESSING);
        build2.setStatus(BuildStatus.COMPLETED);
        buildRepository.save(build1);
        buildRepository.save(build2);
        
        Task task1 = taskRepository.save(new Task(build1, TaskType.PLAN));
        Task task2 = taskRepository.save(new Task(build1, TaskType.RETRIEVE));
        task1.setStatus(TaskStatus.PENDING);
        task2.setStatus(TaskStatus.COMPLETED);
        taskRepository.save(task1);
        taskRepository.save(task2);
        
        // When & Then
        assertThat(buildRepository.countByStatus(BuildStatus.PROCESSING)).isEqualTo(1);
        assertThat(buildRepository.countByStatus(BuildStatus.COMPLETED)).isEqualTo(1);
        
        assertThat(taskRepository.countByStatus(TaskStatus.PENDING)).isEqualTo(1);
        assertThat(taskRepository.countByStatus(TaskStatus.COMPLETED)).isEqualTo(1);
        
        List<Build> processingBuilds = buildRepository.findByStatus(BuildStatus.PROCESSING);
        assertThat(processingBuilds).hasSize(1);
        assertThat(processingBuilds.get(0).getJob()).isEqualTo("job1");
        
        List<Task> pendingTasks = taskRepository.findByStatusAndType(TaskStatus.PENDING, TaskType.PLAN);
        assertThat(pendingTasks).hasSize(1);
        assertThat(pendingTasks.get(0).getBuild().getId()).isEqualTo(build1.getId());
    }
}