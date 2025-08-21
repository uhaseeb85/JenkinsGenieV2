package com.example.cifixer.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Analyzes Spring Boot projects to extract context information including
 * Spring Boot version, build tool, modules, annotations, and dependencies.
 */
@Component
public class SpringProjectAnalyzer {
    
    private static final Logger logger = LoggerFactory.getLogger(SpringProjectAnalyzer.class);
    
    // Spring Boot version patterns
    private static final Pattern MAVEN_SPRING_BOOT_VERSION = Pattern.compile(
        "<spring-boot\\.version>([^<]+)</spring-boot\\.version>|" +
        "<artifactId>spring-boot-starter-parent</artifactId>.*?<version>([^<]+)</version>", 
        Pattern.DOTALL);
    
    private static final Pattern GRADLE_SPRING_BOOT_VERSION = Pattern.compile(
        "id\\s+['\"]org\\.springframework\\.boot['\"]\\s+version\\s+['\"]([^'\"]+)['\"]|" +
        "springBootVersion\\s*=\\s*['\"]([^'\"]+)['\"]");
    
    // Maven module pattern
    private static final Pattern MAVEN_MODULE_PATTERN = Pattern.compile("<module>([^<]+)</module>");
    
    // Spring annotation patterns
    private static final Set<String> SPRING_ANNOTATIONS = Set.of(
        "@SpringBootApplication", "@Controller", "@RestController", "@Service", 
        "@Repository", "@Component", "@Configuration", "@EnableAutoConfiguration",
        "@ComponentScan", "@EntityScan", "@EnableJpaRepositories", "@Autowired",
        "@Value", "@ConfigurationProperties", "@Profile", "@Conditional"
    );
    
    // Dependency patterns
    private static final Pattern MAVEN_DEPENDENCY_PATTERN = Pattern.compile(
        "<dependency>.*?<groupId>([^<]+)</groupId>.*?<artifactId>([^<]+)</artifactId>.*?</dependency>",
        Pattern.DOTALL);
    
    private static final Pattern MAVEN_VERSION_PATTERN = Pattern.compile(
        "<version>([^<]+)</version>");
    
    private static final Pattern GRADLE_DEPENDENCY_PATTERN = Pattern.compile(
        "(?:implementation|compile|api|testImplementation)\\s+['\"]([^:'\"]+):([^:'\"]+)(?::([^'\"]+))?['\"]");
    
    /**
     * Analyzes a Spring Boot project directory and returns context information.
     * 
     * @param workingDir the root directory of the Spring Boot project
     * @return SpringProjectContext containing project analysis results
     * @throws IllegalStateException if no Maven or Gradle build file is found
     */
    public SpringProjectContext analyzeProject(String workingDir) {
        logger.info("Analyzing Spring project at: {}", workingDir);
        
        SpringProjectContext context = new SpringProjectContext();
        
        // Detect build tool
        BuildTool buildTool = detectBuildTool(workingDir);
        context.setBuildTool(buildTool);
        logger.debug("Detected build tool: {}", buildTool);
        
        // Detect Spring Boot version
        String springBootVersion = detectSpringBootVersion(workingDir, buildTool);
        context.setSpringBootVersion(springBootVersion);
        logger.debug("Detected Spring Boot version: {}", springBootVersion);
        
        // Find Maven modules (for multi-module projects)
        List<String> modules = findMavenModules(workingDir, buildTool);
        context.setMavenModules(modules);
        logger.debug("Found modules: {}", modules);
        
        // Find Spring annotations in Java source files
        Set<String> annotations = findSpringAnnotations(workingDir);
        context.setSpringAnnotations(annotations);
        logger.debug("Found Spring annotations: {}", annotations);
        
        // Parse dependencies
        Map<String, String> dependencies = parseDependencies(workingDir, buildTool);
        context.setDependencies(dependencies);
        logger.debug("Found {} dependencies", dependencies.size());
        
        // Find active profiles (basic implementation)
        List<String> profiles = findActiveProfiles(workingDir);
        context.setActiveProfiles(profiles);
        
        return context;
    }
    
    /**
     * Detects the build tool used by the project.
     */
    public BuildTool detectBuildTool(String workingDir) {
        File pomFile = new File(workingDir, "pom.xml");
        File gradleFile = new File(workingDir, "build.gradle");
        File gradleKtsFile = new File(workingDir, "build.gradle.kts");
        
        if (pomFile.exists()) {
            return BuildTool.MAVEN;
        } else if (gradleFile.exists() || gradleKtsFile.exists()) {
            return BuildTool.GRADLE;
        } else {
            throw new IllegalStateException("No Maven (pom.xml) or Gradle (build.gradle) build file found in: " + workingDir);
        }
    }
    
    /**
     * Detects the Spring Boot version from build files.
     */
    public String detectSpringBootVersion(String workingDir, BuildTool buildTool) {
        try {
            if (buildTool == BuildTool.MAVEN) {
                return detectSpringBootVersionFromMaven(workingDir);
            } else {
                return detectSpringBootVersionFromGradle(workingDir);
            }
        } catch (Exception e) {
            logger.warn("Could not detect Spring Boot version: {}", e.getMessage());
            return "unknown";
        }
    }
    
    private String detectSpringBootVersionFromMaven(String workingDir) throws IOException {
        Path pomPath = Paths.get(workingDir, "pom.xml");
        if (!Files.exists(pomPath)) {
            return "unknown";
        }
        
        String pomContent = new String(Files.readAllBytes(pomPath));
        Matcher matcher = MAVEN_SPRING_BOOT_VERSION.matcher(pomContent);
        
        if (matcher.find()) {
            // Try first group (spring-boot.version property)
            String version = matcher.group(1);
            if (version != null && !version.trim().isEmpty()) {
                return version.trim();
            }
            // Try second group (parent version)
            version = matcher.group(2);
            if (version != null && !version.trim().isEmpty()) {
                return version.trim();
            }
        }
        
        return "unknown";
    }
    
    private String detectSpringBootVersionFromGradle(String workingDir) throws IOException {
        Path gradlePath = Paths.get(workingDir, "build.gradle");
        Path gradleKtsPath = Paths.get(workingDir, "build.gradle.kts");
        
        Path buildFile = Files.exists(gradlePath) ? gradlePath : gradleKtsPath;
        if (!Files.exists(buildFile)) {
            return "unknown";
        }
        
        String buildContent = new String(Files.readAllBytes(buildFile));
        Matcher matcher = GRADLE_SPRING_BOOT_VERSION.matcher(buildContent);
        
        if (matcher.find()) {
            String version = matcher.group(1);
            if (version != null) {
                return version.trim();
            }
            version = matcher.group(2);
            if (version != null) {
                return version.trim();
            }
        }
        
        return "unknown";
    }
    
    /**
     * Finds Maven modules in multi-module projects.
     */
    public List<String> findMavenModules(String workingDir, BuildTool buildTool) {
        if (buildTool != BuildTool.MAVEN) {
            return Collections.emptyList();
        }
        
        try {
            Path pomPath = Paths.get(workingDir, "pom.xml");
            if (!Files.exists(pomPath)) {
                return Collections.emptyList();
            }
            
            String pomContent = new String(Files.readAllBytes(pomPath));
            Matcher matcher = MAVEN_MODULE_PATTERN.matcher(pomContent);
            
            List<String> modules = new ArrayList<>();
            while (matcher.find()) {
                modules.add(matcher.group(1).trim());
            }
            
            return modules;
        } catch (IOException e) {
            logger.warn("Could not read pom.xml for module detection: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
    
    /**
     * Finds Spring annotations in Java source files.
     */
    public Set<String> findSpringAnnotations(String workingDir) {
        Set<String> foundAnnotations = new HashSet<>();
        
        try {
            Path srcPath = Paths.get(workingDir, "src");
            if (!Files.exists(srcPath)) {
                return foundAnnotations;
            }
            
            try (Stream<Path> paths = Files.walk(srcPath)) {
                paths.filter(path -> path.toString().endsWith(".java"))
                     .forEach(javaFile -> {
                         try {
                             String content = new String(Files.readAllBytes(javaFile));
                             for (String annotation : SPRING_ANNOTATIONS) {
                                 if (content.contains(annotation)) {
                                     foundAnnotations.add(annotation);
                                 }
                             }
                         } catch (IOException e) {
                             logger.debug("Could not read Java file {}: {}", javaFile, e.getMessage());
                         }
                     });
            }
        } catch (IOException e) {
            logger.warn("Could not scan for Spring annotations: {}", e.getMessage());
        }
        
        return foundAnnotations;
    }
    
    /**
     * Parses dependencies from build files.
     */
    public Map<String, String> parseDependencies(String workingDir, BuildTool buildTool) {
        try {
            if (buildTool == BuildTool.MAVEN) {
                return parseMavenDependencies(workingDir);
            } else {
                return parseGradleDependencies(workingDir);
            }
        } catch (Exception e) {
            logger.warn("Could not parse dependencies: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }
    
    private Map<String, String> parseMavenDependencies(String workingDir) throws IOException {
        Path pomPath = Paths.get(workingDir, "pom.xml");
        if (!Files.exists(pomPath)) {
            return Collections.emptyMap();
        }
        
        String pomContent = new String(Files.readAllBytes(pomPath));
        Matcher matcher = MAVEN_DEPENDENCY_PATTERN.matcher(pomContent);
        
        Map<String, String> dependencies = new HashMap<>();
        while (matcher.find()) {
            String dependencyBlock = matcher.group(0);
            String groupId = matcher.group(1).trim();
            String artifactId = matcher.group(2).trim();
            
            // Look for version within this dependency block
            Matcher versionMatcher = MAVEN_VERSION_PATTERN.matcher(dependencyBlock);
            String version = "managed";
            if (versionMatcher.find()) {
                version = versionMatcher.group(1).trim();
            }
            
            String key = groupId + ":" + artifactId;
            dependencies.put(key, version);
        }
        
        return dependencies;
    }
    
    private Map<String, String> parseGradleDependencies(String workingDir) throws IOException {
        Path gradlePath = Paths.get(workingDir, "build.gradle");
        Path gradleKtsPath = Paths.get(workingDir, "build.gradle.kts");
        
        Path buildFile = Files.exists(gradlePath) ? gradlePath : gradleKtsPath;
        if (!Files.exists(buildFile)) {
            return Collections.emptyMap();
        }
        
        String buildContent = new String(Files.readAllBytes(buildFile));
        Matcher matcher = GRADLE_DEPENDENCY_PATTERN.matcher(buildContent);
        
        Map<String, String> dependencies = new HashMap<>();
        while (matcher.find()) {
            String groupId = matcher.group(1).trim();
            String artifactId = matcher.group(2).trim();
            String version = matcher.group(3);
            
            String key = groupId + ":" + artifactId;
            String value = version != null ? version.trim() : "managed";
            dependencies.put(key, value);
        }
        
        return dependencies;
    }
    
    /**
     * Finds active Spring profiles (basic implementation).
     */
    public List<String> findActiveProfiles(String workingDir) {
        List<String> profiles = new ArrayList<>();
        
        try {
            // Check application.properties
            Path propsPath = Paths.get(workingDir, "src/main/resources/application.properties");
            if (Files.exists(propsPath)) {
                String content = new String(Files.readAllBytes(propsPath));
                if (content.contains("spring.profiles.active=")) {
                    String[] lines = content.split("\n");
                    for (String line : lines) {
                        if (line.trim().startsWith("spring.profiles.active=")) {
                            String profilesStr = line.substring(line.indexOf("=") + 1).trim();
                            profiles.addAll(Arrays.asList(profilesStr.split(",")));
                            break;
                        }
                    }
                }
            }
            
            // Check application.yml
            Path ymlPath = Paths.get(workingDir, "src/main/resources/application.yml");
            if (Files.exists(ymlPath)) {
                String content = new String(Files.readAllBytes(ymlPath));
                if (content.contains("active:")) {
                    // Basic YAML parsing - could be enhanced
                    String[] lines = content.split("\n");
                    boolean inSpringSection = false;
                    boolean inProfilesSection = false;
                    
                    for (String line : lines) {
                        String trimmed = line.trim();
                        if (trimmed.startsWith("spring:")) {
                            inSpringSection = true;
                            inProfilesSection = false;
                        } else if (inSpringSection && trimmed.startsWith("profiles:")) {
                            inProfilesSection = true;
                        } else if (inProfilesSection && trimmed.startsWith("active:")) {
                            String profilesStr = trimmed.substring(7).trim();
                            profiles.addAll(Arrays.asList(profilesStr.split(",")));
                            break;
                        } else if (!line.startsWith(" ") && !line.startsWith("\t")) {
                            inSpringSection = false;
                            inProfilesSection = false;
                        }
                    }
                }
            }
        } catch (IOException e) {
            logger.debug("Could not read application configuration files: {}", e.getMessage());
        }
        
        return profiles.stream()
                      .map(String::trim)
                      .filter(s -> !s.isEmpty())
                      .collect(Collectors.toList());
    }
    
    /**
     * Generates comprehensive project structure information for LLM context.
     * 
     * @param workingDir the root directory of the Spring Boot project
     * @return String containing detailed project structure
     */
    public String generateProjectStructure(String workingDir) {
        logger.info("Generating project structure for LLM context at: {}", workingDir);
        
        StringBuilder structure = new StringBuilder();
        structure.append("=== PROJECT STRUCTURE ===\n");
        
        // 1. Build configuration files
        structure.append("\n📋 Build Configuration:\n");
        if (new File(workingDir, "pom.xml").exists()) {
            structure.append("- pom.xml (Maven project)\n");
        }
        if (new File(workingDir, "build.gradle").exists() || new File(workingDir, "build.gradle.kts").exists()) {
            structure.append("- build.gradle (Gradle project)\n");
        }
        
        // 2. Application properties and configuration
        structure.append("\n⚙️ Configuration Files:\n");
        scanConfigurationFiles(workingDir, structure);
        
        // 3. Java package structure with classes
        structure.append("\n📦 Java Package Structure:\n");
        scanJavaPackages(workingDir, structure);
        
        // 4. Test structure
        structure.append("\n🧪 Test Structure:\n");
        scanTestPackages(workingDir, structure);
        
        // 5. Resources
        structure.append("\n📁 Resources:\n");
        scanResourceFiles(workingDir, structure);
        
        structure.append("\n=== END PROJECT STRUCTURE ===\n");
        
        logger.debug("Generated project structure with {} characters", structure.length());
        return structure.toString();
    }
    
    private void scanConfigurationFiles(String workingDir, StringBuilder structure) {
        String[] configFiles = {
            "src/main/resources/application.yml",
            "src/main/resources/application.yaml", 
            "src/main/resources/application.properties",
            "src/main/resources/application-dev.properties",
            "src/main/resources/application-prod.properties",
            "src/main/resources/application-test.properties"
        };
        
        for (String configFile : configFiles) {
            if (new File(workingDir, configFile).exists()) {
                structure.append("  - ").append(configFile).append("\n");
            }
        }
    }
    
    private void scanJavaPackages(String workingDir, StringBuilder structure) {
        Path srcPath = Paths.get(workingDir, "src", "main", "java");
        if (!Files.exists(srcPath)) {
            structure.append("  - No src/main/java directory found\n");
            return;
        }
        
        try (Stream<Path> paths = Files.walk(srcPath)) {
            Map<String, List<String>> packageClasses = paths
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".java"))
                .collect(Collectors.groupingBy(
                    p -> extractPackageName(p, srcPath),
                    Collectors.mapping(p -> extractClassName(p), Collectors.toList())
                ));
            
            packageClasses.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    structure.append("  📦 ").append(entry.getKey()).append("/\n");
                    entry.getValue().stream()
                        .sorted()
                        .forEach(className -> structure.append("    - ").append(className).append(".java\n"));
                });
                
        } catch (IOException e) {
            logger.debug("Error scanning Java packages: {}", e.getMessage());
            structure.append("  - Error scanning Java packages\n");
        }
    }
    
    private void scanTestPackages(String workingDir, StringBuilder structure) {
        Path testPath = Paths.get(workingDir, "src", "test", "java");
        if (!Files.exists(testPath)) {
            structure.append("  - No src/test/java directory found\n");
            return;
        }
        
        try (Stream<Path> paths = Files.walk(testPath)) {
            Map<String, List<String>> packageClasses = paths
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".java"))
                .collect(Collectors.groupingBy(
                    p -> extractPackageName(p, testPath),
                    Collectors.mapping(p -> extractClassName(p), Collectors.toList())
                ));
            
            packageClasses.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    structure.append("  📦 ").append(entry.getKey()).append("/\n");
                    entry.getValue().stream()
                        .sorted()
                        .forEach(className -> structure.append("    - ").append(className).append(".java (test)\n"));
                });
                
        } catch (IOException e) {
            logger.debug("Error scanning test packages: {}", e.getMessage());
            structure.append("  - Error scanning test packages\n");
        }
    }
    
    private void scanResourceFiles(String workingDir, StringBuilder structure) {
        Path resourcePath = Paths.get(workingDir, "src", "main", "resources");
        if (!Files.exists(resourcePath)) {
            structure.append("  - No src/main/resources directory found\n");
            return;
        }
        
        try (Stream<Path> paths = Files.walk(resourcePath)) {
            paths.filter(Files::isRegularFile)
                .filter(p -> !p.getFileName().toString().startsWith("."))
                .sorted()
                .forEach(p -> {
                    String relativePath = resourcePath.relativize(p).toString().replace("\\", "/");
                    structure.append("  - ").append(relativePath).append("\n");
                });
                
        } catch (IOException e) {
            logger.debug("Error scanning resource files: {}", e.getMessage());
            structure.append("  - Error scanning resource files\n");
        }
    }
    
    private String extractPackageName(Path filePath, Path srcPath) {
        Path relativePath = srcPath.relativize(filePath.getParent());
        return relativePath.toString().replace("\\", ".").replace("/", ".");
    }
    
    private String extractClassName(Path filePath) {
        String fileName = filePath.getFileName().toString();
        return fileName.substring(0, fileName.lastIndexOf(".java"));
    }
}