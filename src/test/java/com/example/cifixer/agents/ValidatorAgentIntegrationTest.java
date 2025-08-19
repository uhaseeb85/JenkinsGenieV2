package com.example.cifixer.agents;

import com.example.cifixer.core.Task;
import com.example.cifixer.core.TaskResult;
import com.example.cifixer.core.TaskStatus;
import com.example.cifixer.core.TaskType;
import com.example.cifixer.store.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ValidatorAgentIntegrationTest {
    
    @Autowired
    private ValidatorAgent validatorAgent;
    
    @Autowired
    private BuildRepository buildRepository;
    
    @Autowired
    private ValidationRepository validationRepository;
    
    @TempDir
    Path tempDir;
    
    private Build testBuild;
    
    @BeforeEach
    void setUp() {
        testBuild = new Build("test-job", 123, "main", "https://github.com/test/repo", "abc123");
        testBuild = buildRepository.save(testBuild);
    }
    
    @Test
    void shouldValidateSimpleMavenProject() throws IOException {
        // Given - Create a simple Maven project structure
        Path projectDir = createSimpleMavenProject();
        
        Task task = new Task(testBuild, TaskType.VALIDATE);
        ValidatePayload payload = new ValidatePayload(projectDir.toString(), "MAVEN");
        
        // When
        TaskResult result = validatorAgent.handle(task, payload);
        
        // Then
        assertThat(result.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(result.getMessage()).contains("Validation completed successfully");
        
        // Verify validations were stored
        List<Validation> validations = validationRepository.findByBuildIdOrderByCreatedAtDesc(testBuild.getId());
        assertThat(validations).hasSize(2);
        assertThat(validations).extracting(Validation::getValidationType)
                .containsExactlyInAnyOrder(ValidationType.COMPILE, ValidationType.TEST);
        assertThat(validations).allMatch(Validation::isSuccessful);
    }
    
    @Test
    void shouldFailValidationForProjectWithCompilationErrors() throws IOException {
        // Given - Create a Maven project with compilation errors
        Path projectDir = createMavenProjectWithCompilationError();
        
        Task task = new Task(testBuild, TaskType.VALIDATE);
        ValidatePayload payload = new ValidatePayload(projectDir.toString(), "MAVEN");
        
        // When
        TaskResult result = validatorAgent.handle(task, payload);
        
        // Then
        assertThat(result.getStatus()).isEqualTo(TaskStatus.FAILED);
        assertThat(result.getMessage()).contains("Validation failed");
        
        // Verify compilation validation was stored and failed
        List<Validation> validations = validationRepository.findByBuildIdOrderByCreatedAtDesc(testBuild.getId());
        assertThat(validations).hasSize(1);
        assertThat(validations.get(0).getValidationType()).isEqualTo(ValidationType.COMPILE);
        assertThat(validations.get(0).isSuccessful()).isFalse();
        assertThat(validations.get(0).getStderr()).contains("cannot find symbol");
    }
    
    @Test
    void shouldValidateGradleProject() throws IOException {
        // Given - Create a simple Gradle project structure
        Path projectDir = createSimpleGradleProject();
        
        Task task = new Task(testBuild, TaskType.VALIDATE);
        ValidatePayload payload = new ValidatePayload(projectDir.toString(), "GRADLE");
        
        // When
        TaskResult result = validatorAgent.handle(task, payload);
        
        // Then
        assertThat(result.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(result.getMessage()).contains("Validation completed successfully");
        assertThat(result.getMetadata()).containsEntry("buildTool", "GRADLE");
        
        // Verify validations were stored
        List<Validation> validations = validationRepository.findByBuildIdOrderByCreatedAtDesc(testBuild.getId());
        assertThat(validations).hasSize(2);
        assertThat(validations).extracting(Validation::getValidationType)
                .containsExactlyInAnyOrder(ValidationType.COMPILE, ValidationType.TEST);
    }
    
    @Test
    void shouldHandleSpringBootTestFailures() throws IOException {
        // Given - Create a Spring Boot project with test failures
        Path projectDir = createSpringBootProjectWithTestFailure();
        
        Task task = new Task(testBuild, TaskType.VALIDATE);
        task.setAttempt(1);
        task.setMaxAttempts(3);
        ValidatePayload payload = new ValidatePayload(projectDir.toString(), "MAVEN");
        
        // When
        TaskResult result = validatorAgent.handle(task, payload);
        
        // Then - Should retry for Spring context failures
        assertThat(result.getStatus()).isIn(TaskStatus.RETRY, TaskStatus.FAILED);
        
        // Verify both compilation and test validations were attempted
        List<Validation> validations = validationRepository.findByBuildIdOrderByCreatedAtDesc(testBuild.getId());
        assertThat(validations).hasSizeGreaterThanOrEqualTo(1);
    }
    
    private Path createSimpleMavenProject() throws IOException {
        Path projectDir = tempDir.resolve("simple-maven");
        Files.createDirectories(projectDir);
        
        // Create pom.xml
        String pomContent = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<project xmlns=\"http://maven.apache.org/POM/4.0.0\"\n" +
            "         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
            "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 \n" +
            "         http://maven.apache.org/xsd/maven-4.0.0.xsd\">\n" +
            "    <modelVersion>4.0.0</modelVersion>\n" +
            "    <groupId>com.example</groupId>\n" +
            "    <artifactId>test-project</artifactId>\n" +
            "    <version>1.0.0</version>\n" +
            "    <packaging>jar</packaging>\n" +
            "    \n" +
            "    <properties>\n" +
            "        <maven.compiler.source>8</maven.compiler.source>\n" +
            "        <maven.compiler.target>8</maven.compiler.target>\n" +
            "        <spring.boot.version>2.7.0</spring.boot.version>\n" +
            "    </properties>\n" +
            "    \n" +
            "    <dependencies>\n" +
            "        <dependency>\n" +
            "            <groupId>org.springframework.boot</groupId>\n" +
            "            <artifactId>spring-boot-starter</artifactId>\n" +
            "            <version>${spring.boot.version}</version>\n" +
            "        </dependency>\n" +
            "        <dependency>\n" +
            "            <groupId>org.springframework.boot</groupId>\n" +
            "            <artifactId>spring-boot-starter-test</artifactId>\n" +
            "            <version>${spring.boot.version}</version>\n" +
            "            <scope>test</scope>\n" +
            "        </dependency>\n" +
            "    </dependencies>\n" +
            "    \n" +
            "    <build>\n" +
            "        <plugins>\n" +
            "            <plugin>\n" +
            "                <groupId>org.springframework.boot</groupId>\n" +
            "                <artifactId>spring-boot-maven-plugin</artifactId>\n" +
            "                <version>${spring.boot.version}</version>\n" +
            "            </plugin>\n" +
            "        </plugins>\n" +
            "    </build>\n" +
            "</project>";
        Files.write(projectDir.resolve("pom.xml"), pomContent.getBytes());
        
        // Create source directories
        Path srcMain = projectDir.resolve("src/main/java/com/example");
        Path srcTest = projectDir.resolve("src/test/java/com/example");
        Files.createDirectories(srcMain);
        Files.createDirectories(srcTest);
        
        // Create a simple Java class
        String javaContent = "package com.example;\n\n" +
            "public class SimpleClass {\n" +
            "    public String getMessage() {\n" +
            "        return \"Hello World\";\n" +
            "    }\n" +
            "}";
        Files.write(srcMain.resolve("SimpleClass.java"), javaContent.getBytes());
        
        // Create a simple test
        String testContent = "package com.example;\n\n" +
            "import org.junit.jupiter.api.Test;\n" +
            "import static org.junit.jupiter.api.Assertions.assertEquals;\n\n" +
            "public class SimpleClassTest {\n" +
            "    @Test\n" +
            "    public void testGetMessage() {\n" +
            "        SimpleClass simple = new SimpleClass();\n" +
            "        assertEquals(\"Hello World\", simple.getMessage());\n" +
            "    }\n" +
            "}";
        Files.write(srcTest.resolve("SimpleClassTest.java"), testContent.getBytes());
        
        return projectDir;
    }
    
    private Path createMavenProjectWithCompilationError() throws IOException {
        Path projectDir = createSimpleMavenProject();
        
        // Create a Java class with compilation error
        Path srcMain = projectDir.resolve("src/main/java/com/example");
        String errorContent = "package com.example;\n\n" +
            "public class ErrorClass {\n" +
            "    public void method() {\n" +
            "        NonExistentClass obj = new NonExistentClass();\n" +
            "    }\n" +
            "}";
        Files.write(srcMain.resolve("ErrorClass.java"), errorContent.getBytes());
        
        return projectDir;
    }
    
    private Path createSimpleGradleProject() throws IOException {
        Path projectDir = tempDir.resolve("simple-gradle");
        Files.createDirectories(projectDir);
        
        // Create build.gradle
        String buildGradleContent = "plugins {\n" +
            "    id 'java'\n" +
            "    id 'org.springframework.boot' version '2.7.0'\n" +
            "    id 'io.spring.dependency-management' version '1.0.11.RELEASE'\n" +
            "}\n\n" +
            "group = 'com.example'\n" +
            "version = '1.0.0'\n" +
            "sourceCompatibility = '8'\n\n" +
            "repositories {\n" +
            "    mavenCentral()\n" +
            "}\n\n" +
            "dependencies {\n" +
            "    implementation 'org.springframework.boot:spring-boot-starter'\n" +
            "    testImplementation 'org.springframework.boot:spring-boot-starter-test'\n" +
            "}\n\n" +
            "test {\n" +
            "    useJUnitPlatform()\n" +
            "}";
        Files.write(projectDir.resolve("build.gradle"), buildGradleContent.getBytes());
        
        // Create gradle wrapper (simplified)
        Files.write(projectDir.resolve("gradlew"), "#!/bin/bash\necho 'Gradle wrapper not available in test'".getBytes());
        
        // Create source directories and files (same as Maven)
        Path srcMain = projectDir.resolve("src/main/java/com/example");
        Path srcTest = projectDir.resolve("src/test/java/com/example");
        Files.createDirectories(srcMain);
        Files.createDirectories(srcTest);
        
        String javaContent = "package com.example;\n\n" +
            "public class SimpleClass {\n" +
            "    public String getMessage() {\n" +
            "        return \"Hello World\";\n" +
            "    }\n" +
            "}";
        Files.write(srcMain.resolve("SimpleClass.java"), javaContent.getBytes());
        
        String testContent = "package com.example;\n\n" +
            "import org.junit.jupiter.api.Test;\n" +
            "import static org.junit.jupiter.api.Assertions.assertEquals;\n\n" +
            "public class SimpleClassTest {\n" +
            "    @Test\n" +
            "    public void testGetMessage() {\n" +
            "        SimpleClass simple = new SimpleClass();\n" +
            "        assertEquals(\"Hello World\", simple.getMessage());\n" +
            "    }\n" +
            "}";
        Files.write(srcTest.resolve("SimpleClassTest.java"), testContent.getBytes());
        
        return projectDir;
    }
    
    private Path createSpringBootProjectWithTestFailure() throws IOException {
        Path projectDir = createSimpleMavenProject();
        
        // Create a Spring Boot test that will fail due to missing bean
        Path srcTest = projectDir.resolve("src/test/java/com/example");
        String failingTestContent = "package com.example;\n\n" +
            "import org.junit.jupiter.api.Test;\n" +
            "import org.springframework.boot.test.context.SpringBootTest;\n" +
            "import org.springframework.beans.factory.annotation.Autowired;\n\n" +
            "@SpringBootTest\n" +
            "public class FailingSpringTest {\n" +
            "    \n" +
            "    @Autowired\n" +
            "    private NonExistentService service; // This will cause NoSuchBeanDefinitionException\n" +
            "    \n" +
            "    @Test\n" +
            "    public void testService() {\n" +
            "        // This test will fail during context loading\n" +
            "    }\n" +
            "}";
        Files.write(srcTest.resolve("FailingSpringTest.java"), failingTestContent.getBytes());
        
        return projectDir;
    }
}