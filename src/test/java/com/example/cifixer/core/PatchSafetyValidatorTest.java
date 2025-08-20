package com.example.cifixer.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PatchSafetyValidatorTest {
    
    private PatchSafetyValidator validator;
    
    @BeforeEach
    void setUp() {
        validator = new PatchSafetyValidator();
    }
    
    @Test
    void shouldValidateValidJavaPatch() {
        // Given
        String validPatch = "--- a/src/main/java/com/example/UserService.java\n" +
            "+++ b/src/main/java/com/example/UserService.java\n" +
            "@@ -10,6 +10,7 @@\n" +
            " public class UserService {\n" +
            "     \n" +
            "+    @Autowired\n" +
            "     private UserRepository userRepository;\n" +
            "     \n" +
            "     public User findUser(Long id) {\n";
        
        // When
        PatchSafetyValidator.ValidationResult result = validator.validatePatch(validPatch, "src/main/java/com/example/UserService.java");
        
        // Then
        assertTrue(result.isValid());
    }
    
    @Test
    void shouldRejectPatchWithDangerousOperations() {
        // Given
        String dangerousPatch = "--- a/src/main/java/com/example/UserService.java\n" +
            "+++ b/src/main/java/com/example/UserService.java\n" +
            "@@ -10,6 +10,7 @@\n" +
            " public class UserService {\n" +
            "     \n" +
            "+    Runtime.getRuntime().exec(\"rm -rf /\");\n" +
            "     private UserRepository userRepository;\n";
        
        // When
        PatchSafetyValidator.ValidationResult result = validator.validatePatch(dangerousPatch, "src/main/java/com/example/UserService.java");
        
        // Then
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("Dangerous operation detected"));
    }
    
    @Test
    void shouldRejectInvalidFilePath() {
        // Given
        String patch = "--- a/../../etc/passwd\n" +
            "+++ b/../../etc/passwd\n" +
            "@@ -1,1 +1,1 @@\n" +
            "-root:x:0:0:root:/root:/bin/bash\n" +
            "+hacker:x:0:0:hacker:/root:/bin/bash\n";
        
        // When
        PatchSafetyValidator.ValidationResult result = validator.validatePatch(patch, "../../etc/passwd");
        
        // Then
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("directory traversal detected"));
    }
    
    @Test
    void shouldRejectPatchForDisallowedFileExtension() {
        // Given
        String patch = "--- a/malicious.exe\n" +
            "+++ b/malicious.exe\n" +
            "@@ -1,1 +1,1 @@\n" +
            "-old content\n" +
            "+new content\n";
        
        // When
        PatchSafetyValidator.ValidationResult result = validator.validatePatch(patch, "malicious.exe");
        
        // Then
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("File extension not allowed"));
    }
    
    @Test
    void shouldRejectPatchOutsideSpringProjectStructure() {
        // Given
        String patch = "--- a/random/file.java\n" +
            "+++ b/random/file.java\n" +
            "@@ -1,1 +1,1 @@\n" +
            "-old content\n" +
            "+new content\n";
        
        // When
        PatchSafetyValidator.ValidationResult result = validator.validatePatch(patch, "random/file.java");
        
        // Then
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("File path not allowed for Spring project"));
    }
    
    @Test
    void shouldRejectOversizedPatch() {
        // Given
        StringBuilder largePatch = new StringBuilder();
        largePatch.append("--- a/src/main/java/com/example/Test.java\n");
        largePatch.append("+++ b/src/main/java/com/example/Test.java\n");
        largePatch.append("@@ -1,1 +1,250 @@\n");
        
        // Add more than 200 lines
        for (int i = 0; i < 250; i++) {
            largePatch.append("+    // Line ").append(i).append("\n");
        }
        
        // When
        PatchSafetyValidator.ValidationResult result = validator.validatePatch(largePatch.toString(), "src/main/java/com/example/Test.java");
        
        // Then
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("Patch too large"));
    }
    
    @Test
    void shouldValidatePomXmlPatch() {
        // Given
        String pomPatch = "--- a/pom.xml\n" +
            "+++ b/pom.xml\n" +
            "@@ -20,6 +20,11 @@\n" +
            "         </dependency>\n" +
            "     </dependencies>\n" +
            " \n" +
            "+    <dependency>\n" +
            "+        <groupId>org.springframework.boot</groupId>\n" +
            "+        <artifactId>spring-boot-starter-data-jpa</artifactId>\n" +
            "+    </dependency>\n" +
            "+\n" +
            " </project>\n";
        
        // When
        PatchSafetyValidator.ValidationResult result = validator.validatePatch(pomPatch, "pom.xml");
        
        // Then
        assertTrue(result.isValid());
    }
    
    @Test
    void shouldValidateGradleBuildPatch() {
        // Given
        String gradlePatch = "--- a/build.gradle\n" +
            "+++ b/build.gradle\n" +
            "@@ -10,6 +10,7 @@\n" +
            " dependencies {\n" +
            "     implementation 'org.springframework.boot:spring-boot-starter-web'\n" +
            "+    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'\n" +
            "     testImplementation 'org.springframework.boot:spring-boot-starter-test'\n" +
            " }\n";
        
        // When
        PatchSafetyValidator.ValidationResult result = validator.validatePatch(gradlePatch, "build.gradle");
        
        // Then
        assertTrue(result.isValid());
    }
    
    @Test
    void shouldRejectInvalidJavaClassDeclaration() {
        // Given
        String invalidJavaPatch = "--- a/src/main/java/com/example/Test.java\n" +
            "+++ b/src/main/java/com/example/Test.java\n" +
            "@@ -1,3 +1,4 @@\n" +
            " package com.example;\n" +
            " \n" +
            "+class InvalidClass {\n" +
            " public class Test {\n";
        
        // When
        PatchSafetyValidator.ValidationResult result = validator.validatePatch(invalidJavaPatch, "src/main/java/com/example/Test.java");
        
        // Then
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("Invalid Java class declaration"));
    }
    
    @Test
    void shouldRejectInvalidJavaPackageDeclaration() {
        // Given
        String invalidPackagePatch = "--- a/src/main/java/com/example/Test.java\n" +
            "+++ b/src/main/java/com/example/Test.java\n" +
            "@@ -1,3 +1,4 @@\n" +
            "+package invalid-package-name;\n" +
            " \n" +
            " public class Test {\n";
        
        // When
        PatchSafetyValidator.ValidationResult result = validator.validatePatch(invalidPackagePatch, "src/main/java/com/example/Test.java");
        
        // Then
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("Invalid Java package declaration"));
    }
    
    @Test
    void shouldRejectInvalidJavaImportStatement() {
        // Given
        String invalidImportPatch = "--- a/src/main/java/com/example/Test.java\n" +
            "+++ b/src/main/java/com/example/Test.java\n" +
            "@@ -1,3 +1,4 @@\n" +
            " package com.example;\n" +
            "+import invalid import statement;\n" +
            " \n" +
            " public class Test {\n";
        
        // When
        PatchSafetyValidator.ValidationResult result = validator.validatePatch(invalidImportPatch, "src/main/java/com/example/Test.java");
        
        // Then
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("Invalid Java import statement"));
    }
    
    @Test
    void shouldRejectShellCommandPatterns() {
        // Given
        String shellPatch = "--- a/src/main/java/com/example/Test.java\n" +
            "+++ b/src/main/java/com/example/Test.java\n" +
            "@@ -5,6 +5,7 @@\n" +
            " public class Test {\n" +
            "     public void method() {\n" +
            "+        String cmd = \"ls -la; rm file.txt\";\n" +
            "     }\n" +
            " }\n";
        
        // When
        PatchSafetyValidator.ValidationResult result = validator.validatePatch(shellPatch, "src/main/java/com/example/Test.java");
        
        // Then
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("Shell command patterns detected"));
    }
    
    @Test
    void shouldRejectSqlInjectionPatterns() {
        // Given
        String sqlPatch = "--- a/src/main/java/com/example/Test.java\n" +
            "+++ b/src/main/java/com/example/Test.java\n" +
            "@@ -5,6 +5,7 @@\n" +
            " public class Test {\n" +
            "     public void method() {\n" +
            "+        String sql = \"SELECT * FROM users UNION SELECT password FROM admin\";\n" +
            "     }\n" +
            " }\n";
        
        // When
        PatchSafetyValidator.ValidationResult result = validator.validatePatch(sqlPatch, "src/main/java/com/example/Test.java");
        
        // Then
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("Potential SQL injection pattern detected"));
    }
    
    @Test
    void shouldValidateYamlPatch() {
        // Given
        String yamlPatch = "--- a/src/main/resources/application.yml\n" +
            "+++ b/src/main/resources/application.yml\n" +
            "@@ -1,3 +1,6 @@\n" +
            " server:\n" +
            "   port: 8080\n" +
            "+\n" +
            "+spring:\n" +
            "+  datasource:\n" +
            "+    url: jdbc:h2:mem:testdb\n";
        
        // When
        PatchSafetyValidator.ValidationResult result = validator.validatePatch(yamlPatch, "src/main/resources/application.yml");
        
        // Then
        assertTrue(result.isValid());
    }
    
    @Test
    void shouldRejectYamlWithTabs() {
        // Given
        String yamlWithTabs = "--- a/src/main/resources/application.yml\n" +
            "+++ b/src/main/resources/application.yml\n" +
            "@@ -1,3 +1,4 @@\n" +
            " server:\n" +
            "   port: 8080\n" +
            "+\tdatasource: test\n";
        
        // When
        PatchSafetyValidator.ValidationResult result = validator.validatePatch(yamlWithTabs, "src/main/resources/application.yml");
        
        // Then
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("YAML files should use spaces, not tabs"));
    }
    
    @Test
    void shouldRejectYamlTypeTags() {
        // Given
        String yamlWithTypeTags = "--- a/src/main/resources/application.yml\n" +
            "+++ b/src/main/resources/application.yml\n" +
            "@@ -1,3 +1,4 @@\n" +
            " server:\n" +
            "   port: 8080\n" +
            "+  config: !!python/object/apply:os.system [\"rm -rf /\"]\n";
        
        // When
        PatchSafetyValidator.ValidationResult result = validator.validatePatch(yamlWithTypeTags, "src/main/resources/application.yml");
        
        // Then
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("YAML type tags not allowed"));
    }
    
    @Test
    void shouldValidatePropertiesPatch() {
        // Given
        String propertiesPatch = "--- a/src/main/resources/application.properties\n" +
            "+++ b/src/main/resources/application.properties\n" +
            "@@ -1,2 +1,3 @@\n" +
            " server.port=8080\n" +
            "+spring.datasource.url=jdbc:h2:mem:testdb\n" +
            " logging.level.root=INFO\n";
        
        // When
        PatchSafetyValidator.ValidationResult result = validator.validatePatch(propertiesPatch, "src/main/resources/application.properties");
        
        // Then
        assertTrue(result.isValid());
    }
    
    @Test
    void shouldRejectInvalidPropertiesFormat() {
        // Given
        String invalidPropertiesPatch = "--- a/src/main/resources/application.properties\n" +
            "+++ b/src/main/resources/application.properties\n" +
            "@@ -1,2 +1,3 @@\n" +
            " server.port=8080\n" +
            "+invalid property line without equals or colon\n" +
            " logging.level.root=INFO\n";
        
        // When
        PatchSafetyValidator.ValidationResult result = validator.validatePatch(invalidPropertiesPatch, "src/main/resources/application.properties");
        
        // Then
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("Invalid properties format"));
    }
    
    @Test
    void shouldRejectGradleExecOperations() {
        // Given
        String gradleExecPatch = "--- a/build.gradle\n" +
            "+++ b/build.gradle\n" +
            "@@ -10,6 +10,9 @@\n" +
            " dependencies {\n" +
            "     implementation 'org.springframework.boot:spring-boot-starter-web'\n" +
            " }\n" +
            "+\n" +
            "+task malicious {\n" +
            "+    project.exec { commandLine 'rm', '-rf', '/' }\n" +
            "+}\n";
        
        // When
        PatchSafetyValidator.ValidationResult result = validator.validatePatch(gradleExecPatch, "build.gradle");
        
        // Then
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("Gradle exec operations not allowed"));
    }
    
    @Test
    void shouldHandleNullAndEmptyInputs() {
        // Test null patch
        PatchSafetyValidator.ValidationResult result1 = validator.validatePatch(null, "test.java");
        assertFalse(result1.isValid());
        assertTrue(result1.getErrorMessage().contains("empty or null"));
        
        // Test empty patch
        PatchSafetyValidator.ValidationResult result2 = validator.validatePatch("", "test.java");
        assertFalse(result2.isValid());
        assertTrue(result2.getErrorMessage().contains("empty or null"));
        
        // Test null file path
        PatchSafetyValidator.ValidationResult result3 = validator.validatePatch("patch content", null);
        assertFalse(result3.isValid());
        assertTrue(result3.getErrorMessage().contains("empty or null"));
    }
}