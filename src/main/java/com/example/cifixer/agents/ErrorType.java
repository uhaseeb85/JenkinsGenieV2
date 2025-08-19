package com.example.cifixer.agents;

/**
 * Enumeration of different types of build errors that can be detected in Spring projects.
 */
public enum ErrorType {
    /**
     * Java compilation error (missing symbols, syntax errors)
     */
    COMPILATION_ERROR,
    
    /**
     * Missing dependency or import
     */
    MISSING_DEPENDENCY,
    
    /**
     * Spring context failure (NoSuchBeanDefinitionException, etc.)
     */
    SPRING_CONTEXT_ERROR,
    
    /**
     * Unit or integration test failure
     */
    TEST_FAILURE,
    
    /**
     * Maven/Gradle dependency resolution failure
     */
    DEPENDENCY_RESOLUTION_ERROR,
    
    /**
     * Java stack trace with line numbers
     */
    STACK_TRACE_ERROR,
    
    /**
     * Spring annotation or configuration error
     */
    SPRING_ANNOTATION_ERROR,
    
    /**
     * Maven/Gradle build configuration error
     */
    BUILD_CONFIGURATION_ERROR,
    
    /**
     * Unknown or unrecognized error type
     */
    UNKNOWN_ERROR
}