package com.example.cifixer.store;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Simple unit tests for BuildStatus enum.
 */
public class BuildStatusSimpleTest {

    @Test
    public void testBuildStatusValues() {
        // Test that all expected BuildStatus values exist
        assertNotNull(BuildStatus.RECEIVED);
        assertNotNull(BuildStatus.PROCESSING);
        assertNotNull(BuildStatus.COMPLETED);
        assertNotNull(BuildStatus.FAILED);
        assertNotNull(BuildStatus.MANUAL_INTERVENTION_REQUIRED);
    }

    @Test
    public void testBuildStatusFromString() {
        // Test valueOf functionality
        assertEquals(BuildStatus.RECEIVED, BuildStatus.valueOf("RECEIVED"));
        assertEquals(BuildStatus.PROCESSING, BuildStatus.valueOf("PROCESSING"));
        assertEquals(BuildStatus.COMPLETED, BuildStatus.valueOf("COMPLETED"));
        assertEquals(BuildStatus.FAILED, BuildStatus.valueOf("FAILED"));
        assertEquals(BuildStatus.MANUAL_INTERVENTION_REQUIRED, 
                    BuildStatus.valueOf("MANUAL_INTERVENTION_REQUIRED"));
    }

    @Test
    public void testBuildStatusToString() {
        // Test toString functionality
        assertEquals("RECEIVED", BuildStatus.RECEIVED.toString());
        assertEquals("PROCESSING", BuildStatus.PROCESSING.toString());
        assertEquals("COMPLETED", BuildStatus.COMPLETED.toString());
        assertEquals("FAILED", BuildStatus.FAILED.toString());
        assertEquals("MANUAL_INTERVENTION_REQUIRED", 
                    BuildStatus.MANUAL_INTERVENTION_REQUIRED.toString());
    }

    @Test
    public void testBuildStatusComparison() {
        // Test enum comparison
        assertEquals(BuildStatus.RECEIVED, BuildStatus.RECEIVED);
        assertNotEquals(BuildStatus.RECEIVED, BuildStatus.PROCESSING);
        assertNotEquals(BuildStatus.COMPLETED, BuildStatus.FAILED);
    }

    @Test
    public void testBuildStatusEnumValues() {
        BuildStatus[] values = BuildStatus.values();
        
        assertNotNull(values);
        assertTrue(values.length >= 5); // At least 5 status values
        
        // Verify specific values are present
        boolean hasReceived = false;
        boolean hasProcessing = false;
        boolean hasCompleted = false;
        boolean hasFailed = false;
        boolean hasManualIntervention = false;
        
        for (BuildStatus status : values) {
            switch (status) {
                case RECEIVED:
                    hasReceived = true;
                    break;
                case PROCESSING:
                    hasProcessing = true;
                    break;
                case COMPLETED:
                    hasCompleted = true;
                    break;
                case FAILED:
                    hasFailed = true;
                    break;
                case MANUAL_INTERVENTION_REQUIRED:
                    hasManualIntervention = true;
                    break;
            }
        }
        
        assertTrue(hasReceived);
        assertTrue(hasProcessing);
        assertTrue(hasCompleted);
        assertTrue(hasFailed);
        assertTrue(hasManualIntervention);
    }

    @Test
    public void testInvalidBuildStatus() {
        // Test that invalid status throws exception
        assertThrows(IllegalArgumentException.class, () -> {
            BuildStatus.valueOf("INVALID_STATUS");
        });
    }
}