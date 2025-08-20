package com.example.cifixer;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Basic application tests that don't require Spring context.
 */
public class ApplicationBasicTest {

    @Test
    public void testApplicationClassExists() {
        // Test that the main application class exists and can be instantiated
        assertDoesNotThrow(() -> {
            Class<?> appClass = MultiAgentCiFixerApplication.class;
            assertNotNull(appClass);
        });
    }

    @Test
    public void testMainMethodExists() {
        // Test that main method exists
        assertDoesNotThrow(() -> {
            MultiAgentCiFixerApplication.class.getMethod("main", String[].class);
        });
    }

    @Test
    public void testPackageStructure() {
        // Test that key packages exist by checking for classes
        assertDoesNotThrow(() -> {
            // Core package
            Class.forName("com.example.cifixer.core.Task");
            Class.forName("com.example.cifixer.core.TaskStatus");
            Class.forName("com.example.cifixer.core.TaskType");
            
            // Store package
            Class.forName("com.example.cifixer.store.BuildStatus");
            
            // Util package
            Class.forName("com.example.cifixer.util.BuildTool");
        });
    }

    @Test
    public void testBasicConstants() {
        // Test some basic application constants or properties
        String packageName = MultiAgentCiFixerApplication.class.getPackage().getName();
        assertEquals("com.example.cifixer", packageName);
    }

    @Test
    public void testJavaVersion() {
        // Ensure we're running on Java 8 or higher
        String javaVersion = System.getProperty("java.version");
        assertNotNull(javaVersion);
        
        // Extract major version number
        String[] versionParts = javaVersion.split("\\.");
        int majorVersion;
        if (versionParts[0].equals("1")) {
            // Java 8 format: 1.8.x
            majorVersion = Integer.parseInt(versionParts[1]);
        } else {
            // Java 9+ format: 9.x, 11.x, etc.
            majorVersion = Integer.parseInt(versionParts[0]);
        }
        
        assertTrue(majorVersion >= 8, "Java 8 or higher required, found: " + javaVersion);
    }
}