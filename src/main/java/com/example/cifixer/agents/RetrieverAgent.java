package com.example.cifixer.agents;

import com.example.cifixer.core.Agent;
import com.example.cifixer.core.Task;
import com.example.cifixer.core.TaskResult;
import com.example.cifixer.core.TaskStatus;
import com.example.cifixer.store.Build;
import com.example.cifixer.store.CandidateFile;
import com.example.cifixer.store.CandidateFileRepository;
import com.example.cifixer.store.Plan;
import com.example.cifixer.store.PlanRepository;
import com.example.cifixer.util.SpringProjectAnalyzer;
import com.example.cifixer.util.SpringProjectContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Agent responsible for identifying and ranking candidate files for Spring-aware fixes.
 * Specializes in Spring component prioritization and context-aware file scoring.
 */
@Component
public class RetrieverAgent implements Agent<Map<String, Object>> {
    
    private static final Logger logger = LoggerFactory.getLogger(RetrieverAgent.class);
    
    private final CandidateFileRepository candidateFileRepository;
    private final PlanRepository planRepository;
    private final SpringProjectAnalyzer springProjectAnalyzer;
    
    @Value("${cifixer.working-dir:/tmp/cifixer}")
    private String workingDirBase;
    
    // Spring annotation patterns for prioritization
    private static final Set<String> HIGH_PRIORITY_ANNOTATIONS = Set.of(
        "@SpringBootApplication", "@Configuration", "@EnableAutoConfiguration"
    );
    
    private static final Set<String> MEDIUM_PRIORITY_ANNOTATIONS = Set.of(
        "@Controller", "@RestController", "@Service", "@Repository", "@Component"
    );
    
    // File type scoring weights
    private static final BigDecimal COMPILATION_ERROR_WEIGHT = new BigDecimal("120.0");
    private static final BigDecimal STACK_TRACE_WEIGHT = new BigDecimal("100.0");
    private static final BigDecimal SPRING_ERROR_WEIGHT = new BigDecimal("80.0");
    private static final BigDecimal BUILD_FILE_WEIGHT = new BigDecimal("70.0");
    private static final BigDecimal HIGH_PRIORITY_ANNOTATION_WEIGHT = new BigDecimal("60.0");
    private static final BigDecimal MEDIUM_PRIORITY_ANNOTATION_WEIGHT = new BigDecimal("40.0");
    private static final BigDecimal LEXICAL_MATCH_WEIGHT = new BigDecimal("10.0");
    
    // Regex patterns for error analysis
    private static final Pattern STACK_TRACE_PATTERN = Pattern.compile(
        "at\\s+([\\w.$]+)\\.([\\w$]+)\\(([\\w.]+):(\\d+)\\)"
    );
    
    private static final Pattern COMPILATION_ERROR_PATTERN = Pattern.compile(
        "([A-Z]:[/\\\\][^\\s]+[/\\\\]src[/\\\\]main[/\\\\]java[/\\\\].+?\\.java)\\[\\d+,\\d+\\]|" +
        "([/\\\\][^\\s]*[/\\\\]src[/\\\\]main[/\\\\]java[/\\\\].+?\\.java)\\[\\d+,\\d+\\]|" +
        "([^\\s]+\\.java)\\[\\d+,\\d+\\]|" +
        "([A-Z]:[/\\\\][^\\s]+[/\\\\]src[/\\\\]main[/\\\\]java[/\\\\].+?\\.java):|" +
        "([/\\\\][^\\s]*[/\\\\]src[/\\\\]main[/\\\\]java[/\\\\].+?\\.java):"
    );
    
    private static final Pattern SPRING_CONTEXT_ERROR_PATTERN = Pattern.compile(
        "NoSuchBeanDefinitionException.*?'([\\w.$]+)'"
    );
    
    public RetrieverAgent(CandidateFileRepository candidateFileRepository,
                         PlanRepository planRepository,
                         SpringProjectAnalyzer springProjectAnalyzer) {
        this.candidateFileRepository = candidateFileRepository;
        this.planRepository = planRepository;
        this.springProjectAnalyzer = springProjectAnalyzer;
    }
    
    @Override
    public TaskResult handle(Task task, Map<String, Object> payload) {
        logger.info("Processing retrieval task for build ID: {}", task.getBuild().getId());
        
        try {
            Build build = task.getBuild();
            
            // Try to get working directory from payload first, fall back to default pattern
            String workingDir = extractWorkingDirectory(payload, build);
            logger.debug("Using working directory: {}", workingDir);
            
            // Get the plan for this build
            Optional<Plan> planOpt = planRepository.findByBuildId(build.getId());
            if (!planOpt.isPresent()) {
                return TaskResult.failure("No plan found for build ID: " + build.getId());
            }
            
            Plan plan = planOpt.get();
            Map<String, Object> planData = plan.getPlanJson();
            
            // Analyze Spring project context
            SpringProjectContext springContext = springProjectAnalyzer.analyzeProject(workingDir);
            logger.debug("Spring context analyzed: {}", springContext);
            
            // Extract error information from plan
            List<ErrorInfo> errors = extractErrorsFromPlan(planData);
            logger.debug("Extracted {} errors from plan", errors.size());
            
            // Find and rank candidate files
            List<CandidateFileInfo> candidates = findAndRankCandidateFiles(workingDir, errors, springContext);
            logger.info("Found {} candidate files", candidates.size());
            
            // Save candidate files to database
            saveCandidateFiles(build, candidates);
            
            // Prepare context window for LLM
            String contextWindow = buildContextWindow(candidates, springContext, errors);
            logger.debug("Built context window of {} characters", contextWindow.length());
            
            Map<String, Object> result = new HashMap<>();
            result.put("candidateCount", candidates.size());
            result.put("contextWindow", contextWindow);
            result.put("springContext", springContext);
            
            return TaskResult.success("Retrieved " + candidates.size() + " candidate files", result);
            
        } catch (Exception e) {
            logger.error("Error processing retrieval task", e);
            return TaskResult.failure("Retrieval failed: " + e.getMessage());
        }
    }
    
    /**
     * Extracts error information from the plan JSON structure.
     */
    private List<ErrorInfo> extractErrorsFromPlan(Map<String, Object> planData) {
        List<ErrorInfo> errors = new ArrayList<>();
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> steps = (List<Map<String, Object>>) planData.get("steps");
        
        if (steps != null) {
            for (Map<String, Object> step : steps) {
                String targetFile = (String) step.get("targetFile");
                String reasoning = (String) step.get("reasoning");
                String action = (String) step.get("action");
                
                if (targetFile != null) {
                    ErrorInfo error = new ErrorInfo();
                    error.setFilePath(targetFile);
                    error.setErrorMessage(reasoning != null ? reasoning : "Unknown error");
                    error.setErrorType(determineErrorType(action));
                    errors.add(error);
                }
            }
        }
        
        return errors;
    }
    
    /**
     * Determines error type from plan action.
     */
    private ErrorType determineErrorType(String action) {
        if (action == null) return ErrorType.UNKNOWN;
        
        switch (action.toUpperCase()) {
            case "ADD_SPRING_ANNOTATION":
                return ErrorType.SPRING_CONTEXT_ERROR;
            case "ADD_IMPORT_OR_DEPENDENCY":
                return ErrorType.MISSING_DEPENDENCY;
            case "FIX_COMPILATION_ERROR":
                return ErrorType.COMPILATION_ERROR;
            case "FIX_TEST_FAILURE":
                return ErrorType.TEST_FAILURE;
            default:
                return ErrorType.UNKNOWN;
        }
    }
    
    /**
     * Finds and ranks candidate files based on Spring-aware scoring.
     */
    private List<CandidateFileInfo> findAndRankCandidateFiles(String workingDir, 
                                                              List<ErrorInfo> errors, 
                                                              SpringProjectContext springContext) throws IOException {
        
        logger.info("🔍 STARTING CANDIDATE FILE ANALYSIS");
        logger.info("Working directory: {}", workingDir);
        logger.info("Total errors to analyze: {}", errors.size());
        logger.info("Spring context loaded: {}", springContext != null);
        
        Map<String, CandidateFileInfo> candidateMap = new HashMap<>();
        
        // Add files from compilation errors (highest priority)
        logger.info("📋 Phase 1: Analyzing compilation errors...");
        addCompilationErrorFiles(candidateMap, errors, workingDir);
        logger.info("Candidates after compilation errors: {}", candidateMap.size());
        
        // Add files from stack traces (high priority)
        logger.info("📋 Phase 2: Analyzing stack traces...");
        addStackTraceFiles(candidateMap, errors, workingDir);
        logger.info("Candidates after stack traces: {}", candidateMap.size());
        
        // Add files from Spring context errors
        logger.info("📋 Phase 3: Analyzing Spring context errors...");
        addSpringContextFiles(candidateMap, errors, workingDir);
        logger.info("Candidates after Spring context errors: {}", candidateMap.size());
        
        // Add Spring component files
        logger.info("📋 Phase 4: Analyzing Spring components...");
        addSpringComponentFiles(candidateMap, workingDir, springContext);
        logger.info("Candidates after Spring components: {}", candidateMap.size());
        
        // Add build files
        logger.info("📋 Phase 5: Analyzing build files...");
        addBuildFiles(candidateMap, workingDir, springContext);
        logger.info("Candidates after build files: {}", candidateMap.size());
        
        // Sort by score descending and limit results
        List<CandidateFileInfo> sortedCandidates = candidateMap.values().stream()
                .sorted((a, b) -> b.getScore().compareTo(a.getScore()))
                .limit(20) // Limit to top 20 candidates
                .collect(Collectors.toList());
        
        logger.info("🎯 CANDIDATE FILE ANALYSIS COMPLETE");
        logger.info("Total unique candidates found: {}", candidateMap.size());
        logger.info("Top candidates returned: {}", sortedCandidates.size());
        
        // Log top 5 candidates for debugging
        logger.info("=== TOP 5 CANDIDATES ===");
        for (int i = 0; i < Math.min(5, sortedCandidates.size()); i++) {
            CandidateFileInfo candidate = sortedCandidates.get(i);
            logger.info("{}. {} (score: {}) - {}", 
                i + 1, candidate.getFilePath(), candidate.getScore(), candidate.getReason());
        }
        
        return sortedCandidates;
    }
    
    /**
     * Adds files mentioned in compilation errors with highest priority.
     */
    private void addCompilationErrorFiles(Map<String, CandidateFileInfo> candidateMap, 
                                         List<ErrorInfo> errors, 
                                         String workingDir) {
        logger.info("=== COMPILATION ERROR FILE ANALYSIS ===");
        logger.info("Analyzing {} errors for compilation file references", errors.size());
        
        int compilationErrorsFound = 0;
        int filesAdded = 0;
        
        for (ErrorInfo error : errors) {
            if (error.getErrorMessage() != null) {
                logger.debug("Checking error message: {}", error.getErrorMessage().substring(0, Math.min(200, error.getErrorMessage().length())));
                
                Matcher matcher = COMPILATION_ERROR_PATTERN.matcher(error.getErrorMessage());
                while (matcher.find()) {
                    compilationErrorsFound++;
                    String filePath = null;
                    
                    // Check each capture group to find the file path
                    for (int i = 1; i <= matcher.groupCount(); i++) {
                        String group = matcher.group(i);
                        if (group != null && group.endsWith(".java")) {
                            filePath = group;
                            logger.debug("Found compilation error file path in group {}: {}", i, filePath);
                            break;
                        }
                    }
                    
                    if (filePath != null) {
                        // Convert absolute path to relative path if needed
                        String relativePath = normalizeFilePath(filePath, workingDir);
                        
                        if (relativePath != null && fileExists(workingDir, relativePath)) {
                            addOrUpdateCandidate(candidateMap, relativePath, COMPILATION_ERROR_WEIGHT, 
                                "Compilation error in file: " + relativePath);
                            filesAdded++;
                            logger.info("✅ COMPILATION ERROR FILE ADDED: {} (weight: {})", relativePath, COMPILATION_ERROR_WEIGHT);
                        } else {
                            logger.warn("❌ COMPILATION ERROR FILE NOT FOUND: {} (normalized: {})", filePath, relativePath);
                        }
                    } else {
                        logger.debug("Could not extract file path from compilation error match");
                    }
                }
            }
        }
        
        logger.info("=== COMPILATION ERROR ANALYSIS COMPLETE ===");
        logger.info("Compilation errors found: {}, Files successfully added: {}", compilationErrorsFound, filesAdded);
        
        if (compilationErrorsFound == 0) {
            logger.warn("⚠️  NO COMPILATION ERRORS DETECTED - This might indicate pattern matching issues");
        }
    }
    
    /**
     * Adds files mentioned in stack traces with highest priority.
     */
    private void addStackTraceFiles(Map<String, CandidateFileInfo> candidateMap, 
                                   List<ErrorInfo> errors, 
                                   String workingDir) {
        for (ErrorInfo error : errors) {
            if (error.getErrorMessage() != null) {
                Matcher matcher = STACK_TRACE_PATTERN.matcher(error.getErrorMessage());
                while (matcher.find()) {
                    String className = matcher.group(1);
                    
                    String javaFile = convertClassNameToFilePath(className);
                    if (javaFile != null && fileExists(workingDir, javaFile)) {
                        addOrUpdateCandidate(candidateMap, javaFile, STACK_TRACE_WEIGHT, 
                            "Stack trace reference: " + className);
                    }
                }
            }
        }
    }
    
    /**
     * Adds files related to Spring context errors.
     */
    private void addSpringContextFiles(Map<String, CandidateFileInfo> candidateMap, 
                                      List<ErrorInfo> errors, 
                                      String workingDir) {
        for (ErrorInfo error : errors) {
            if (error.getErrorType() == ErrorType.SPRING_CONTEXT_ERROR && error.getErrorMessage() != null) {
                Matcher matcher = SPRING_CONTEXT_ERROR_PATTERN.matcher(error.getErrorMessage());
                while (matcher.find()) {
                    String beanClass = matcher.group(1);
                    String javaFile = convertClassNameToFilePath(beanClass);
                    
                    if (javaFile != null && fileExists(workingDir, javaFile)) {
                        addOrUpdateCandidate(candidateMap, javaFile, SPRING_ERROR_WEIGHT, 
                            "Spring context error: missing bean " + beanClass);
                    }
                }
            }
        }
    }
    
    /**
     * Adds Spring component files based on annotations.
     */
    private void addSpringComponentFiles(Map<String, CandidateFileInfo> candidateMap, 
                                        String workingDir, 
                                        SpringProjectContext springContext) throws IOException {
        
        Path srcPath = Paths.get(workingDir, "src");
        if (!Files.exists(srcPath)) {
            return;
        }
        
        try (Stream<Path> paths = Files.walk(srcPath)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                 .forEach(javaFile -> {
                     try {
                         String content = new String(Files.readAllBytes(javaFile));
                         String relativePath = getRelativePath(workingDir, javaFile.toString());
                         
                         BigDecimal weight = calculateSpringAnnotationWeight(content);
                         if (weight.compareTo(BigDecimal.ZERO) > 0) {
                             String reason = "Spring component with annotations: " + 
                                           getFoundAnnotations(content);
                             addOrUpdateCandidate(candidateMap, relativePath, weight, reason);
                         }
                     } catch (IOException e) {
                         logger.debug("Could not read Java file {}: {}", javaFile, e.getMessage());
                     }
                 });
        }
    }
    
    /**
     * Adds build files (pom.xml, build.gradle).
     */
    private void addBuildFiles(Map<String, CandidateFileInfo> candidateMap, 
                              String workingDir, 
                              SpringProjectContext springContext) {
        
        String buildFile = springContext.getBuildTool().getBuildFile();
        if (fileExists(workingDir, buildFile)) {
            addOrUpdateCandidate(candidateMap, buildFile, BUILD_FILE_WEIGHT, 
                "Build configuration file");
        }
        
        // Also check for additional build files
        if (fileExists(workingDir, "pom.xml")) {
            addOrUpdateCandidate(candidateMap, "pom.xml", BUILD_FILE_WEIGHT, 
                "Maven build file");
        }
        if (fileExists(workingDir, "build.gradle")) {
            addOrUpdateCandidate(candidateMap, "build.gradle", BUILD_FILE_WEIGHT, 
                "Gradle build file");
        }
    }
    
    /**
     * Adds or updates a candidate file with cumulative scoring.
     */
    private void addOrUpdateCandidate(Map<String, CandidateFileInfo> candidateMap, 
                                     String filePath, 
                                     BigDecimal weight, 
                                     String reason) {
        
        CandidateFileInfo existing = candidateMap.get(filePath);
        if (existing != null) {
            // Cumulative scoring
            BigDecimal newScore = existing.getScore().add(weight);
            existing.setScore(newScore);
            existing.setReason(existing.getReason() + "; " + reason);
        } else {
            CandidateFileInfo candidate = new CandidateFileInfo();
            candidate.setFilePath(filePath);
            candidate.setScore(weight);
            candidate.setReason(reason);
            candidateMap.put(filePath, candidate);
        }
    }
    
    /**
     * Calculates Spring annotation weight for a Java file.
     */
    private BigDecimal calculateSpringAnnotationWeight(String content) {
        BigDecimal totalWeight = BigDecimal.ZERO;
        
        for (String annotation : HIGH_PRIORITY_ANNOTATIONS) {
            if (content.contains(annotation)) {
                totalWeight = totalWeight.add(HIGH_PRIORITY_ANNOTATION_WEIGHT);
            }
        }
        
        for (String annotation : MEDIUM_PRIORITY_ANNOTATIONS) {
            if (content.contains(annotation)) {
                totalWeight = totalWeight.add(MEDIUM_PRIORITY_ANNOTATION_WEIGHT);
            }
        }
        
        return totalWeight;
    }
    
    /**
     * Gets found Spring annotations in content for reasoning.
     */
    private String getFoundAnnotations(String content) {
        List<String> found = new ArrayList<>();
        
        for (String annotation : HIGH_PRIORITY_ANNOTATIONS) {
            if (content.contains(annotation)) {
                found.add(annotation);
            }
        }
        for (String annotation : MEDIUM_PRIORITY_ANNOTATIONS) {
            if (content.contains(annotation)) {
                found.add(annotation);
            }
        }
        
        return found.isEmpty() ? "none" : String.join(", ", found);
    }
    
    /**
     * Converts a Java class name to file path.
     */
    private String convertClassNameToFilePath(String className) {
        if (className == null || className.isEmpty()) {
            return null;
        }
        
        // Remove inner class references
        int dollarIndex = className.indexOf('$');
        if (dollarIndex > 0) {
            className = className.substring(0, dollarIndex);
        }
        
        return "src/main/java/" + className.replace('.', '/') + ".java";
    }
    
    /**
     * Checks if a file exists in the working directory.
     */
    private boolean fileExists(String workingDir, String filePath) {
        return new File(workingDir, filePath).exists();
    }
    
    /**
     * Gets relative path from working directory.
     */
    private String getRelativePath(String workingDir, String absolutePath) {
        Path workingPath = Paths.get(workingDir);
        Path filePath = Paths.get(absolutePath);
        return workingPath.relativize(filePath).toString().replace('\\', '/');
    }
    
    /**
     * Normalizes file path from compilation errors to relative path.
     */
    private String normalizeFilePath(String filePath, String workingDir) {
        if (filePath == null) {
            return null;
        }
        
        // If it's already a relative path starting with src/, use it directly
        if (filePath.startsWith("src/") || filePath.startsWith("src\\")) {
            return filePath.replace('\\', '/');
        }
        
        // If it's an absolute path, try to extract the relative part
        if (filePath.contains("/src/main/java/") || filePath.contains("\\src\\main\\java\\")) {
            int srcIndex = filePath.indexOf("/src/main/java/");
            if (srcIndex == -1) {
                srcIndex = filePath.indexOf("\\src\\main\\java\\");
            }
            if (srcIndex != -1) {
                return filePath.substring(srcIndex + 1).replace('\\', '/');
            }
        }
        
        // If it's just a filename, look for it in src/main/java
        if (!filePath.contains("/") && !filePath.contains("\\") && filePath.endsWith(".java")) {
            // This would require searching the directory tree, for now return null
            return null;
        }
        
        // Try to get relative path using existing method
        try {
            return getRelativePath(workingDir, filePath);
        } catch (Exception e) {
            logger.debug("Could not normalize file path {}: {}", filePath, e.getMessage());
            return null;
        }
    }
    
    /**
     * Saves candidate files to the database.
     */
    private void saveCandidateFiles(Build build, List<CandidateFileInfo> candidates) {
        // Delete existing candidates for this build
        candidateFileRepository.deleteByBuildId(build.getId());
        
        // Save new candidates
        for (CandidateFileInfo candidate : candidates) {
            CandidateFile entity = new CandidateFile(
                build, 
                candidate.getFilePath(), 
                candidate.getScore(), 
                candidate.getReason()
            );
            candidateFileRepository.save(entity);
        }
        
        logger.info("Saved {} candidate files for build {}", candidates.size(), build.getId());
    }
    
    /**
     * Builds context window for LLM consumption.
     */
    private String buildContextWindow(List<CandidateFileInfo> candidates, 
                                     SpringProjectContext springContext, 
                                     List<ErrorInfo> errors) {
        
        StringBuilder context = new StringBuilder();
        
        // Spring project context
        context.append("=== Spring Project Context ===\n");
        context.append("Spring Boot Version: ").append(springContext.getSpringBootVersion()).append("\n");
        context.append("Build Tool: ").append(springContext.getBuildTool()).append("\n");
        context.append("Spring Annotations: ").append(springContext.getRelevantAnnotations()).append("\n");
        context.append("Dependencies: ").append(springContext.getDependencyInfo()).append("\n");
        
        if (!springContext.getMavenModules().isEmpty()) {
            context.append("Maven Modules: ").append(String.join(", ", springContext.getMavenModules())).append("\n");
        }
        
        context.append("\n=== Error Summary ===\n");
        for (int i = 0; i < Math.min(errors.size(), 5); i++) {
            ErrorInfo error = errors.get(i);
            context.append("Error ").append(i + 1).append(": ").append(error.getErrorType())
                   .append(" - ").append(error.getErrorMessage()).append("\n");
        }
        
        context.append("\n=== Top Candidate Files ===\n");
        for (int i = 0; i < Math.min(candidates.size(), 10); i++) {
            CandidateFileInfo candidate = candidates.get(i);
            context.append(String.format("%d. %s (Score: %.2f) - %s\n", 
                i + 1, candidate.getFilePath(), candidate.getScore(), candidate.getReason()));
        }
        
        return context.toString();
    }
    
    /**
     * Inner class to hold candidate file information during processing.
     */
    private static class CandidateFileInfo {
        private String filePath;
        private BigDecimal score;
        private String reason;
        
        public String getFilePath() { return filePath; }
        public void setFilePath(String filePath) { this.filePath = filePath; }
        
        public BigDecimal getScore() { return score; }
        public void setScore(BigDecimal score) { this.score = score; }
        
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }
    
    /**
     * Inner class to hold error information extracted from plans.
     */
    private static class ErrorInfo {
        private String filePath;
        private String errorMessage;
        private ErrorType errorType;
        
        public String getFilePath() { return filePath; }
        public void setFilePath(String filePath) { this.filePath = filePath; }
        
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
        
        public ErrorType getErrorType() { return errorType; }
        public void setErrorType(ErrorType errorType) { this.errorType = errorType; }
    }
    
    /**
     * Enumeration of error types for classification.
     */
    private enum ErrorType {
        COMPILATION_ERROR,
        SPRING_CONTEXT_ERROR,
        MISSING_DEPENDENCY,
        TEST_FAILURE,
        UNKNOWN
    }
    
    /**
     * Extract working directory from payload or use default pattern.
     */
    private String extractWorkingDirectory(Map<String, Object> payload, Build build) {
        // Try to get working directory from payload first
        Object workingDirObj = payload.get("workingDirectory");
        if (workingDirObj != null) {
            String workingDir = workingDirObj.toString();
            logger.info("Using working directory from payload: {}", workingDir);
            return workingDir;
        }
        
        // Fall back to default pattern
        String defaultDir = workingDirBase + "/build-" + build.getId();
        logger.info("Using default working directory pattern: {}", defaultDir);
        return defaultDir;
    }
}