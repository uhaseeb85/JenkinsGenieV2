package com.example.cifixer.llm;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Validates unified diff format and safety checks.
 */
@Component
public class UnifiedDiffValidator {
    
    private static final Pattern DIFF_HEADER_PATTERN = Pattern.compile("^--- a/.+$", Pattern.MULTILINE);
    private static final Pattern DIFF_NEW_FILE_PATTERN = Pattern.compile("^\\+\\+\\+ b/.+$", Pattern.MULTILINE);
    private static final Pattern HUNK_HEADER_PATTERN = Pattern.compile("^@@ -\\d+,\\d+ \\+\\d+,\\d+ @@.*$", Pattern.MULTILINE);
    
    private static final int MAX_PATCH_LINES = 200;
    private static final int MAX_ADDED_LINES = 100;
    private static final int MAX_REMOVED_LINES = 100;
    
    // Dangerous patterns that should not appear in patches
    private static final String[] DANGEROUS_PATTERNS = {
        "rm -rf", "DELETE FROM", "DROP TABLE", "DROP DATABASE",
        "System.exit", "Runtime.getRuntime().exec", "ProcessBuilder",
        "File.delete", "Files.delete", "deleteOnExit"
    };
    
    /**
     * Validates that the given text is a proper unified diff format.
     */
    public ValidationResult validateUnifiedDiff(String diff, String filePath) {
        if (diff == null || diff.trim().isEmpty()) {
            return ValidationResult.invalid("Diff is empty or null");
        }
        
        // Check for basic unified diff structure
        if (!DIFF_HEADER_PATTERN.matcher(diff).find()) {
            return ValidationResult.invalid("Missing diff header (--- a/...)");
        }
        
        if (!DIFF_NEW_FILE_PATTERN.matcher(diff).find()) {
            return ValidationResult.invalid("Missing new file header (+++ b/...)");
        }
        
        if (!HUNK_HEADER_PATTERN.matcher(diff).find()) {
            return ValidationResult.invalid("Missing hunk header (@@ -n,m +x,y @@)");
        }
        
        // Safety checks
        ValidationResult safetyResult = validateSafety(diff, filePath);
        if (!safetyResult.isValid()) {
            return safetyResult;
        }
        
        // Size limits
        ValidationResult sizeResult = validateSize(diff);
        if (!sizeResult.isValid()) {
            return sizeResult;
        }
        
        // File path validation
        ValidationResult pathResult = validateFilePath(diff, filePath);
        if (!pathResult.isValid()) {
            return pathResult;
        }
        
        return ValidationResult.valid();
    }
    
    private ValidationResult validateSafety(String diff, String filePath) {
        // Check for dangerous operations
        for (String dangerousPattern : DANGEROUS_PATTERNS) {
            if (diff.contains(dangerousPattern)) {
                return ValidationResult.invalid("Dangerous operation detected: " + dangerousPattern);
            }
        }
        
        // Validate Java file specific patterns
        if (filePath.endsWith(".java")) {
            return validateJavaFile(diff);
        }
        
        // Validate Maven pom.xml
        if (filePath.equals("pom.xml")) {
            return validateMavenPom(diff);
        }
        
        // Validate Gradle build file
        if (filePath.equals("build.gradle") || filePath.equals("build.gradle.kts")) {
            return validateGradleBuild(diff);
        }
        
        return ValidationResult.valid();
    }
    
    private ValidationResult validateJavaFile(String diff) {
        // Check for proper Java syntax patterns in additions
        String[] lines = diff.split("\n");
        for (String line : lines) {
            if (line.startsWith("+") && !line.startsWith("+++")) {
                String addedLine = line.substring(1).trim();
                
                // Skip empty lines and comments
                if (addedLine.isEmpty() || addedLine.startsWith("//") || addedLine.startsWith("/*")) {
                    continue;
                }
                
                // Basic Java syntax validation
                if (addedLine.contains("class ") && !addedLine.matches(".*\\b(public|private|protected)?\\s*(static|final|abstract)?\\s*class\\s+\\w+.*")) {
                    return ValidationResult.invalid("Invalid Java class declaration: " + addedLine);
                }
            }
        }
        
        return ValidationResult.valid();
    }
    
    private ValidationResult validateMavenPom(String diff) {
        // Check for proper XML structure in Maven pom.xml changes
        String[] lines = diff.split("\n");
        int xmlDepth = 0;
        
        for (String line : lines) {
            if (line.startsWith("+") && !line.startsWith("+++")) {
                String addedLine = line.substring(1).trim();
                
                // Basic XML validation for Maven dependencies
                if (addedLine.contains("<dependency>")) {
                    xmlDepth++;
                } else if (addedLine.contains("</dependency>")) {
                    xmlDepth--;
                }
                
                // Check for malformed XML
                if (addedLine.contains("<") && addedLine.contains(">")) {
                    if (!addedLine.matches(".*<[^>]+>.*") && !addedLine.startsWith("<!--")) {
                        return ValidationResult.invalid("Invalid XML syntax: " + addedLine);
                    }
                }
            }
        }
        
        return ValidationResult.valid();
    }
    
    private ValidationResult validateGradleBuild(String diff) {
        // Basic Gradle syntax validation
        String[] lines = diff.split("\n");
        
        for (String line : lines) {
            if (line.startsWith("+") && !line.startsWith("+++")) {
                String addedLine = line.substring(1).trim();
                
                // Check for proper Gradle dependency syntax
                if (addedLine.contains("implementation") || addedLine.contains("testImplementation")) {
                    if (!addedLine.matches(".*implementation\\s+['\"].*['\"].*") && 
                        !addedLine.matches(".*testImplementation\\s+['\"].*['\"].*")) {
                        return ValidationResult.invalid("Invalid Gradle dependency syntax: " + addedLine);
                    }
                }
            }
        }
        
        return ValidationResult.valid();
    }
    
    private ValidationResult validateSize(String diff) {
        String[] lines = diff.split("\n");
        
        if (lines.length > MAX_PATCH_LINES) {
            return ValidationResult.invalid("Patch too large: " + lines.length + " lines (max: " + MAX_PATCH_LINES + ")");
        }
        
        int addedLines = 0;
        int removedLines = 0;
        
        for (String line : lines) {
            if (line.startsWith("+") && !line.startsWith("+++")) {
                addedLines++;
            } else if (line.startsWith("-") && !line.startsWith("---")) {
                removedLines++;
            }
        }
        
        if (addedLines > MAX_ADDED_LINES) {
            return ValidationResult.invalid("Too many added lines: " + addedLines + " (max: " + MAX_ADDED_LINES + ")");
        }
        
        if (removedLines > MAX_REMOVED_LINES) {
            return ValidationResult.invalid("Too many removed lines: " + removedLines + " (max: " + MAX_REMOVED_LINES + ")");
        }
        
        return ValidationResult.valid();
    }
    
    private ValidationResult validateFilePath(String diff, String expectedFilePath) {
        // Validate that the diff is for the expected file
        if (!diff.contains("--- a/" + expectedFilePath) || !diff.contains("+++ b/" + expectedFilePath)) {
            return ValidationResult.invalid("Diff file path mismatch. Expected: " + expectedFilePath);
        }
        
        // Validate file path is within allowed Spring project structure
        if (!isAllowedFilePath(expectedFilePath)) {
            return ValidationResult.invalid("File path not allowed for Spring project: " + expectedFilePath);
        }
        
        return ValidationResult.valid();
    }
    
    private boolean isAllowedFilePath(String filePath) {
        // Allow Spring project structure files
        return filePath.startsWith("src/main/java/") ||
               filePath.startsWith("src/test/java/") ||
               filePath.startsWith("src/main/resources/") ||
               filePath.startsWith("src/test/resources/") ||
               filePath.equals("pom.xml") ||
               filePath.equals("build.gradle") ||
               filePath.equals("build.gradle.kts") ||
               filePath.equals("settings.gradle") ||
               filePath.equals("gradle.properties");
    }
    
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
    }
}