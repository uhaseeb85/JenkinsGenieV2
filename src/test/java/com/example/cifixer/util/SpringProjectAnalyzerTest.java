package com.example.cifixer.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

class SpringProjectAnalyzerTest {
    
    private SpringProjectAnalyzer analyzer;
    
    @TempDir
    Path tempDir;
    
    @BeforeEach
    void setUp() {
        analyzer = new SpringProjectAnalyzer();
    }
    
    @Test
    void shouldDetectMavenBuildTool() throws IOException {
        // Given
        Files.createFile(tempDir.resolve("pom.xml"));
        
        // When
        BuildTool buildTool = analyzer.detectBuildTool(tempDir.toString());
        
        // Then
        assertThat(buildTool).isEqualTo(BuildTool.MAVEN);
    }
    
    @Test
    void shouldDetectGradleBuildTool() throws IOException {
        // Given
        Files.createFile(tempDir.resolve("build.gradle"));
        
        // When
        BuildTool buildTool = analyzer.detectBuildTool(tempDir.toString());
        
        // Then
        assertThat(buildTool).isEqualTo(BuildTool.GRADLE);
    }
    
    @Test
    void shouldDetectGradleKtsBuildTool() throws IOException {
        // Given
        Files.createFile(tempDir.resolve("build.gradle.kts"));
        
        // When
        BuildTool buildTool = analyzer.detectBuildTool(tempDir.toString());
        
        // Then
        assertThat(buildTool).isEqualTo(BuildTool.GRADLE);
    }
    
    @Test
    void shouldThrowExceptionWhenNoBuildFileFound() {
        // When & Then
        assertThatThrownBy(() -> analyzer.detectBuildTool(tempDir.toString()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("No Maven (pom.xml) or Gradle (build.gradle) build file found");
    }
    
    @Test
    void shouldDetectSpringBootVersionFromMavenParent() throws IOException {
        // Given
        String pomContent = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<project xmlns=\"http://maven.apache.org/POM/4.0.0\">\n" +
            "    <parent>\n" +
            "        <groupId>org.springframework.boot</groupId>\n" +
            "        <artifactId>spring-boot-starter-parent</artifactId>\n" +
            "        <version>2.7.8</version>\n" +
            "        <relativePath/>\n" +
            "    </parent>\n" +
            "    <groupId>com.example</groupId>\n" +
            "    <artifactId>demo</artifactId>\n" +
            "    <version>0.0.1-SNAPSHOT</version>\n" +
            "</project>";
        Files.write(tempDir.resolve("pom.xml"), pomContent.getBytes());
        
        // When
        String version = analyzer.detectSpringBootVersion(tempDir.toString(), BuildTool.MAVEN);
        
        // Then
        assertThat(version).isEqualTo("2.7.8");
    }
    
    @Test
    void shouldDetectSpringBootVersionFromMavenProperty() throws IOException {
        // Given
        String pomContent = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<project xmlns=\"http://maven.apache.org/POM/4.0.0\">\n" +
            "    <properties>\n" +
            "        <spring-boot.version>2.7.8</spring-boot.version>\n" +
            "    </properties>\n" +
            "</project>";
        Files.write(tempDir.resolve("pom.xml"), pomContent.getBytes());
        
        // When
        String version = analyzer.detectSpringBootVersion(tempDir.toString(), BuildTool.MAVEN);
        
        // Then
        assertThat(version).isEqualTo("2.7.8");
    }
    
    @Test
    void shouldDetectSpringBootVersionFromGradlePlugin() throws IOException {
        // Given
        String buildGradleContent = "plugins {\n" +
            "    id 'org.springframework.boot' version '2.7.8'\n" +
            "    id 'io.spring.dependency-management' version '1.0.15.RELEASE'\n" +
            "    id 'java'\n" +
            "}";
        Files.write(tempDir.resolve("build.gradle"), buildGradleContent.getBytes());
        
        // When
        String version = analyzer.detectSpringBootVersion(tempDir.toString(), BuildTool.GRADLE);
        
        // Then
        assertThat(version).isEqualTo("2.7.8");
    }
    
    @Test
    void shouldDetectSpringBootVersionFromGradleVariable() throws IOException {
        // Given
        String buildGradleContent = "ext {\n" +
            "    springBootVersion = '2.7.8'\n" +
            "}\n" +
            "\n" +
            "plugins {\n" +
            "    id 'org.springframework.boot' version \"${springBootVersion}\"\n" +
            "}";
        Files.write(tempDir.resolve("build.gradle"), buildGradleContent.getBytes());
        
        // When
        String version = analyzer.detectSpringBootVersion(tempDir.toString(), BuildTool.GRADLE);
        
        // Then
        assertThat(version).isEqualTo("2.7.8");
    }
    
    @Test
    void shouldReturnUnknownWhenSpringBootVersionNotFound() throws IOException {
        // Given
        String pomContent = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<project xmlns=\"http://maven.apache.org/POM/4.0.0\">\n" +
            "    <groupId>com.example</groupId>\n" +
            "    <artifactId>demo</artifactId>\n" +
            "    <version>0.0.1-SNAPSHOT</version>\n" +
            "</project>";
        Files.write(tempDir.resolve("pom.xml"), pomContent.getBytes());
        
        // When
        String version = analyzer.detectSpringBootVersion(tempDir.toString(), BuildTool.MAVEN);
        
        // Then
        assertThat(version).isEqualTo("unknown");
    }
    
    @Test
    void shouldFindMavenModules() throws IOException {
        // Given
        String pomContent = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<project xmlns=\"http://maven.apache.org/POM/4.0.0\">\n" +
            "    <modules>\n" +
            "        <module>core</module>\n" +
            "        <module>web</module>\n" +
            "        <module>data</module>\n" +
            "    </modules>\n" +
            "</project>";
        Files.write(tempDir.resolve("pom.xml"), pomContent.getBytes());
        
        // When
        List<String> modules = analyzer.findMavenModules(tempDir.toString(), BuildTool.MAVEN);
        
        // Then
        assertThat(modules).containsExactly("core", "web", "data");
    }
    
    @Test
    void shouldReturnEmptyModulesForGradle() {
        // When
        List<String> modules = analyzer.findMavenModules(tempDir.toString(), BuildTool.GRADLE);
        
        // Then
        assertThat(modules).isEmpty();
    }
    
    @Test
    void shouldFindSpringAnnotationsInJavaFiles() throws IOException {
        // Given
        Path srcDir = tempDir.resolve("src/main/java/com/example");
        Files.createDirectories(srcDir);
        
        String controllerContent = "package com.example;\n" +
            "\n" +
            "import org.springframework.web.bind.annotation.RestController;\n" +
            "import org.springframework.web.bind.annotation.GetMapping;\n" +
            "\n" +
            "@RestController\n" +
            "public class UserController {\n" +
            "    \n" +
            "    @GetMapping(\"/users\")\n" +
            "    public String getUsers() {\n" +
            "        return \"users\";\n" +
            "    }\n" +
            "}";
        Files.write(srcDir.resolve("UserController.java"), controllerContent.getBytes());
        
        String serviceContent = "package com.example;\n" +
            "\n" +
            "import org.springframework.stereotype.Service;\n" +
            "import org.springframework.beans.factory.annotation.Autowired;\n" +
            "\n" +
            "@Service\n" +
            "public class UserService {\n" +
            "    \n" +
            "    @Autowired\n" +
            "    private UserRepository userRepository;\n" +
            "}";
        Files.write(srcDir.resolve("UserService.java"), serviceContent.getBytes());
        
        // When
        Set<String> annotations = analyzer.findSpringAnnotations(tempDir.toString());
        
        // Then
        assertThat(annotations).containsExactlyInAnyOrder("@RestController", "@Service", "@Autowired");
    }
    
    @Test
    void shouldReturnEmptyAnnotationsWhenNoSrcDirectory() {
        // When
        Set<String> annotations = analyzer.findSpringAnnotations(tempDir.toString());
        
        // Then
        assertThat(annotations).isEmpty();
    }
    
    @Test
    void shouldParseMavenDependencies() throws IOException {
        // Given
        String pomContent = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<project xmlns=\"http://maven.apache.org/POM/4.0.0\">\n" +
            "    <dependencies>\n" +
            "        <dependency>\n" +
            "            <groupId>org.springframework.boot</groupId>\n" +
            "            <artifactId>spring-boot-starter-web</artifactId>\n" +
            "        </dependency>\n" +
            "        <dependency>\n" +
            "            <groupId>org.springframework.boot</groupId>\n" +
            "            <artifactId>spring-boot-starter-data-jpa</artifactId>\n" +
            "            <version>2.7.8</version>\n" +
            "        </dependency>\n" +
            "        <dependency>\n" +
            "            <groupId>com.h2database</groupId>\n" +
            "            <artifactId>h2</artifactId>\n" +
            "            <version>2.1.214</version>\n" +
            "            <scope>test</scope>\n" +
            "        </dependency>\n" +
            "    </dependencies>\n" +
            "</project>";
        Files.write(tempDir.resolve("pom.xml"), pomContent.getBytes());
        
        // When
        Map<String, String> dependencies = analyzer.parseDependencies(tempDir.toString(), BuildTool.MAVEN);
        
        // Then
        assertThat(dependencies).containsEntry("org.springframework.boot:spring-boot-starter-web", "managed");
        assertThat(dependencies).containsEntry("org.springframework.boot:spring-boot-starter-data-jpa", "2.7.8");
        assertThat(dependencies).containsEntry("com.h2database:h2", "2.1.214");
    }
    
    @Test
    void shouldParseGradleDependencies() throws IOException {
        // Given
        String buildGradleContent = "dependencies {\n" +
            "    implementation 'org.springframework.boot:spring-boot-starter-web'\n" +
            "    implementation 'org.springframework.boot:spring-boot-starter-data-jpa:2.7.8'\n" +
            "    testImplementation 'com.h2database:h2:2.1.214'\n" +
            "    compile 'org.apache.commons:commons-lang3:3.12.0'\n" +
            "}";
        Files.write(tempDir.resolve("build.gradle"), buildGradleContent.getBytes());
        
        // When
        Map<String, String> dependencies = analyzer.parseDependencies(tempDir.toString(), BuildTool.GRADLE);
        
        // Then
        assertThat(dependencies).containsEntry("org.springframework.boot:spring-boot-starter-web", "managed");
        assertThat(dependencies).containsEntry("org.springframework.boot:spring-boot-starter-data-jpa", "2.7.8");
        assertThat(dependencies).containsEntry("com.h2database:h2", "2.1.214");
        assertThat(dependencies).containsEntry("org.apache.commons:commons-lang3", "3.12.0");
    }
    
    @Test
    void shouldFindActiveProfilesFromProperties() throws IOException {
        // Given
        Path resourcesDir = tempDir.resolve("src/main/resources");
        Files.createDirectories(resourcesDir);
        
        String propertiesContent = "spring.application.name=demo\n" +
            "spring.profiles.active=dev,test\n" +
            "server.port=8080";
        Files.write(resourcesDir.resolve("application.properties"), propertiesContent.getBytes());
        
        // When
        List<String> profiles = analyzer.findActiveProfiles(tempDir.toString());
        
        // Then
        assertThat(profiles).containsExactly("dev", "test");
    }
    
    @Test
    void shouldFindActiveProfilesFromYml() throws IOException {
        // Given
        Path resourcesDir = tempDir.resolve("src/main/resources");
        Files.createDirectories(resourcesDir);
        
        String ymlContent = "spring:\n" +
            "  application:\n" +
            "    name: demo\n" +
            "  profiles:\n" +
            "    active: prod,monitoring\n" +
            "server:\n" +
            "  port: 8080";
        Files.write(resourcesDir.resolve("application.yml"), ymlContent.getBytes());
        
        // When
        List<String> profiles = analyzer.findActiveProfiles(tempDir.toString());
        
        // Then
        assertThat(profiles).containsExactly("prod", "monitoring");
    }
    
    @Test
    void shouldReturnEmptyProfilesWhenNoConfigFiles() {
        // When
        List<String> profiles = analyzer.findActiveProfiles(tempDir.toString());
        
        // Then
        assertThat(profiles).isEmpty();
    }
    
    @Test
    void shouldAnalyzeCompleteSpringProject() throws IOException {
        // Given - Create a complete Spring Boot project structure
        Files.createFile(tempDir.resolve("pom.xml"));
        String pomContent = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<project xmlns=\"http://maven.apache.org/POM/4.0.0\">\n" +
            "    <parent>\n" +
            "        <groupId>org.springframework.boot</groupId>\n" +
            "        <artifactId>spring-boot-starter-parent</artifactId>\n" +
            "        <version>2.7.8</version>\n" +
            "    </parent>\n" +
            "    <modules>\n" +
            "        <module>core</module>\n" +
            "        <module>web</module>\n" +
            "    </modules>\n" +
            "    <dependencies>\n" +
            "        <dependency>\n" +
            "            <groupId>org.springframework.boot</groupId>\n" +
            "            <artifactId>spring-boot-starter-web</artifactId>\n" +
            "        </dependency>\n" +
            "    </dependencies>\n" +
            "</project>";
        Files.write(tempDir.resolve("pom.xml"), pomContent.getBytes());
        
        // Create Java source with Spring annotations
        Path srcDir = tempDir.resolve("src/main/java/com/example");
        Files.createDirectories(srcDir);
        String javaContent = "@SpringBootApplication\n" +
            "@RestController\n" +
            "public class Application {\n" +
            "    public static void main(String[] args) {}\n" +
            "}";
        Files.write(srcDir.resolve("Application.java"), javaContent.getBytes());
        
        // Create application properties
        Path resourcesDir = tempDir.resolve("src/main/resources");
        Files.createDirectories(resourcesDir);
        Files.write(resourcesDir.resolve("application.properties"), "spring.profiles.active=dev".getBytes());
        
        // When
        SpringProjectContext context = analyzer.analyzeProject(tempDir.toString());
        
        // Then
        assertThat(context.getBuildTool()).isEqualTo(BuildTool.MAVEN);
        assertThat(context.getSpringBootVersion()).isEqualTo("2.7.8");
        assertThat(context.getMavenModules()).containsExactly("core", "web");
        assertThat(context.getSpringAnnotations()).containsExactlyInAnyOrder("@SpringBootApplication", "@RestController");
        assertThat(context.getDependencies()).containsEntry("org.springframework.boot:spring-boot-starter-web", "managed");
        assertThat(context.getActiveProfiles()).containsExactly("dev");
    }
}