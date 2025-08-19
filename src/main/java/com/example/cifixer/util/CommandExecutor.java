package com.example.cifixer.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

/**
 * Utility class for executing shell commands with proper output capture and timeout handling.
 */
@Component
public class CommandExecutor {
    
    private static final Logger logger = LoggerFactory.getLogger(CommandExecutor.class);
    private static final int DEFAULT_TIMEOUT_MINUTES = 10;
    
    /**
     * Result of command execution.
     */
    public static class CommandResult {
        private final int exitCode;
        private final String stdout;
        private final String stderr;
        private final boolean timedOut;
        
        public CommandResult(int exitCode, String stdout, String stderr, boolean timedOut) {
            this.exitCode = exitCode;
            this.stdout = stdout;
            this.stderr = stderr;
            this.timedOut = timedOut;
        }
        
        public int getExitCode() {
            return exitCode;
        }
        
        public String getStdout() {
            return stdout;
        }
        
        public String getStderr() {
            return stderr;
        }
        
        public boolean isTimedOut() {
            return timedOut;
        }
        
        public boolean isSuccessful() {
            return exitCode == 0 && !timedOut;
        }
        
        @Override
        public String toString() {
            return "CommandResult{" +
                    "exitCode=" + exitCode +
                    ", timedOut=" + timedOut +
                    ", stdoutLength=" + (stdout != null ? stdout.length() : 0) +
                    ", stderrLength=" + (stderr != null ? stderr.length() : 0) +
                    '}';
        }
    }
    
    /**
     * Execute a command in the specified working directory with default timeout.
     */
    public CommandResult execute(String command, File workingDirectory) {
        return execute(command, workingDirectory, DEFAULT_TIMEOUT_MINUTES);
    }
    
    /**
     * Execute a command in the specified working directory with custom timeout.
     */
    public CommandResult execute(String command, File workingDirectory, int timeoutMinutes) {
        logger.info("Executing command: {} in directory: {}", command, workingDirectory.getAbsolutePath());
        
        ProcessBuilder processBuilder = new ProcessBuilder();
        
        // Handle different operating systems
        if (System.getProperty("os.name").toLowerCase().contains("windows")) {
            processBuilder.command("cmd", "/c", command);
        } else {
            processBuilder.command("bash", "-c", command);
        }
        
        processBuilder.directory(workingDirectory);
        processBuilder.redirectErrorStream(false);
        
        try {
            Process process = processBuilder.start();
            
            // Capture stdout and stderr
            StringBuilder stdout = new StringBuilder();
            StringBuilder stderr = new StringBuilder();
            
            Thread stdoutThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        stdout.append(line).append("\n");
                    }
                } catch (IOException e) {
                    logger.warn("Error reading stdout: {}", e.getMessage());
                }
            });
            
            Thread stderrThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        stderr.append(line).append("\n");
                    }
                } catch (IOException e) {
                    logger.warn("Error reading stderr: {}", e.getMessage());
                }
            });
            
            stdoutThread.start();
            stderrThread.start();
            
            // Wait for process completion with timeout
            boolean finished = process.waitFor(timeoutMinutes, TimeUnit.MINUTES);
            
            if (!finished) {
                logger.warn("Command timed out after {} minutes, destroying process", timeoutMinutes);
                process.destroyForcibly();
                return new CommandResult(-1, stdout.toString(), stderr.toString(), true);
            }
            
            // Wait for output threads to complete
            stdoutThread.join(5000);
            stderrThread.join(5000);
            
            int exitCode = process.exitValue();
            String stdoutStr = stdout.toString();
            String stderrStr = stderr.toString();
            
            logger.info("Command completed with exit code: {}", exitCode);
            if (logger.isDebugEnabled()) {
                logger.debug("Stdout length: {}, Stderr length: {}", stdoutStr.length(), stderrStr.length());
            }
            
            return new CommandResult(exitCode, stdoutStr, stderrStr, false);
            
        } catch (IOException e) {
            logger.error("Failed to execute command: {}", e.getMessage());
            return new CommandResult(-1, "", "Failed to execute command: " + e.getMessage(), false);
        } catch (InterruptedException e) {
            logger.error("Command execution interrupted: {}", e.getMessage());
            Thread.currentThread().interrupt();
            return new CommandResult(-1, "", "Command execution interrupted: " + e.getMessage(), false);
        }
    }
    
    /**
     * Execute a Maven command in the specified directory.
     */
    public CommandResult executeMaven(String mavenArgs, File workingDirectory) {
        String command = "mvn " + mavenArgs;
        return execute(command, workingDirectory);
    }
    
    /**
     * Execute a Gradle command in the specified directory.
     */
    public CommandResult executeGradle(String gradleArgs, File workingDirectory) {
        String command = "./gradlew " + gradleArgs;
        return execute(command, workingDirectory);
    }
}