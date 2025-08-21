package com.example.cifixer.llm;

import com.example.cifixer.util.SpringProjectContext;

/**
 * Spring-specific prompt templates for LLM code generation.
 */
public class SpringPromptTemplate {
    
    private static final String SPRING_FIX_TEMPLATE = 
        "System: You are a senior Java Spring Boot developer with expertise in enterprise-grade applications. Fix this Spring Boot project issue following best practices.\n\n" +
        
        "🏗️ PROJECT INFORMATION:\n" +
        "Project: %s\n" +
        "Maven Module: %s\n" +
        "Spring Boot Version: %s\n" +
        "Build Tool: %s\n" +
        
        "🚨 ERROR CONTEXT:\n" +
        "%s\n\n" +
        
        "📁 PROJECT STRUCTURE:\n" +
        "%s\n\n" +
        
        "📄 FILE TO FIX (%s):\n" +
        "```java\n" +
        "%s\n" +
        "```\n\n" +
        
        "🌱 SPRING CONTEXT:\n" +
        "- Available Annotations: %s\n" +
        "- Project Dependencies: %s\n" +
        "- Active Profiles: %s\n\n" +
        
        "📋 JAVA SPRING PROJECT BEST PRACTICES:\n" +
        
        "🔒 IMPORT MANAGEMENT RULES:\n" +
        "- ONLY add missing imports that are explicitly mentioned in compilation errors\n" +
        "- NEVER remove existing imports (they may be used elsewhere in the file)\n" +
        "- Check project structure above to identify correct package paths for custom classes\n" +
        "- Do NOT add duplicate imports if they already exist in the file\n" +
        "- Use fully qualified names from the project structure for custom classes\n" +
        "- Standard library imports: java.util.*, java.io.*, etc.\n" +
        "- Spring framework imports: org.springframework.*, org.slf4j.* for logging\n" +
        
        "🌱 SPRING BOOT CONVENTIONS:\n" +
        "- Use @Service for business logic classes\n" +
        "- Use @RestController for REST API endpoints (includes @Controller + @ResponseBody)\n" +
        "- Use @Repository for data access layer\n" +
        "- Use @Component for generic Spring-managed beans\n" +
        "- Use @Configuration for configuration classes\n" +
        "- Prefer constructor injection over @Autowired field injection\n" +
        "- Use @Value for property injection: @Value(\"${property.name}\")\n" +
        "- Use @ConfigurationProperties for complex configuration binding\n" +
        
        "🧪 TEST FAILURE HANDLING:\n" +
        "- For failing tests, add @Ignore annotation with descriptive reason\n" +
        "- Add import: import org.junit.Ignore;\n" +
        "- Format: @Ignore(\"Reason: [specific reason for ignoring test]\")\n" +
        "- Place @Ignore annotation directly above the @Test method\n" +
        "- Do NOT modify test logic or assertions\n" +
        "- Do NOT remove test methods\n" +
        
        "💾 DEPENDENCY INJECTION:\n" +
        "- Prefer constructor injection: private final DependencyType dependency;\n" +
        "- Use @Autowired only when constructor injection isn't possible\n" +
        "- Inject interfaces, not implementations when possible\n" +
        "- Use @Qualifier when multiple beans of same type exist\n" +
        
        "🛡️ EXCEPTION HANDLING:\n" +
        "- Use proper Spring exception handling with @ExceptionHandler\n" +
        "- Return appropriate HTTP status codes (ResponseEntity)\n" +
        "- Log exceptions with proper log levels (error, warn, info, debug)\n" +
        "- Use slf4j Logger: private static final Logger logger = LoggerFactory.getLogger(ClassName.class);\n" +
        
        "⚠️ SAFETY RULES:\n" +
        "- Make ONLY minimal changes to fix the specific compilation error\n" +
        "- Do NOT refactor or optimize code beyond fixing the error\n" +
        "- Do NOT remove existing methods, fields, or annotations\n" +
        "- Do NOT modify method signatures unless explicitly required by error\n" +
        "- Do NOT change existing logic or business rules\n" +
        "- Preserve all existing comments and documentation\n" +
        
        "📝 CODE QUALITY:\n" +
        "- Follow Java naming conventions (camelCase for variables/methods, PascalCase for classes)\n" +
        "- Use meaningful variable and method names\n" +
        "- Maintain proper indentation and formatting\n" +
        "- Add brief comments for complex fixes when necessary\n" +
        
        "🎯 RESPONSE REQUIREMENTS:\n" +
        "- Return ONLY a minimal unified diff that fixes the specific compilation error\n" +
        "- Start with \"--- a/\" and \"+++ b/\" format\n" +
        "- Include only the lines that need to be changed\n" +
        "- Ensure the fix maintains Spring Boot compatibility\n" +
        "- Test your changes mentally against the project structure provided\n\n" +
        
        "FOCUS: Fix ONLY the compilation error mentioned. Do not make additional changes.";
        
    
    private static final String MAVEN_POM_TEMPLATE = 
        "System: You are a senior Java Spring Boot developer. Fix this Maven pom.xml dependency issue.\n\n" +
        "Project: %s\n" +
        "Spring Boot Version: %s\n" +
        "Error Context: %s\n\n" +
        "Current pom.xml content:\n" +
        "```xml\n" +
        "%s\n" +
        "```\n\n" +
        "Current Dependencies: %s\n\n" +
        "Instructions:\n" +
        "- Return ONLY a minimal unified diff that fixes the Maven dependency issue\n" +
        "- Add only the necessary Spring Boot starter dependencies\n" +
        "- Maintain compatibility with Spring Boot %s\n" +
        "- Use proper Maven dependency management\n" +
        "- Do not modify existing working dependencies\n" +
        "- Keep changes minimal and focused\n\n" +
        "Response format: Return only the unified diff starting with \"--- a/\" and \"+++ b/\"";
    
    private static final String GRADLE_BUILD_TEMPLATE = 
        "System: You are a senior Java Spring Boot developer. Fix this Gradle build.gradle dependency issue.\n\n" +
        "Project: %s\n" +
        "Spring Boot Version: %s\n" +
        "Error Context: %s\n\n" +
        "Current build.gradle content:\n" +
        "```gradle\n" +
        "%s\n" +
        "```\n\n" +
        "Current Dependencies: %s\n\n" +
        "Instructions:\n" +
        "- Return ONLY a minimal unified diff that fixes the Gradle dependency issue\n" +
        "- Add only the necessary Spring Boot starter dependencies\n" +
        "- Maintain compatibility with Spring Boot %s\n" +
        "- Use proper Gradle dependency syntax\n" +
        "- Do not modify existing working dependencies\n" +
        "- Keep changes minimal and focused\n\n" +
        "Response format: Return only the unified diff starting with \"--- a/\" and \"+++ b/\"";
    
    public static String buildSpringFixPrompt(String projectName, String filePath, String fileContent, 
                                            String errorContext, SpringProjectContext springContext) {
        return buildSpringFixPrompt(projectName, filePath, fileContent, errorContext, springContext, null);
    }
    
    public static String buildSpringFixPrompt(String projectName, String filePath, String fileContent, 
                                            String errorContext, SpringProjectContext springContext, 
                                            String projectStructure) {
        return String.format(SPRING_FIX_TEMPLATE,
            projectName,
            (springContext.getMavenModules() == null || springContext.getMavenModules().isEmpty()) ? "root" : springContext.getMavenModules().get(0),
            springContext.getSpringBootVersion(),
            springContext.getBuildTool().name(),
            errorContext,
            projectStructure != null ? projectStructure : "Project structure not available",
            filePath,
            fileContent,
            springContext.getSpringAnnotations() == null ? "None" : String.join(", ", springContext.getSpringAnnotations()),
            formatDependencies(springContext),
            springContext.getActiveProfiles() == null ? "None" : String.join(", ", springContext.getActiveProfiles())
        );
    }
    
    public static String buildMavenPomPrompt(String projectName, String fileContent, 
                                           String errorContext, SpringProjectContext springContext) {
        return String.format(MAVEN_POM_TEMPLATE,
            projectName,
            springContext.getSpringBootVersion(),
            errorContext,
            fileContent,
            formatDependencies(springContext),
            springContext.getSpringBootVersion()
        );
    }
    
    public static String buildGradleBuildPrompt(String projectName, String fileContent, 
                                              String errorContext, SpringProjectContext springContext) {
        return String.format(GRADLE_BUILD_TEMPLATE,
            projectName,
            springContext.getSpringBootVersion(),
            errorContext,
            fileContent,
            formatDependencies(springContext),
            springContext.getSpringBootVersion()
        );
    }
    
    private static String formatDependencies(SpringProjectContext springContext) {
        if (springContext.getDependencies() == null || springContext.getDependencies().isEmpty()) {
            return "None specified";
        }
        
        StringBuilder sb = new StringBuilder();
        springContext.getDependencies().forEach((key, value) -> {
            if (sb.length() > 0) sb.append(", ");
            sb.append(key).append(":").append(value);
        });
        return sb.toString();
    }
}