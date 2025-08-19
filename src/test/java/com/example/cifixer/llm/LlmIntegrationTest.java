package com.example.cifixer.llm;

import com.example.cifixer.util.BuildTool;
import com.example.cifixer.util.SpringProjectContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for LLM components working together.
 */
class LlmIntegrationTest {
    
    private MockWebServer mockWebServer;
    private LlmClient llmClient;
    private ObjectMapper objectMapper;
    
    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        
        objectMapper = new ObjectMapper();
        UnifiedDiffValidator diffValidator = new UnifiedDiffValidator();
        llmClient = new LlmClient(objectMapper, diffValidator);
        
        // Set test configuration
        ReflectionTestUtils.setField(llmClient, "llmEndpoint", mockWebServer.url("/api/generate").toString());
        ReflectionTestUtils.setField(llmClient, "defaultModel", "test-model");
        ReflectionTestUtils.setField(llmClient, "maxTokens", 2000);
        ReflectionTestUtils.setField(llmClient, "temperature", 0.1);
        ReflectionTestUtils.setField(llmClient, "timeoutSeconds", 30);
    }
    
    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }
    
    @Test
    void shouldGenerateSpringServiceFix() throws Exception {
        // Setup Spring project context
        SpringProjectContext context = new SpringProjectContext(
                "2.7.18",
                BuildTool.MAVEN,
                Arrays.asList("user-service"),
                new HashSet<>(Arrays.asList("@Service", "@Repository", "@Autowired")),
                Map.of(
                        "spring-boot-starter-web", "2.7.18",
                        "spring-boot-starter-data-jpa", "2.7.18"
                ),
                Arrays.asList("dev")
        );
        
        // Create Spring-aware prompt
        String fileContent = "package com.example.service;\n\n" +
            "import org.springframework.stereotype.Service;\n\n" +
            "@Service\n" +
            "public class UserService {\n" +
            "    \n" +
            "    private UserRepository userRepository;\n" +
            "    \n" +
            "    public UserService(UserRepository userRepository) {\n" +
            "        this.userRepository = userRepository;\n" +
            "    }\n" +
            "    \n" +
            "    public User findById(Long id) {\n" +
            "        return userRepository.findById(id).orElse(null);\n" +
            "    }\n" +
            "}";
        
        String errorContext = "NoSuchBeanDefinitionException: No qualifying bean of type 'com.example.repository.UserRepository'";
        
        String prompt = SpringPromptTemplate.buildSpringFixPrompt(
                "user-management-service",
                "src/main/java/com/example/service/UserService.java",
                fileContent,
                errorContext,
                context
        );
        
        // Mock LLM response with valid Spring fix
        String expectedDiff = "--- a/src/main/java/com/example/service/UserService.java\n" +
            "+++ b/src/main/java/com/example/service/UserService.java\n" +
            "@@ -2,6 +2,7 @@\n" +
            " \n" +
            " import org.springframework.stereotype.Service;\n" +
            "+import org.springframework.beans.factory.annotation.Autowired;\n" +
            " \n" +
            " @Service\n" +
            " public class UserService {\n" +
            "@@ -9,6 +10,7 @@\n" +
            "     private UserRepository userRepository;\n" +
            "     \n" +
            "+    @Autowired\n" +
            "     public UserService(UserRepository userRepository) {\n" +
            "         this.userRepository = userRepository;\n" +
            "     }";
        
        LlmResponse mockResponse = new LlmResponse();
        mockResponse.setText("I'll add the missing @Autowired annotation:\n\n" + expectedDiff);
        
        mockWebServer.enqueue(new MockResponse()
                .setBody(objectMapper.writeValueAsString(mockResponse))
                .addHeader("Content-Type", "application/json"));
        
        // Generate patch
        String result = llmClient.generatePatch(prompt, "src/main/java/com/example/service/UserService.java");
        
        // Verify result
        assertThat(result).isEqualTo(expectedDiff);
        assertThat(result).contains("@Autowired");
        assertThat(result).contains("import org.springframework.beans.factory.annotation.Autowired");
        
        // Verify prompt contains Spring context
        assertThat(prompt).contains("Spring Boot Version: 2.7.18");
        assertThat(prompt).contains("Build Tool: MAVEN");
        assertThat(prompt).contains("@Service").contains("@Repository").contains("@Autowired");
        assertThat(prompt).contains("spring-boot-starter-web:2.7.18");
        assertThat(prompt).contains("NoSuchBeanDefinitionException");
        assertThat(prompt).contains("Spring Boot best practices");
    }
    
    @Test
    void shouldGenerateMavenPomFix() throws Exception {
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
        
        String errorContext = "Could not resolve dependencies for project: Missing spring-boot-starter-data-jpa";
        
        String prompt = SpringPromptTemplate.buildMavenPomPrompt(
                "user-service",
                pomContent,
                errorContext,
                context
        );
        
        String expectedDiff = "--- a/pom.xml\n" +
            "+++ b/pom.xml\n" +
            "@@ -3,6 +3,10 @@\n" +
            "         <groupId>org.springframework.boot</groupId>\n" +
            "         <artifactId>spring-boot-starter-web</artifactId>\n" +
            "     </dependency>\n" +
            "+    <dependency>\n" +
            "+        <groupId>org.springframework.boot</groupId>\n" +
            "+        <artifactId>spring-boot-starter-data-jpa</artifactId>\n" +
            "+    </dependency>\n" +
            " </dependencies>";
        
        LlmResponse mockResponse = new LlmResponse();
        mockResponse.setText("Adding the missing JPA dependency:\n\n" + expectedDiff);
        
        mockWebServer.enqueue(new MockResponse()
                .setBody(objectMapper.writeValueAsString(mockResponse))
                .addHeader("Content-Type", "application/json"));
        
        String result = llmClient.generatePatch(prompt, "pom.xml");
        
        assertThat(result).isEqualTo(expectedDiff);
        assertThat(result).contains("spring-boot-starter-data-jpa");
        
        // Verify Maven-specific prompt content
        assertThat(prompt).contains("Maven pom.xml dependency issue");
        assertThat(prompt).contains("Maven dependency management");
        assertThat(prompt).contains("Spring Boot 2.7.18");
    }
    
    @Test
    void shouldHandleComplexSpringContext() throws Exception {
        // Complex Spring project with multiple modules and annotations
        SpringProjectContext context = new SpringProjectContext(
                "2.7.18",
                BuildTool.MAVEN,
                Arrays.asList("user-service", "notification-service", "common"),
                new HashSet<>(Arrays.asList(
                        "@Service", "@Repository", "@Controller", "@RestController",
                        "@Component", "@Configuration", "@Autowired", "@Value"
                )),
                Map.of(
                        "spring-boot-starter-web", "2.7.18",
                        "spring-boot-starter-data-jpa", "2.7.18",
                        "spring-boot-starter-security", "2.7.18",
                        "postgresql", "42.6.0"
                ),
                Arrays.asList("dev", "postgres", "security")
        );
        
        String prompt = SpringPromptTemplate.buildSpringFixPrompt(
                "microservices-platform",
                "src/main/java/com/example/config/SecurityConfig.java",
                "@Configuration public class SecurityConfig { }",
                "SecurityConfigurationException: Missing security configuration",
                context
        );
        
        // Verify complex context is properly formatted in prompt
        assertThat(prompt).contains("microservices-platform");
        assertThat(prompt).contains("user-service");
        assertThat(prompt).contains("Spring Boot Version: 2.7.18");
        assertThat(prompt).contains("Build Tool: MAVEN");
        assertThat(prompt).contains("@Service").contains("@Repository").contains("@Controller")
                .contains("@RestController").contains("@Component").contains("@Configuration")
                .contains("@Autowired").contains("@Value");
        assertThat(prompt).contains("spring-boot-starter-web:2.7.18")
                .contains("spring-boot-starter-data-jpa:2.7.18")
                .contains("spring-boot-starter-security:2.7.18")
                .contains("postgresql:42.6.0");
        assertThat(prompt).contains("dev, postgres, security");
        assertThat(prompt).contains("SecurityConfigurationException");
        
        // Mock a simple response to verify the flow works
        String simpleDiff = "--- a/src/main/java/com/example/config/SecurityConfig.java\n" +
            "+++ b/src/main/java/com/example/config/SecurityConfig.java\n" +
            "@@ -1,3 +1,4 @@\n" +
            "+@EnableWebSecurity\n" +
            " @Configuration \n" +
            " public class SecurityConfig { }";
        
        LlmResponse mockResponse = new LlmResponse();
        mockResponse.setText(simpleDiff);
        
        mockWebServer.enqueue(new MockResponse()
                .setBody(objectMapper.writeValueAsString(mockResponse))
                .addHeader("Content-Type", "application/json"));
        
        String result = llmClient.generatePatch(prompt, "src/main/java/com/example/config/SecurityConfig.java");
        
        assertThat(result).isEqualTo(simpleDiff);
    }
}