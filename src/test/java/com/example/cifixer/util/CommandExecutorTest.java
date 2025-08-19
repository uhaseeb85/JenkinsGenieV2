package com.example.cifixer.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CommandExecutorTest {
    
    private CommandExecutor commandExecutor;
    
    @TempDir
    Path tempDir;
    
    @BeforeEach
    void setUp() {
        commandExecutor = new CommandExecutor();
    }
    
    @Test
    void shouldExecuteSimpleCommandSuccessfully() {
        // Given
        File workingDir = tempDir.toFile();
        String command = isWindows() ? "echo Hello World" : "echo 'Hello World'";
        
        // When
        CommandExecutor.CommandResult result = commandExecutor.execute(command, workingDir);
        
        // Then
        assertThat(result.isSuccessful()).isTrue();
        assertThat(result.getExitCode()).isEqualTo(0);
        assertThat(result.getStdout()).contains("Hello World");
        assertThat(result.isTimedOut()).isFalse();
    }
    
    @Test
    void shouldHandleCommandFailure() {
        // Given
        File workingDir = tempDir.toFile();
        String command = isWindows() ? "nonexistentcommand" : "nonexistentcommand";
        
        // When
        CommandExecutor.CommandResult result = commandExecutor.execute(command, workingDir);
        
        // Then
        assertThat(result.isSuccessful()).isFalse();
        assertThat(result.getExitCode()).isNotEqualTo(0);
        assertThat(result.isTimedOut()).isFalse();
    }
    
    @Test
    void shouldCaptureStderrOutput() {
        // Given
        File workingDir = tempDir.toFile();
        String command = isWindows() ? 
            "echo Error message 1>&2" : 
            "echo 'Error message' >&2";
        
        // When
        CommandExecutor.CommandResult result = commandExecutor.execute(command, workingDir);
        
        // Then
        assertThat(result.getStderr()).contains("Error message");
    }
    
    @Test
    void shouldExecuteMavenCommand() throws IOException {
        // Given
        File workingDir = createMavenProject();
        
        // When
        CommandExecutor.CommandResult result = commandExecutor.executeMaven("--version", workingDir);
        
        // Then - Command should execute (may fail if Maven not installed, but should not throw)
        assertThat(result).isNotNull();
        assertThat(result.isTimedOut()).isFalse();
    }
    
    @Test
    void shouldExecuteGradleCommand() throws IOException {
        // Given
        File workingDir = createGradleProject();
        
        // When
        CommandExecutor.CommandResult result = commandExecutor.executeGradle("--version", workingDir);
        
        // Then - Command should execute (may fail if Gradle not installed, but should not throw)
        assertThat(result).isNotNull();
        assertThat(result.isTimedOut()).isFalse();
    }
    
    @Test
    void shouldHandleTimeout() {
        // Given
        File workingDir = tempDir.toFile();
        String command = isWindows() ? 
            "timeout /t 5 /nobreak" : 
            "sleep 5";
        
        // When - Use very short timeout
        CommandExecutor.CommandResult result = commandExecutor.execute(command, workingDir, 0); // 0 minutes timeout
        
        // Then
        assertThat(result.isTimedOut()).isTrue();
        assertThat(result.getExitCode()).isEqualTo(-1);
    }
    
    @Test
    void shouldHandleWorkingDirectoryCorrectly() throws IOException {
        // Given
        Path subDir = tempDir.resolve("subdir");
        Files.createDirectories(subDir);
        Files.write(subDir.resolve("testfile.txt"), "test content".getBytes());
        
        String command = isWindows() ? "dir testfile.txt" : "ls testfile.txt";
        
        // When
        CommandExecutor.CommandResult result = commandExecutor.execute(command, subDir.toFile());
        
        // Then
        assertThat(result.isSuccessful()).isTrue();
        assertThat(result.getStdout()).contains("testfile.txt");
    }
    
    @Test
    void shouldHandleLargeOutput() {
        // Given
        File workingDir = tempDir.toFile();
        String command = isWindows() ? 
            "for /l %i in (1,1,100) do echo Line %i" : 
            "for i in {1..100}; do echo \"Line $i\"; done";
        
        // When
        CommandExecutor.CommandResult result = commandExecutor.execute(command, workingDir);
        
        // Then
        assertThat(result.getStdout()).isNotEmpty();
        assertThat(result.getStdout().split("\n")).hasSizeGreaterThan(50);
    }
    
    private File createMavenProject() throws IOException {
        Path projectDir = tempDir.resolve("maven-project");
        Files.createDirectories(projectDir);
        
        String pomContent = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<project xmlns=\"http://maven.apache.org/POM/4.0.0\">\n" +
            "    <modelVersion>4.0.0</modelVersion>\n" +
            "    <groupId>com.example</groupId>\n" +
            "    <artifactId>test</artifactId>\n" +
            "    <version>1.0.0</version>\n" +
            "</project>";
        Files.write(projectDir.resolve("pom.xml"), pomContent.getBytes());
        
        return projectDir.toFile();
    }
    
    private File createGradleProject() throws IOException {
        Path projectDir = tempDir.resolve("gradle-project");
        Files.createDirectories(projectDir);
        
        String buildGradleContent = "plugins {\n" +
            "    id 'java'\n" +
            "}\n\n" +
            "group = 'com.example'\n" +
            "version = '1.0.0'";
        Files.write(projectDir.resolve("build.gradle"), buildGradleContent.getBytes());
        
        // Create a simple gradlew script
        String gradlewContent = isWindows() ? 
            "@echo off\necho Gradle wrapper" : 
            "#!/bin/bash\necho 'Gradle wrapper'";
        Path gradlewPath = projectDir.resolve(isWindows() ? "gradlew.bat" : "gradlew");
        Files.write(gradlewPath, gradlewContent.getBytes());
        
        if (!isWindows()) {
            gradlewPath.toFile().setExecutable(true);
        }
        
        return projectDir.toFile();
    }
    
    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("windows");
    }
}