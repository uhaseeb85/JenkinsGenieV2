package com.example.cifixer.util;

/**
 * Enumeration of supported build tools for Spring projects.
 */
public enum BuildTool {
    MAVEN("mvn clean compile test", "pom.xml"),
    GRADLE("./gradlew clean build test", "build.gradle");
    
    private final String testCommand;
    private final String buildFile;
    
    BuildTool(String testCommand, String buildFile) {
        this.testCommand = testCommand;
        this.buildFile = buildFile;
    }
    
    public String getTestCommand() {
        return testCommand;
    }
    
    public String getBuildFile() {
        return buildFile;
    }
}