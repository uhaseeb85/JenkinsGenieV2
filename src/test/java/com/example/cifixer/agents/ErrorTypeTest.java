package com.example.cifixer.agents;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorTypeTest {

    @Test
    void shouldHaveAllExpectedErrorTypes() {
        // Verify all expected error types exist
        ErrorType[] errorTypes = ErrorType.values();
        
        assertThat(errorTypes).contains(
            ErrorType.COMPILATION_ERROR,
            ErrorType.MISSING_DEPENDENCY,
            ErrorType.SPRING_CONTEXT_ERROR,
            ErrorType.TEST_FAILURE,
            ErrorType.DEPENDENCY_RESOLUTION_ERROR,
            ErrorType.STACK_TRACE_ERROR,
            ErrorType.SPRING_ANNOTATION_ERROR,
            ErrorType.BUILD_CONFIGURATION_ERROR,
            ErrorType.UNKNOWN_ERROR
        );
    }

    @Test
    void shouldHaveCorrectStringRepresentation() {
        assertThat(ErrorType.COMPILATION_ERROR.toString()).isEqualTo("COMPILATION_ERROR");
        assertThat(ErrorType.MISSING_DEPENDENCY.toString()).isEqualTo("MISSING_DEPENDENCY");
        assertThat(ErrorType.SPRING_CONTEXT_ERROR.toString()).isEqualTo("SPRING_CONTEXT_ERROR");
        assertThat(ErrorType.TEST_FAILURE.toString()).isEqualTo("TEST_FAILURE");
        assertThat(ErrorType.DEPENDENCY_RESOLUTION_ERROR.toString()).isEqualTo("DEPENDENCY_RESOLUTION_ERROR");
        assertThat(ErrorType.STACK_TRACE_ERROR.toString()).isEqualTo("STACK_TRACE_ERROR");
        assertThat(ErrorType.SPRING_ANNOTATION_ERROR.toString()).isEqualTo("SPRING_ANNOTATION_ERROR");
        assertThat(ErrorType.BUILD_CONFIGURATION_ERROR.toString()).isEqualTo("BUILD_CONFIGURATION_ERROR");
        assertThat(ErrorType.UNKNOWN_ERROR.toString()).isEqualTo("UNKNOWN_ERROR");
    }

    @Test
    void shouldBeComparable() {
        // Test enum ordering
        assertThat(ErrorType.COMPILATION_ERROR.ordinal()).isLessThan(ErrorType.UNKNOWN_ERROR.ordinal());
    }

    @Test
    void shouldSupportValueOf() {
        assertThat(ErrorType.valueOf("COMPILATION_ERROR")).isEqualTo(ErrorType.COMPILATION_ERROR);
        assertThat(ErrorType.valueOf("SPRING_CONTEXT_ERROR")).isEqualTo(ErrorType.SPRING_CONTEXT_ERROR);
        assertThat(ErrorType.valueOf("UNKNOWN_ERROR")).isEqualTo(ErrorType.UNKNOWN_ERROR);
    }
}