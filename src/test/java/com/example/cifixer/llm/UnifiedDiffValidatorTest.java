package com.example.cifixer.llm;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UnifiedDiffValidatorTest {
    
    private UnifiedDiffValidator validator;
    
    @BeforeEach
    void setUp() {
        validator = new UnifiedDiffValidator();
    }
    
    @Test
    void shouldValidateValidJavaDiff() {
        String validDiff = "--- a/src/main/java/com/example/UserService.java\n" +
            "+++ b/src/main/java/com/example/UserService.java\n" +
            "@@ -10,6 +10,7 @@\n" +
            " public class UserService {\n" +
            " \n" +
            "     private final UserRepository userRepository;\n" +
            "+    \n" +
            "+    @Autowired\n" +
            "     public UserService(UserRepository userRepository) {\n" +
            "         this.userRepository = userRepository;\n" +
            "     }";
        
        UnifiedDiffValidator.ValidationResult result = validator.validateUnifiedDiff(validDiff, "src/main/java/com/example/UserService.java");
        
        assertThat(result.isValid()).isTrue();
        assertThat(result.getErrorMessage()).isNull();
    }
    
    @Test
    void shouldValidateValidMavenPomDiff() {
        String validDiff = "--- a/pom.xml\n" +
            "+++ b/pom.xml\n" +
            "@@ -45,6 +45,11 @@\n" +
            "         <groupId>org.springframework.boot</groupId>\n" +
            "         <artifactId>spring-boot-starter-web</artifactId>\n" +
            "     </dependency>\n" +
            "+    <dependency>\n" +
            "+        <groupId>org.springframework.boot</groupId>\n" +
            "+        <artifactId>spring-boot-starter-data-jpa</artifactId>\n" +
            "+    </dependency>\n" +
            " </dependencies>";
        
        UnifiedDiffValidator.ValidationResult result = validator.validateUnifiedDiff(validDiff, "pom.xml");
        
        assertThat(result.isValid()).isTrue();
    }
    
    @Test
    void shouldRejectDiffWithoutHeader() {
        String invalidDiff = "@@ -10,6 +10,7 @@\n" +
            " public class UserService {\n" +
            "+    @Autowired\n" +
            "     private UserRepository userRepository;";
        
        UnifiedDiffValidator.ValidationResult result = validator.validateUnifiedDiff(invalidDiff, "src/main/java/com/example/UserService.java");
        
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrorMessage()).contains("Missing diff header");
    }
    
    @Test
    void shouldRejectDiffWithoutNewFileHeader() {
        String invalidDiff = "--- a/src/main/java/com/example/UserService.java\n" +
            "@@ -10,6 +10,7 @@\n" +
            " public class UserService {\n" +
            "+    @Autowired\n" +
            "     private UserRepository userRepository;";
        
        UnifiedDiffValidator.ValidationResult result = validator.validateUnifiedDiff(invalidDiff, "src/main/java/com/example/UserService.java");
        
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrorMessage()).contains("Missing new file header");
    }
    
    @Test
    void shouldRejectDiffWithoutHunkHeader() {
        String invalidDiff = "--- a/src/main/java/com/example/UserService.java\n" +
            "+++ b/src/main/java/com/example/UserService.java\n" +
            " public class UserService {\n" +
            "+    @Autowired\n" +
            "     private UserRepository userRepository;";
        
        UnifiedDiffValidator.ValidationResult result = validator.validateUnifiedDiff(invalidDiff, "src/main/java/com/example/UserService.java");
        
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrorMessage()).contains("Missing hunk header");
    }
    
    @Test
    void shouldRejectDangerousOperations() {
        String dangerousDiff = "--- a/src/main/java/com/example/UserService.java\n" +
            "+++ b/src/main/java/com/example/UserService.java\n" +
            "@@ -10,6 +10,7 @@\n" +
            " public class UserService {\n" +
            "+    Runtime.getRuntime().exec(\"rm -rf /\");\n" +
            "     private UserRepository userRepository;";
        
        UnifiedDiffValidator.ValidationResult result = validator.validateUnifiedDiff(dangerousDiff, "src/main/java/com/example/UserService.java");
        
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrorMessage()).contains("Dangerous operation detected");
    }
    
    @Test
    void shouldRejectTooLargePatch() {
        StringBuilder largeDiff = new StringBuilder();
        largeDiff.append("--- a/src/main/java/com/example/UserService.java\n");
        largeDiff.append("+++ b/src/main/java/com/example/UserService.java\n");
        largeDiff.append("@@ -10,6 +10,250 @@\n");
        
        // Add more than MAX_PATCH_LINES
        for (int i = 0; i < 250; i++) {
            largeDiff.append("+    // Added line ").append(i).append("\n");
        }
        
        UnifiedDiffValidator.ValidationResult result = validator.validateUnifiedDiff(largeDiff.toString(), "src/main/java/com/example/UserService.java");
        
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrorMessage()).contains("Patch too large");
    }
    
    @Test
    void shouldRejectFilePathMismatch() {
        String validDiff = "--- a/src/main/java/com/example/OtherService.java\n" +
            "+++ b/src/main/java/com/example/OtherService.java\n" +
            "@@ -10,6 +10,7 @@\n" +
            " public class OtherService {\n" +
            "+    @Autowired\n" +
            "     private UserRepository userRepository;";
        
        UnifiedDiffValidator.ValidationResult result = validator.validateUnifiedDiff(validDiff, "src/main/java/com/example/UserService.java");
        
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrorMessage()).contains("Diff file path mismatch");
    }
    
    @Test
    void shouldRejectDisallowedFilePath() {
        String validDiff = "--- a/etc/passwd\n" +
            "+++ b/etc/passwd\n" +
            "@@ -1,3 +1,4 @@\n" +
            " root:x:0:0:root:/root:/bin/bash\n" +
            "+hacker:x:0:0:hacker:/root:/bin/bash";
        
        UnifiedDiffValidator.ValidationResult result = validator.validateUnifiedDiff(validDiff, "etc/passwd");
        
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrorMessage()).contains("File path not allowed");
    }
    
    @Test
    void shouldAcceptAllowedSpringProjectPaths() {
        String[] allowedPaths = {
            "src/main/java/com/example/UserService.java",
            "src/test/java/com/example/UserServiceTest.java",
            "src/main/resources/application.yml",
            "src/test/resources/test.properties",
            "pom.xml",
            "build.gradle",
            "build.gradle.kts",
            "settings.gradle",
            "gradle.properties"
        };
        
        for (String path : allowedPaths) {
            String diff = String.format("--- a/%s\n" +
                "+++ b/%s\n" +
                "@@ -1,3 +1,4 @@\n" +
                " line1\n" +
                "+line2", path, path);
            
            UnifiedDiffValidator.ValidationResult result = validator.validateUnifiedDiff(diff, path);
            assertThat(result.isValid()).as("Path should be allowed: " + path).isTrue();
        }
    }
    
    @Test
    void shouldHandleEmptyOrNullDiff() {
        UnifiedDiffValidator.ValidationResult result1 = validator.validateUnifiedDiff(null, "src/main/java/Test.java");
        assertThat(result1.isValid()).isFalse();
        assertThat(result1.getErrorMessage()).contains("empty or null");
        
        UnifiedDiffValidator.ValidationResult result2 = validator.validateUnifiedDiff("", "src/main/java/Test.java");
        assertThat(result2.isValid()).isFalse();
        assertThat(result2.getErrorMessage()).contains("empty or null");
        
        UnifiedDiffValidator.ValidationResult result3 = validator.validateUnifiedDiff("   ", "src/main/java/Test.java");
        assertThat(result3.isValid()).isFalse();
        assertThat(result3.getErrorMessage()).contains("empty or null");
    }
}