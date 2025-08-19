package com.example.cifixer.store;

/**
 * Types of validation that can be performed on a Spring Boot project.
 */
public enum ValidationType {
    /**
     * Compilation validation using Maven or Gradle
     */
    COMPILE,
    
    /**
     * Test execution validation using Maven or Gradle
     */
    TEST,
    
    /**
     * Full build validation including compilation and tests
     */
    BUILD
}