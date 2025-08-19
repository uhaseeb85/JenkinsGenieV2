package com.example.cifixer.llm;

import com.example.cifixer.util.BuildTool;
import com.example.cifixer.util.SpringProjectContext;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class SpringPromptTemplateTest {
    
    @Test
    void shouldBuildSpringFixPrompt() {
        SpringProjectContext context = new SpringProjectContext(
                "2.7.18",
                BuildTool.MAVEN,
                Arrays.asList("user-service", "common"),
                new HashSet<>(Arrays.asList("@Service", "@Repository", "@Controller")),
                Map.of("spring-boot-starter-web", "2.7.18", "spring-boot-starter-data-jpa", "2.7.18"),
                Arrays.asList("dev", "test")
        );
        
        String prompt = SpringPromptTemplate.buildSpringFixPrompt(
                "user-management",
                "src/main/java/com/example/UserService.java",
                "public class UserService { }",
                "NoSuchBeanDefinitionException: No qualifying bean of type 'UserRepository'",
                context
        );
        
        assertThat(prompt).contains("user-management");
        assertThat(prompt).contains("user-service");
        assertThat(prompt).contains("2.7.18");
        assertThat(prompt).contains("MAVEN");
        assertThat(prompt).contains("NoSuchBeanDefinitionException");
        assertThat(prompt).contains("src/main/java/com/example/UserService.java");
        assertThat(prompt).contains("public class UserService { }");
        assertThat(prompt).contains("@Service, @Repository, @Controller");
        assertThat(prompt).contains("spring-boot-starter-web:2.7.18");
        assertThat(prompt).contains("dev, test");
        assertThat(prompt).contains("unified diff");
        assertThat(prompt).contains("Spring Boot best practices");
    }
    
    @Test
    void shouldBuildMavenPomPrompt() {
        SpringProjectContext context = new SpringProjectContext(
                "2.7.18",
                BuildTool.MAVEN,
                null,
                null,
                Map.of("spring-boot-starter-web", "2.7.18"),
                null
        );
        
        String pomContent = "<dependencies>\n" +
            "    <dependency>\n" +
            "        <groupId>org.springframework.boot</groupId>\n" +
            "        <artifactId>spring-boot-starter-web</artifactId>\n" +
            "    </dependency>\n" +
            "</dependencies>";
        
        String prompt = SpringPromptTemplate.buildMavenPomPrompt(
                "user-management",
                pomContent,
                "Could not resolve dependencies for project",
                context
        );
        
        assertThat(prompt).contains("user-management");
        assertThat(prompt).contains("2.7.18");
        assertThat(prompt).contains("Could not resolve dependencies");
        assertThat(prompt).contains(pomContent);
        assertThat(prompt).contains("spring-boot-starter-web:2.7.18");
        assertThat(prompt).contains("Maven dependency management");
        assertThat(prompt).contains("unified diff");
    }
    
    @Test
    void shouldBuildGradleBuildPrompt() {
        SpringProjectContext context = new SpringProjectContext(
                "2.7.18",
                BuildTool.GRADLE,
                null,
                null,
                Map.of("spring-boot-starter-web", "2.7.18"),
                null
        );
        
        String buildContent = "dependencies {\n" +
            "    implementation 'org.springframework.boot:spring-boot-starter-web'\n" +
            "}";
        
        String prompt = SpringPromptTemplate.buildGradleBuildPrompt(
                "user-management",
                buildContent,
                "Could not resolve all dependencies",
                context
        );
        
        assertThat(prompt).contains("user-management");
        assertThat(prompt).contains("2.7.18");
        assertThat(prompt).contains("Could not resolve all dependencies");
        assertThat(prompt).contains(buildContent);
        assertThat(prompt).contains("spring-boot-starter-web:2.7.18");
        assertThat(prompt).contains("Gradle dependency syntax");
        assertThat(prompt).contains("unified diff");
    }
    
    @Test
    void shouldHandleEmptyDependencies() {
        SpringProjectContext context = new SpringProjectContext(
                "2.7.18",
                BuildTool.MAVEN,
                Collections.singletonList("root"),
                new HashSet<>(),
                Collections.emptyMap(),
                Collections.emptyList()
        );
        
        String prompt = SpringPromptTemplate.buildSpringFixPrompt(
                "simple-project",
                "src/main/java/Main.java",
                "public class Main { }",
                "Compilation error",
                context
        );
        
        assertThat(prompt).contains("None specified");
        assertThat(prompt).contains("root");
        assertThat(prompt).doesNotContain("null");
    }
    
    @Test
    void shouldFormatMultipleDependencies() {
        Map<String, String> dependencies = new LinkedHashMap<>();
        dependencies.put("spring-boot-starter-web", "2.7.18");
        dependencies.put("spring-boot-starter-data-jpa", "2.7.18");
        dependencies.put("postgresql", "42.6.0");
        
        SpringProjectContext context = new SpringProjectContext(
                "2.7.18",
                BuildTool.MAVEN,
                null,
                null,
                dependencies,
                null
        );
        
        String prompt = SpringPromptTemplate.buildSpringFixPrompt(
                "multi-dep-project",
                "src/main/java/Service.java",
                "public class Service { }",
                "Error",
                context
        );
        
        assertThat(prompt).contains("spring-boot-starter-web:2.7.18");
        assertThat(prompt).contains("spring-boot-starter-data-jpa:2.7.18");
        assertThat(prompt).contains("postgresql:42.6.0");
    }
    
    @Test
    void shouldHandleNullValues() {
        SpringProjectContext context = new SpringProjectContext(
                "2.7.18",
                BuildTool.MAVEN,
                null,
                null,
                null,
                null
        );
        
        String prompt = SpringPromptTemplate.buildSpringFixPrompt(
                "null-handling-project",
                "src/main/java/Test.java",
                "public class Test { }",
                "Error",
                context
        );
        
        // Should not throw NPE and should handle nulls gracefully
        assertThat(prompt).isNotNull();
        assertThat(prompt).contains("null-handling-project");
        assertThat(prompt).contains("2.7.18");
    }
}