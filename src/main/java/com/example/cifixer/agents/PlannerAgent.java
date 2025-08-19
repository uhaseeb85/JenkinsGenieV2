package com.example.cifixer.agents;

import com.example.cifixer.core.Agent;
import com.example.cifixer.core.Task;
import com.example.cifixer.core.TaskResult;
import com.example.cifixer.core.TaskType;
import com.example.cifixer.store.Build;
import com.example.cifixer.store.Plan;
import com.example.cifixer.store.PlanRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Agent responsible for analyzing Spring project build logs and creating structured fix plans.
 * Specializes in Maven/Gradle build failures, Spring context issues, and Java compilation errors.
 */
@Component
public class PlannerAgent implements Agent<Map<String, Object>> {
    
    private static final Logger logger = LoggerFactory.getLogger(PlannerAgent.class);
    
    private final PlanRepository planRepository;
    
    // Regex patterns for different error types
    private static final Pattern MAVEN_COMPILER_ERROR = Pattern.compile(
        "\\[ERROR\\]\\s+(.+?):\\[(\\d+),(\\d+)\\]\\s+(.+)"
    );
    
    private static final Pattern GRADLE_COMPILER_ERROR = Pattern.compile(
        "(.+?):(\\d+):\\s*error:\\s*(.+)"
    );
    
    private static final Pattern JAVA_STACK_TRACE = Pattern.compile(
        "at\\s+([\\w.$]+)\\.([\\w$]+)\\(([\\w.]+):(\\d+)\\)"
    );
    
    private static final Pattern SPRING_CONTEXT_ERROR = Pattern.compile(
        "NoSuchBeanDefinitionException.*?'([\\w.$]+)'"
    );
    
    private static final Pattern SPRING_AUTOWIRED_ERROR = Pattern.compile(
        "Could not autowire.*?private\\s+([\\w.$]+)\\s+"
    );
    
    private static final Pattern MAVEN_DEPENDENCY_ERROR = Pattern.compile(
        "Could not resolve dependencies for project\\s+([\\w.:$-]+)(?:\\s|$)"
    );
    
    private static final Pattern GRADLE_DEPENDENCY_ERROR = Pattern.compile(
        "Could not resolve\\s+([\\w.:$-]+:[\\w.:$-]+)"
    );
    
    private static final Pattern TEST_FAILURE = Pattern.compile(
        "FAILED:\\s+([\\w.$]+)\\.([\\w$]+)"
    );
    
    public PlannerAgent(PlanRepository planRepository) {
        this.planRepository = planRepository;
    }
    
    @Override
    public TaskResult handle(Task task, Map<String, Object> payload) {
        logger.info("Processing PLAN task for build: {}", task.getBuild().getId());
        
        try {
            Build build = task.getBuild();
            String buildLogs = extractBuildLogs(payload);
            
            if (buildLogs == null || buildLogs.trim().isEmpty()) {
                return TaskResult.failure("No build logs found in payload");
            }
            
            // Parse logs to identify errors
            List<ErrorInfo> errors = parseSpringProjectLogs(buildLogs);
            
            if (errors.isEmpty()) {
                logger.warn("No recognizable Spring/Java errors found in logs for build: {}", build.getId());
                return createManualInvestigationPlan(build, "No recognizable Spring/Java errors found");
            }
            
            // Generate structured plan
            Map<String, Object> planData = generateStructuredPlan(errors, build);
            
            // Save plan to database
            Plan plan = new Plan(build, planData);
            planRepository.save(plan);
            
            logger.info("Successfully created plan for build: {} with {} errors", 
                build.getId(), errors.size());
            
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("planId", plan.getId());
            metadata.put("errorCount", errors.size());
            metadata.put("errorTypes", errors.stream()
                .map(e -> e.getErrorType().name())
                .distinct()
                .collect(Collectors.toList()));
            
            return TaskResult.success("Plan created successfully", metadata);
            
        } catch (Exception e) {
            logger.error("Failed to process PLAN task for build: {}", 
                task.getBuild().getId(), e);
            return TaskResult.failure("Failed to create plan: " + e.getMessage());
        }
    }
    
    /**
     * Parses Spring project build logs to identify various error types.
     */
    public List<ErrorInfo> parseSpringProjectLogs(String logs) {
        List<ErrorInfo> errors = new ArrayList<>();
        
        // Limit log parsing to last 300 lines for performance
        String[] lines = logs.split("\n");
        int startIndex = Math.max(0, lines.length - 300);
        String limitedLogs = String.join("\n", Arrays.copyOfRange(lines, startIndex, lines.length));
        
        // Parse different error types
        errors.addAll(parseMavenCompilerErrors(limitedLogs));
        errors.addAll(parseGradleCompilerErrors(limitedLogs));
        errors.addAll(parseSpringContextErrors(limitedLogs));
        errors.addAll(parseJavaStackTraces(limitedLogs));
        errors.addAll(parseDependencyErrors(limitedLogs));
        errors.addAll(parseTestFailures(limitedLogs));
        
        // Remove duplicates and sort by priority
        return errors.stream()
            .distinct()
            .sorted(this::compareErrorPriority)
            .collect(Collectors.toList());
    }
    
    private List<ErrorInfo> parseMavenCompilerErrors(String logs) {
        List<ErrorInfo> errors = new ArrayList<>();
        Matcher matcher = MAVEN_COMPILER_ERROR.matcher(logs);
        
        while (matcher.find()) {
            ErrorInfo error = new ErrorInfo(ErrorType.COMPILATION_ERROR, matcher.group(4));
            error.setFilePath(normalizeFilePath(matcher.group(1)));
            error.setLineNumber(Integer.parseInt(matcher.group(2)));
            
            // Check if it's a missing dependency error
            if (matcher.group(4).contains("cannot find symbol")) {
                error.setErrorType(ErrorType.MISSING_DEPENDENCY);
                error.setMissingDependency(extractMissingSymbol(matcher.group(4)));
            }
            
            errors.add(error);
        }
        
        return errors;
    }
    
    private List<ErrorInfo> parseGradleCompilerErrors(String logs) {
        List<ErrorInfo> errors = new ArrayList<>();
        Matcher matcher = GRADLE_COMPILER_ERROR.matcher(logs);
        
        while (matcher.find()) {
            ErrorInfo error = new ErrorInfo(ErrorType.COMPILATION_ERROR, matcher.group(3));
            error.setFilePath(normalizeFilePath(matcher.group(1)));
            error.setLineNumber(Integer.parseInt(matcher.group(2)));
            
            // Check if it's a missing dependency error
            if (matcher.group(3).contains("cannot find symbol")) {
                error.setErrorType(ErrorType.MISSING_DEPENDENCY);
                error.setMissingDependency(extractMissingSymbol(matcher.group(3)));
            }
            
            errors.add(error);
        }
        
        return errors;
    }
    
    private List<ErrorInfo> parseSpringContextErrors(String logs) {
        List<ErrorInfo> errors = new ArrayList<>();
        
        // NoSuchBeanDefinitionException
        Matcher beanMatcher = SPRING_CONTEXT_ERROR.matcher(logs);
        while (beanMatcher.find()) {
            ErrorInfo error = new ErrorInfo(ErrorType.SPRING_CONTEXT_ERROR, 
                "Missing Spring bean: " + beanMatcher.group(1));
            error.setMissingBean(beanMatcher.group(1));
            errors.add(error);
        }
        
        // Autowiring errors
        Matcher autowiredMatcher = SPRING_AUTOWIRED_ERROR.matcher(logs);
        while (autowiredMatcher.find()) {
            ErrorInfo error = new ErrorInfo(ErrorType.SPRING_ANNOTATION_ERROR, 
                "Autowiring failed for: " + autowiredMatcher.group(1));
            error.setMissingBean(autowiredMatcher.group(1));
            errors.add(error);
        }
        
        return errors;
    }
    
    private List<ErrorInfo> parseJavaStackTraces(String logs) {
        List<ErrorInfo> errors = new ArrayList<>();
        Matcher matcher = JAVA_STACK_TRACE.matcher(logs);
        
        while (matcher.find()) {
            String className = matcher.group(1);
            String methodName = matcher.group(2);
            String fileName = matcher.group(3);
            int lineNumber = Integer.parseInt(matcher.group(4));
            
            ErrorInfo error = new ErrorInfo(ErrorType.STACK_TRACE_ERROR, 
                "Exception in " + className + "." + methodName);
            error.setFilePath(convertClassNameToFilePath(className, fileName));
            error.setLineNumber(lineNumber);
            error.setStackTrace(matcher.group(0));
            
            errors.add(error);
        }
        
        return errors;
    }
    
    private List<ErrorInfo> parseDependencyErrors(String logs) {
        List<ErrorInfo> errors = new ArrayList<>();
        
        // Maven dependency errors
        Matcher mavenMatcher = MAVEN_DEPENDENCY_ERROR.matcher(logs);
        while (mavenMatcher.find()) {
            ErrorInfo error = new ErrorInfo(ErrorType.DEPENDENCY_RESOLUTION_ERROR, 
                "Maven dependency resolution failed: " + mavenMatcher.group(1));
            error.setMissingDependency(mavenMatcher.group(1));
            errors.add(error);
        }
        
        // Gradle dependency errors
        Matcher gradleMatcher = GRADLE_DEPENDENCY_ERROR.matcher(logs);
        while (gradleMatcher.find()) {
            ErrorInfo error = new ErrorInfo(ErrorType.DEPENDENCY_RESOLUTION_ERROR, 
                "Gradle dependency resolution failed: " + gradleMatcher.group(1));
            error.setMissingDependency(gradleMatcher.group(1));
            errors.add(error);
        }
        
        return errors;
    }
    
    private List<ErrorInfo> parseTestFailures(String logs) {
        List<ErrorInfo> errors = new ArrayList<>();
        Matcher matcher = TEST_FAILURE.matcher(logs);
        
        while (matcher.find()) {
            String testClass = matcher.group(1);
            String testMethod = matcher.group(2);
            
            ErrorInfo error = new ErrorInfo(ErrorType.TEST_FAILURE, 
                "Test failed: " + testClass + "." + testMethod);
            error.setFilePath(convertClassNameToFilePath(testClass, testClass + ".java"));
            error.setFailedTest(testClass + "." + testMethod);
            
            errors.add(error);
        }
        
        return errors;
    }
    
    private Map<String, Object> generateStructuredPlan(List<ErrorInfo> errors, Build build) {
        Map<String, Object> planData = new HashMap<>();
        List<PlanStep> steps = new ArrayList<>();
        
        planData.put("buildId", build.getId());
        planData.put("summary", generatePlanSummary(errors));
        planData.put("errorCount", errors.size());
        
        // Group errors by type and generate steps
        Map<ErrorType, List<ErrorInfo>> errorsByType = errors.stream()
            .collect(Collectors.groupingBy(ErrorInfo::getErrorType));
        
        int stepPriority = 1;
        
        // Handle Spring context errors first (highest priority)
        if (errorsByType.containsKey(ErrorType.SPRING_CONTEXT_ERROR)) {
            steps.addAll(generateSpringContextSteps(errorsByType.get(ErrorType.SPRING_CONTEXT_ERROR), stepPriority));
            stepPriority += errorsByType.get(ErrorType.SPRING_CONTEXT_ERROR).size();
        }
        
        // Handle missing dependencies
        if (errorsByType.containsKey(ErrorType.MISSING_DEPENDENCY)) {
            steps.addAll(generateDependencySteps(errorsByType.get(ErrorType.MISSING_DEPENDENCY), stepPriority));
            stepPriority += errorsByType.get(ErrorType.MISSING_DEPENDENCY).size();
        }
        
        // Handle compilation errors
        if (errorsByType.containsKey(ErrorType.COMPILATION_ERROR)) {
            steps.addAll(generateCompilationSteps(errorsByType.get(ErrorType.COMPILATION_ERROR), stepPriority));
            stepPriority += errorsByType.get(ErrorType.COMPILATION_ERROR).size();
        }
        
        // Handle test failures
        if (errorsByType.containsKey(ErrorType.TEST_FAILURE)) {
            steps.addAll(generateTestFailureSteps(errorsByType.get(ErrorType.TEST_FAILURE), stepPriority));
            stepPriority += errorsByType.get(ErrorType.TEST_FAILURE).size();
        }
        
        // Handle other error types
        for (Map.Entry<ErrorType, List<ErrorInfo>> entry : errorsByType.entrySet()) {
            if (!Arrays.asList(ErrorType.SPRING_CONTEXT_ERROR, ErrorType.MISSING_DEPENDENCY, 
                    ErrorType.COMPILATION_ERROR, ErrorType.TEST_FAILURE).contains(entry.getKey())) {
                steps.addAll(generateGenericSteps(entry.getValue(), stepPriority));
                stepPriority += entry.getValue().size();
            }
        }
        
        // Convert PlanStep objects to Maps for JSON serialization
        List<Map<String, Object>> stepMaps = steps.stream()
            .map(this::convertPlanStepToMap)
            .collect(Collectors.toList());
        planData.put("steps", stepMaps);
        planData.put("createdAt", new Date());
        
        return planData;
    }
    
    private String generatePlanSummary(List<ErrorInfo> errors) {
        Map<ErrorType, Long> errorCounts = errors.stream()
            .collect(Collectors.groupingBy(ErrorInfo::getErrorType, Collectors.counting()));
        
        StringBuilder summary = new StringBuilder("Spring project build failure analysis: ");
        
        for (Map.Entry<ErrorType, Long> entry : errorCounts.entrySet()) {
            summary.append(entry.getValue()).append(" ").append(entry.getKey().name().toLowerCase())
                   .append(entry.getValue() > 1 ? "s" : "").append(", ");
        }
        
        return summary.toString().replaceAll(", $", "");
    }
    
    private List<PlanStep> generateSpringContextSteps(List<ErrorInfo> errors, int startPriority) {
        List<PlanStep> steps = new ArrayList<>();
        
        for (int i = 0; i < errors.size(); i++) {
            ErrorInfo error = errors.get(i);
            PlanStep step = new PlanStep();
            step.setDescription("Fix Spring context error: " + error.getMissingBean());
            step.setAction("ADD_SPRING_ANNOTATION");
            step.setReasoning("Missing Spring bean definition for " + error.getMissingBean());
            step.setPriority(startPriority + i);
            
            List<String> components = Arrays.asList("@Component", "@Service", "@Repository", "@Configuration");
            step.setSpringComponents(components);
            
            steps.add(step);
        }
        
        return steps;
    }
    
    private List<PlanStep> generateDependencySteps(List<ErrorInfo> errors, int startPriority) {
        List<PlanStep> steps = new ArrayList<>();
        
        for (int i = 0; i < errors.size(); i++) {
            ErrorInfo error = errors.get(i);
            PlanStep step = new PlanStep();
            step.setDescription("Add missing import or dependency: " + error.getMissingDependency());
            step.setAction("ADD_IMPORT_OR_DEPENDENCY");
            step.setTargetFile(error.getFilePath());
            step.setReasoning("Missing symbol: " + error.getMissingDependency());
            step.setPriority(startPriority + i);
            
            steps.add(step);
        }
        
        return steps;
    }
    
    private List<PlanStep> generateCompilationSteps(List<ErrorInfo> errors, int startPriority) {
        List<PlanStep> steps = new ArrayList<>();
        
        for (int i = 0; i < errors.size(); i++) {
            ErrorInfo error = errors.get(i);
            PlanStep step = new PlanStep();
            step.setDescription("Fix compilation error in " + error.getFilePath());
            step.setAction("FIX_COMPILATION_ERROR");
            step.setTargetFile(error.getFilePath());
            step.setReasoning("Compilation error at line " + error.getLineNumber());
            step.setPriority(startPriority + i);
            
            steps.add(step);
        }
        
        return steps;
    }
    
    private List<PlanStep> generateTestFailureSteps(List<ErrorInfo> errors, int startPriority) {
        List<PlanStep> steps = new ArrayList<>();
        
        for (int i = 0; i < errors.size(); i++) {
            ErrorInfo error = errors.get(i);
            PlanStep step = new PlanStep();
            step.setDescription("Fix test failure: " + error.getFailedTest());
            step.setAction("FIX_TEST_FAILURE");
            step.setTargetFile(error.getFilePath());
            step.setReasoning("Test failure in " + error.getFailedTest());
            step.setPriority(startPriority + i);
            
            steps.add(step);
        }
        
        return steps;
    }
    
    private List<PlanStep> generateGenericSteps(List<ErrorInfo> errors, int startPriority) {
        List<PlanStep> steps = new ArrayList<>();
        
        for (int i = 0; i < errors.size(); i++) {
            ErrorInfo error = errors.get(i);
            PlanStep step = new PlanStep();
            step.setDescription("Address " + error.getErrorType().name().toLowerCase() + ": " + error.getErrorMessage());
            step.setAction("INVESTIGATE_ERROR");
            step.setTargetFile(error.getFilePath());
            step.setReasoning(error.getErrorMessage());
            step.setPriority(startPriority + i);
            
            steps.add(step);
        }
        
        return steps;
    }
    
    private TaskResult createManualInvestigationPlan(Build build, String reason) {
        Map<String, Object> planData = new HashMap<>();
        planData.put("buildId", build.getId());
        planData.put("summary", "Manual investigation required: " + reason);
        planData.put("errorCount", 0);
        
        PlanStep manualStep = new PlanStep();
        manualStep.setDescription("Manual investigation required");
        manualStep.setAction("MANUAL_INVESTIGATION");
        manualStep.setReasoning(reason);
        manualStep.setPriority(1);
        
        planData.put("steps", Arrays.asList(convertPlanStepToMap(manualStep)));
        planData.put("createdAt", new Date());
        
        Plan plan = new Plan(build, planData);
        planRepository.save(plan);
        
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("planId", plan.getId());
        metadata.put("requiresManualInvestigation", true);
        
        return TaskResult.success("Manual investigation plan created", metadata);
    }
    
    // Helper methods
    private String extractBuildLogs(Map<String, Object> payload) {
        Object logs = payload.get("buildLogs");
        return logs != null ? logs.toString() : null;
    }
    
    private String normalizeFilePath(String filePath) {
        if (filePath == null) return null;
        
        // Convert absolute paths to relative paths
        if (filePath.contains("/src/main/java/")) {
            return filePath.substring(filePath.indexOf("/src/main/java/") + 1);
        }
        if (filePath.contains("/src/test/java/")) {
            return filePath.substring(filePath.indexOf("/src/test/java/") + 1);
        }
        
        return filePath;
    }
    
    private String convertClassNameToFilePath(String className, String fileName) {
        if (className == null) return fileName;
        
        // If fileName already contains the full class name, just use the simple name
        if (fileName.contains(className)) {
            String simpleClassName = className.substring(className.lastIndexOf('.') + 1);
            fileName = simpleClassName + ".java";
        }
        
        String packagePath = className.replace('.', '/');
        int lastDot = packagePath.lastIndexOf('/');
        
        if (lastDot > 0) {
            return "src/main/java/" + packagePath.substring(0, lastDot + 1) + fileName;
        }
        
        return "src/main/java/" + fileName;
    }
    
    private String extractMissingSymbol(String errorMessage) {
        if (errorMessage.contains("cannot find symbol")) {
            // Extract symbol name from error message
            Pattern symbolPattern = Pattern.compile("symbol:\\s*(?:class|variable|method)\\s+(\\w+)");
            Matcher matcher = symbolPattern.matcher(errorMessage);
            if (matcher.find()) {
                return matcher.group(1);
            }
            
            // Try alternative pattern for "cannot find symbol class ClassName"
            Pattern altPattern = Pattern.compile("cannot find symbol\\s+class\\s+(\\w+)");
            Matcher altMatcher = altPattern.matcher(errorMessage);
            if (altMatcher.find()) {
                return altMatcher.group(1);
            }
        }
        return "unknown";
    }
    
    private int compareErrorPriority(ErrorInfo e1, ErrorInfo e2) {
        // Priority order: Spring context errors > Missing dependencies > Compilation errors > Others
        Map<ErrorType, Integer> priorities = Map.of(
            ErrorType.SPRING_CONTEXT_ERROR, 1,
            ErrorType.SPRING_ANNOTATION_ERROR, 2,
            ErrorType.MISSING_DEPENDENCY, 3,
            ErrorType.COMPILATION_ERROR, 4,
            ErrorType.STACK_TRACE_ERROR, 5,
            ErrorType.TEST_FAILURE, 6,
            ErrorType.DEPENDENCY_RESOLUTION_ERROR, 7,
            ErrorType.BUILD_CONFIGURATION_ERROR, 8,
            ErrorType.UNKNOWN_ERROR, 9
        );
        
        return priorities.getOrDefault(e1.getErrorType(), 9)
            .compareTo(priorities.getOrDefault(e2.getErrorType(), 9));
    }
    
    private Map<String, Object> convertPlanStepToMap(PlanStep step) {
        Map<String, Object> stepMap = new HashMap<>();
        stepMap.put("description", step.getDescription());
        stepMap.put("action", step.getAction());
        stepMap.put("targetFile", step.getTargetFile());
        stepMap.put("springComponents", step.getSpringComponents());
        stepMap.put("dependencies", step.getDependencies());
        stepMap.put("reasoning", step.getReasoning());
        stepMap.put("priority", step.getPriority());
        return stepMap;
    }
}