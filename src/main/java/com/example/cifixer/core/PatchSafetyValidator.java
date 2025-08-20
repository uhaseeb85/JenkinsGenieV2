package com.example.cifixer.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Enhanced safety validator for patches with comprehensive security checks.
 */
@Component
public class PatchSafetyValidator {
    
    private static final Logger logger = LoggerFactory.getLogger(PatchSafetyValidator.class);
    
    // Maximum limits for patch safety
    private static final int MAX_PATCH_LINES = 200;
    private static final int MAX_ADDED_LINES = 100;
    private static final int MAX_REMOVED_LINES = 100;
    private static final int MAX_FILE_SIZE_KB = 500;
    
    // Dangerous operations that should never appear in patches
    private static final Set<String> DANGEROUS_OPERATIONS = new HashSet<>(Arrays.asList(
        "rm -rf", "DELETE FROM", "DROP TABLE", "DROP DATABASE", "TRUNCATE TABLE",
        "System.exit", "Runtime.getRuntime().exec", "ProcessBuilder",
        "File.delete", "Files.delete", "deleteOnExit", "Files.deleteIfExists",
        "shutdown", "halt", "destroy", "killall", "pkill",
        "eval(", "exec(", "system(", "shell_exec", "passthru",
        "sudo ", "su ", "chmod 777", "chmod -R",
        "wget ", "curl ", "nc ", "netcat", "telnet",
        "base64 -d", "echo ", "cat /etc/passwd", "cat /etc/shadow"
    ));
    
    // Suspicious patterns that require extra scrutiny
    private static final Set<String> SUSPICIOUS_PATTERNS = new HashSet<>(Arrays.asList(
        "password", "secret", "token", "key", "credential",
        "admin", "root", "superuser", "localhost", "127.0.0.1",
        "jdbc:", "mongodb:", "redis:", "elasticsearch:",
        "System.getProperty", "System.setProperty", "System.getenv"
    ));
    
    // Allowed file extensions for Spring projects
    private static final Set<String> ALLOWED_EXTENSIONS = new HashSet<>(Arrays.asList(
        ".java", ".xml", ".yml", ".yaml", ".properties", ".gradle", ".kts"
    ));
    
    // Patterns for validating Java code
    private static final Pattern JAVA_PACKAGE_PATTERN = Pattern.compile("^package\\s+[a-zA-Z][a-zA-Z0-9_.]*;$");
    private static final Pattern JAVA_IMPORT_PATTERN = Pattern.compile("^import\\s+(static\\s+)?[a-zA-Z][a-zA-Z0-9_.]*(\\.\\*)?;$");
    private static final Pattern JAVA_CLASS_PATTERN = Pattern.compile(".*\\b(public|private|protected)?\\s*(static|final|abstract)?\\s*(class|interface|enum)\\s+\\w+.*");
    
    /**
     * Validates a patch for safety and security concerns.
     */
    public ValidationResult validatePatch(String diff, String filePath) {
        if (diff == null || diff.trim().isEmpty()) {
            return ValidationResult.invalid("Patch is empty or null");
        }
        
        if (filePath == null || filePath.trim().isEmpty()) {
            return ValidationResult.invalid("File path is empty or null");
        }
        
        logger.debug("Validating patch safety for file: {}", filePath);
        
        // File path validation
        ValidationResult pathResult = validateFilePath(filePath);
        if (!pathResult.isValid()) {
            return pathResult;
        }
        
        // Dangerous operations check
        ValidationResult dangerResult = validateDangerousOperations(diff);
        if (!dangerResult.isValid()) {
            return dangerResult;
        }
        
        // Size limits check
        ValidationResult sizeResult = validatePatchSize(diff);
        if (!sizeResult.isValid()) {
            return sizeResult;
        }
        
        // Content validation based on file type
        ValidationResult contentResult = validateFileContent(diff, filePath);
        if (!contentResult.isValid()) {
            return contentResult;
        }
        
        // Suspicious patterns check (warning level)
        ValidationResult suspiciousResult = checkSuspiciousPatterns(diff);
        if (!suspiciousResult.isValid()) {
            logger.warn("Suspicious patterns detected in patch for {}: {}", 
                filePath, suspiciousResult.getErrorMessage());
            // Continue processing but log the warning
        }
        
        logger.debug("Patch validation successful for file: {}", filePath);
        return ValidationResult.valid();
    }
    
    /**
     * Validates that the file path is safe and within allowed Spring project structure.
     */
    private ValidationResult validateFilePath(String filePath) {
        try {
            Path path = Paths.get(filePath).normalize();
            String normalizedPath = path.toString().replace('\\', '/');
            
            // Check for directory traversal attempts
            if (normalizedPath.contains("..") || normalizedPath.startsWith("/") || 
                normalizedPath.contains("~") || normalizedPath.contains("$")) {
                return ValidationResult.invalid("Invalid file path - directory traversal detected: " + filePath);
            }
            
            // Check if file is within allowed Spring project structure
            if (!isAllowedSpringProjectPath(normalizedPath)) {
                return ValidationResult.invalid("File path not allowed for Spring project: " + filePath);
            }
            
            // Check file extension
            String extension = getFileExtension(normalizedPath);
            if (!ALLOWED_EXTENSIONS.contains(extension)) {
                return ValidationResult.invalid("File extension not allowed: " + extension);
            }
            
            return ValidationResult.valid();
            
        } catch (Exception e) {
            return ValidationResult.invalid("Invalid file path format: " + e.getMessage());
        }
    }
    
    /**
     * Checks if the file path is within allowed Spring project structure.
     */
    private boolean isAllowedSpringProjectPath(String filePath) {
        return filePath.startsWith("src/main/java/") ||
               filePath.startsWith("src/test/java/") ||
               filePath.startsWith("src/main/resources/") ||
               filePath.startsWith("src/test/resources/") ||
               filePath.equals("pom.xml") ||
               filePath.equals("build.gradle") ||
               filePath.equals("build.gradle.kts") ||
               filePath.equals("settings.gradle") ||
               filePath.equals("gradle.properties") ||
               filePath.equals("application.yml") ||
               filePath.equals("application.yaml") ||
               filePath.equals("application.properties") ||
               filePath.startsWith("src/main/resources/application") ||
               filePath.startsWith("src/test/resources/application");
    }
    
    /**
     * Validates that the patch doesn't contain dangerous operations.
     */
    private ValidationResult validateDangerousOperations(String diff) {
        String lowerDiff = diff.toLowerCase();
        
        for (String dangerous : DANGEROUS_OPERATIONS) {
            if (lowerDiff.contains(dangerous.toLowerCase())) {
                return ValidationResult.invalid("Dangerous operation detected: " + dangerous);
            }
        }
        
        // Check for shell command patterns
        if (lowerDiff.matches(".*[;&|`$].*")) {
            return ValidationResult.invalid("Shell command patterns detected in patch");
        }
        
        // Check for SQL injection patterns
        if (lowerDiff.matches(".*(union|select|insert|update|delete)\\s+(from|into|set).*")) {
            return ValidationResult.invalid("Potential SQL injection pattern detected");
        }
        
        return ValidationResult.valid();
    }
    
    /**
     * Validates patch size limits.
     */
    private ValidationResult validatePatchSize(String diff) {
        String[] lines = diff.split("\n");
        
        if (lines.length > MAX_PATCH_LINES) {
            return ValidationResult.invalid(String.format(
                "Patch too large: %d lines (max: %d)", lines.length, MAX_PATCH_LINES));
        }
        
        int addedLines = 0;
        int removedLines = 0;
        int totalSize = 0;
        
        for (String line : lines) {
            totalSize += line.length();
            
            if (line.startsWith("+") && !line.startsWith("+++")) {
                addedLines++;
            } else if (line.startsWith("-") && !line.startsWith("---")) {
                removedLines++;
            }
        }
        
        if (addedLines > MAX_ADDED_LINES) {
            return ValidationResult.invalid(String.format(
                "Too many added lines: %d (max: %d)", addedLines, MAX_ADDED_LINES));
        }
        
        if (removedLines > MAX_REMOVED_LINES) {
            return ValidationResult.invalid(String.format(
                "Too many removed lines: %d (max: %d)", removedLines, MAX_REMOVED_LINES));
        }
        
        if (totalSize > MAX_FILE_SIZE_KB * 1024) {
            return ValidationResult.invalid(String.format(
                "Patch size too large: %d KB (max: %d KB)", totalSize / 1024, MAX_FILE_SIZE_KB));
        }
        
        return ValidationResult.valid();
    }
    
    /**
     * Validates file content based on file type.
     */
    private ValidationResult validateFileContent(String diff, String filePath) {
        String extension = getFileExtension(filePath);
        
        switch (extension) {
            case ".java":
                return validateJavaContent(diff);
            case ".xml":
                return validateXmlContent(diff);
            case ".yml":
            case ".yaml":
                return validateYamlContent(diff);
            case ".properties":
                return validatePropertiesContent(diff);
            case ".gradle":
            case ".kts":
                return validateGradleContent(diff);
            default:
                return ValidationResult.valid(); // Unknown type, allow but log
        }
    }
    
    /**
     * Validates Java file content in the patch.
     */
    private ValidationResult validateJavaContent(String diff) {
        String[] lines = diff.split("\n");
        
        for (String line : lines) {
            if (line.startsWith("+") && !line.startsWith("+++")) {
                String addedLine = line.substring(1).trim();
                
                // Skip empty lines and comments
                if (addedLine.isEmpty() || addedLine.startsWith("//") || 
                    addedLine.startsWith("/*") || addedLine.startsWith("*")) {
                    continue;
                }
                
                // Validate package declarations
                if (addedLine.startsWith("package ") && !JAVA_PACKAGE_PATTERN.matcher(addedLine).matches()) {
                    return ValidationResult.invalid("Invalid Java package declaration: " + addedLine);
                }
                
                // Validate import statements
                if (addedLine.startsWith("import ") && !JAVA_IMPORT_PATTERN.matcher(addedLine).matches()) {
                    return ValidationResult.invalid("Invalid Java import statement: " + addedLine);
                }
                
                // Validate class declarations
                if ((addedLine.contains("class ") || addedLine.contains("interface ") || 
                     addedLine.contains("enum ")) && !JAVA_CLASS_PATTERN.matcher(addedLine).matches()) {
                    return ValidationResult.invalid("Invalid Java class/interface/enum declaration: " + addedLine);
                }
                
                // Check for proper annotation usage
                if (addedLine.startsWith("@") && !addedLine.matches("@[A-Za-z][A-Za-z0-9_]*.*")) {
                    return ValidationResult.invalid("Invalid Java annotation: " + addedLine);
                }
            }
        }
        
        return ValidationResult.valid();
    }
    
    /**
     * Validates XML content (for pom.xml, etc.).
     */
    private ValidationResult validateXmlContent(String diff) {
        String[] lines = diff.split("\n");
        int xmlDepth = 0;
        
        for (String line : lines) {
            if (line.startsWith("+") && !line.startsWith("+++")) {
                String addedLine = line.substring(1).trim();
                
                // Skip empty lines and comments
                if (addedLine.isEmpty() || addedLine.startsWith("<!--")) {
                    continue;
                }
                
                // Basic XML validation
                if (addedLine.contains("<") && addedLine.contains(">")) {
                    // Count opening and closing tags
                    long openTags = addedLine.chars().filter(ch -> ch == '<').count();
                    long closeTags = addedLine.chars().filter(ch -> ch == '>').count();
                    
                    if (openTags != closeTags) {
                        return ValidationResult.invalid("Malformed XML in line: " + addedLine);
                    }
                }
            }
        }
        
        return ValidationResult.valid();
    }
    
    /**
     * Validates YAML content.
     */
    private ValidationResult validateYamlContent(String diff) {
        String[] lines = diff.split("\n");
        
        for (String line : lines) {
            if (line.startsWith("+") && !line.startsWith("+++")) {
                String addedLine = line.substring(1);
                
                // Check for proper YAML indentation (spaces only)
                if (addedLine.startsWith("\t")) {
                    return ValidationResult.invalid("YAML files should use spaces, not tabs: " + addedLine);
                }
                
                // Check for dangerous YAML constructs
                if (addedLine.trim().startsWith("!!")) {
                    return ValidationResult.invalid("YAML type tags not allowed: " + addedLine);
                }
            }
        }
        
        return ValidationResult.valid();
    }
    
    /**
     * Validates properties file content.
     */
    private ValidationResult validatePropertiesContent(String diff) {
        String[] lines = diff.split("\n");
        
        for (String line : lines) {
            if (line.startsWith("+") && !line.startsWith("+++")) {
                String addedLine = line.substring(1).trim();
                
                // Skip empty lines and comments
                if (addedLine.isEmpty() || addedLine.startsWith("#")) {
                    continue;
                }
                
                // Validate property format
                if (!addedLine.contains("=") && !addedLine.contains(":")) {
                    return ValidationResult.invalid("Invalid properties format: " + addedLine);
                }
            }
        }
        
        return ValidationResult.valid();
    }
    
    /**
     * Validates Gradle build file content.
     */
    private ValidationResult validateGradleContent(String diff) {
        String[] lines = diff.split("\n");
        
        for (String line : lines) {
            if (line.startsWith("+") && !line.startsWith("+++")) {
                String addedLine = line.substring(1).trim();
                
                // Check for dangerous Gradle operations
                if (addedLine.contains("exec {") || addedLine.contains("project.exec")) {
                    return ValidationResult.invalid("Gradle exec operations not allowed: " + addedLine);
                }
                
                // Validate dependency declarations
                if ((addedLine.contains("implementation") || addedLine.contains("testImplementation")) &&
                    !addedLine.matches(".*implementation\\s+['\"].*['\"].*")) {
                    return ValidationResult.invalid("Invalid Gradle dependency syntax: " + addedLine);
                }
            }
        }
        
        return ValidationResult.valid();
    }
    
    /**
     * Checks for suspicious patterns that might indicate security issues.
     */
    private ValidationResult checkSuspiciousPatterns(String diff) {
        String lowerDiff = diff.toLowerCase();
        
        for (String suspicious : SUSPICIOUS_PATTERNS) {
            if (lowerDiff.contains(suspicious)) {
                return ValidationResult.invalid("Suspicious pattern detected: " + suspicious);
            }
        }
        
        return ValidationResult.valid();
    }
    
    /**
     * Gets the file extension from a file path.
     */
    private String getFileExtension(String filePath) {
        int lastDot = filePath.lastIndexOf('.');
        return lastDot > 0 ? filePath.substring(lastDot) : "";
    }
    
    /**
     * Result of patch validation.
     */
    public static class ValidationResult {
        private final boolean valid;
        private final String errorMessage;
        
        private ValidationResult(boolean valid, String errorMessage) {
            this.valid = valid;
            this.errorMessage = errorMessage;
        }
        
        public static ValidationResult valid() {
            return new ValidationResult(true, null);
        }
        
        public static ValidationResult invalid(String errorMessage) {
            return new ValidationResult(false, errorMessage);
        }
        
        public boolean isValid() {
            return valid;
        }
        
        public String getErrorMessage() {
            return errorMessage;
        }
        
        @Override
        public String toString() {
            return valid ? "Valid" : "Invalid: " + errorMessage;
        }
    }
}