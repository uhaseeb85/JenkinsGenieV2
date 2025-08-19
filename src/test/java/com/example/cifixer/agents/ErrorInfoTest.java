package com.example.cifixer.agents;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorInfoTest {
    
    @Test
    void shouldCreateErrorInfoWithTypeAndMessage() {
        ErrorInfo error = new ErrorInfo(ErrorType.COMPILATION_ERROR, "Test error message");
        
        assertThat(error.getErrorType()).isEqualTo(ErrorType.COMPILATION_ERROR);
        assertThat(error.getErrorMessage()).isEqualTo("Test error message");
    }
    
    @Test
    void shouldCreateEmptyErrorInfo() {
        ErrorInfo error = new ErrorInfo();
        
        assertThat(error.getErrorType()).isNull();
        assertThat(error.getErrorMessage()).isNull();
        assertThat(error.getFilePath()).isNull();
        assertThat(error.getLineNumber()).isNull();
    }
    
    @Test
    void shouldSetAllProperties() {
        ErrorInfo error = new ErrorInfo();
        error.setErrorType(ErrorType.SPRING_CONTEXT_ERROR);
        error.setErrorMessage("Bean not found");
        error.setFilePath("src/main/java/User.java");
        error.setLineNumber(42);
        error.setStackTrace("at com.example.User.method");
        error.setMissingBean("UserService");
        error.setMissingDependency("UserRepository");
        error.setFailedTest("UserTest.testMethod");
        error.setMavenModule("user-service");
        
        assertThat(error.getErrorType()).isEqualTo(ErrorType.SPRING_CONTEXT_ERROR);
        assertThat(error.getErrorMessage()).isEqualTo("Bean not found");
        assertThat(error.getFilePath()).isEqualTo("src/main/java/User.java");
        assertThat(error.getLineNumber()).isEqualTo(42);
        assertThat(error.getStackTrace()).isEqualTo("at com.example.User.method");
        assertThat(error.getMissingBean()).isEqualTo("UserService");
        assertThat(error.getMissingDependency()).isEqualTo("UserRepository");
        assertThat(error.getFailedTest()).isEqualTo("UserTest.testMethod");
        assertThat(error.getMavenModule()).isEqualTo("user-service");
    }
    
    @Test
    void shouldGenerateToString() {
        ErrorInfo error = new ErrorInfo(ErrorType.MISSING_DEPENDENCY, "Cannot find symbol");
        error.setFilePath("src/main/java/User.java");
        error.setLineNumber(25);
        
        String toString = error.toString();
        
        assertThat(toString).contains("MISSING_DEPENDENCY");
        assertThat(toString).contains("src/main/java/User.java");
        assertThat(toString).contains("25");
        assertThat(toString).contains("Cannot find symbol");
    }
}