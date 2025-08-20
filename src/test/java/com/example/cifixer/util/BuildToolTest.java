package com.example.cifixer.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Simple unit tests for BuildTool enum.
 */
public class BuildToolTest {

    @Test
    public void testBuildToolValues() {
        // Test that BuildTool enum values exist
        assertNotNull(BuildTool.MAVEN);
        assertNotNull(BuildTool.GRADLE);
    }

    @Test
    public void testBuildToolFromString() {
        // Test valueOf functionality
        assertEquals(BuildTool.MAVEN, BuildTool.valueOf("MAVEN"));
        assertEquals(BuildTool.GRADLE, BuildTool.valueOf("GRADLE"));
    }

    @Test
    public void testBuildToolToString() {
        // Test toString functionality
        assertEquals("MAVEN", BuildTool.MAVEN.toString());
        assertEquals("GRADLE", BuildTool.GRADLE.toString());
    }

    @Test
    public void testBuildToolComparison() {
        // Test enum comparison
        assertEquals(BuildTool.MAVEN, BuildTool.MAVEN);
        assertNotEquals(BuildTool.MAVEN, BuildTool.GRADLE);
    }

    @Test
    public void testBuildToolEnumValues() {
        BuildTool[] values = BuildTool.values();
        
        assertNotNull(values);
        assertEquals(2, values.length); // Should have exactly 2 values
        
        // Verify both values are present
        boolean hasMaven = false;
        boolean hasGradle = false;
        
        for (BuildTool tool : values) {
            if (tool == BuildTool.MAVEN) {
                hasMaven = true;
            } else if (tool == BuildTool.GRADLE) {
                hasGradle = true;
            }
        }
        
        assertTrue(hasMaven);
        assertTrue(hasGradle);
    }

    @Test
    public void testInvalidBuildTool() {
        // Test that invalid tool throws exception
        assertThrows(IllegalArgumentException.class, () -> {
            BuildTool.valueOf("INVALID_TOOL");
        });
    }

    @Test
    public void testBuildToolOrdinal() {
        // Test ordinal values (order in enum)
        assertTrue(BuildTool.MAVEN.ordinal() >= 0);
        assertTrue(BuildTool.GRADLE.ordinal() >= 0);
        assertNotEquals(BuildTool.MAVEN.ordinal(), BuildTool.GRADLE.ordinal());
    }
}