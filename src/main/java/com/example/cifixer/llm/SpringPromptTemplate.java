package com.example.cifixer.llm;

import com.example.cifixer.util.SpringProjectContext;

/**
 * Spring-specific prompt templates for LLM code generation.
 */
public class SpringPromptTemplate {
    
    private static final String SPRING_FIX_TEMPLATE = 
        "System: You are a senior Java Spring Boot developer. Fix this Spring Boot project issue.\n\n" +
        "Project: %s\n" +
        "Maven Module: %s\n" +
        "Spring Boot Version: %s\n" +
        "Build Tool: %s\n" +
        "Error Context: %s\n\n" +
        "File (%s):\n" +
        "```java\n" +
        "%s\n" +
        "```\n\n" +
        "Relevant Spring Context:\n" +
        "- Annotations in scope: %s\n" +
        "- Dependencies: %s\n" +
        "- Active Profiles: %s\n\n" +
        "Instructions:\n" +
        "- Return ONLY a minimal unified diff that fixes the Spring-specific error\n" +
        "- Follow Spring Boot best practices and conventions\n" +
        "- Use appropriate Spring annotations (@Service, @Repository, @Controller, @Component, etc.)\n" +
        "- Handle dependency injection properly with @Autowired or constructor injection\n" +
        "- Maintain Java 8 compatibility\n" +
        "- Keep changes minimal and focused on the specific error\n" +
        "- Do not add unnecessary imports or code\n" +
        "- Ensure proper package declarations\n\n" +
        "Response format: Return only the unified diff starting with \"--- a/\" and \"+++ b/\"";
    
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
        return String.format(SPRING_FIX_TEMPLATE,
            projectName,
            (springContext.getMavenModules() == null || springContext.getMavenModules().isEmpty()) ? "root" : springContext.getMavenModules().get(0),
            springContext.getSpringBootVersion(),
            springContext.getBuildTool().name(),
            errorContext,
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