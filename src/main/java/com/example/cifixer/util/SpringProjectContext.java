package com.example.cifixer.util;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Context information about a Spring Boot project including version, build tool,
 * modules, annotations, and dependencies.
 */
public class SpringProjectContext {
    private String springBootVersion;
    private BuildTool buildTool;
    private List<String> mavenModules;
    private Set<String> springAnnotations;
    private Map<String, String> dependencies;
    private List<String> activeProfiles;
    
    public SpringProjectContext() {
    }
    
    public SpringProjectContext(String springBootVersion, BuildTool buildTool, 
                               List<String> mavenModules, Set<String> springAnnotations,
                               Map<String, String> dependencies, List<String> activeProfiles) {
        this.springBootVersion = springBootVersion;
        this.buildTool = buildTool;
        this.mavenModules = mavenModules;
        this.springAnnotations = springAnnotations;
        this.dependencies = dependencies;
        this.activeProfiles = activeProfiles;
    }
    
    public String getSpringBootVersion() {
        return springBootVersion;
    }
    
    public void setSpringBootVersion(String springBootVersion) {
        this.springBootVersion = springBootVersion;
    }
    
    public BuildTool getBuildTool() {
        return buildTool;
    }
    
    public void setBuildTool(BuildTool buildTool) {
        this.buildTool = buildTool;
    }
    
    public List<String> getMavenModules() {
        return mavenModules;
    }
    
    public void setMavenModules(List<String> mavenModules) {
        this.mavenModules = mavenModules;
    }
    
    public Set<String> getSpringAnnotations() {
        return springAnnotations;
    }
    
    public void setSpringAnnotations(Set<String> springAnnotations) {
        this.springAnnotations = springAnnotations;
    }
    
    public Map<String, String> getDependencies() {
        return dependencies;
    }
    
    public void setDependencies(Map<String, String> dependencies) {
        this.dependencies = dependencies;
    }
    
    public List<String> getActiveProfiles() {
        return activeProfiles;
    }
    
    public void setActiveProfiles(List<String> activeProfiles) {
        this.activeProfiles = activeProfiles;
    }
    
    /**
     * Get relevant Spring annotations as a formatted string for LLM context.
     */
    public String getRelevantAnnotations() {
        if (springAnnotations == null || springAnnotations.isEmpty()) {
            return "None found";
        }
        return String.join(", ", springAnnotations);
    }
    
    /**
     * Get dependency information as a formatted string for LLM context.
     */
    public String getDependencyInfo() {
        if (dependencies == null || dependencies.isEmpty()) {
            return "No dependencies parsed";
        }
        
        StringBuilder sb = new StringBuilder();
        dependencies.entrySet().stream()
            .limit(10) // Limit to avoid overwhelming LLM context
            .forEach(entry -> sb.append(entry.getKey()).append(":").append(entry.getValue()).append(", "));
        
        if (sb.length() > 2) {
            sb.setLength(sb.length() - 2); // Remove trailing comma
        }
        
        return sb.toString();
    }
    
    @Override
    public String toString() {
        return "SpringProjectContext{" +
                "springBootVersion='" + springBootVersion + '\'' +
                ", buildTool=" + buildTool +
                ", mavenModules=" + mavenModules +
                ", springAnnotations=" + springAnnotations +
                ", dependencies=" + dependencies +
                ", activeProfiles=" + activeProfiles +
                '}';
    }
}