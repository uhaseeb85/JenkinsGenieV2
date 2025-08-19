package com.example.cifixer.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class BuildToolTest {
    
    @Test
    void shouldHaveMavenWithCorrectProperties() {
        // When
        BuildTool maven = BuildTool.MAVEN;
        
        // Then
        assertThat(maven.getTestCommand()).isEqualTo("mvn clean compile test");
        assertThat(maven.getBuildFile()).isEqualTo("pom.xml");
    }
    
    @Test
    void shouldHaveGradleWithCorrectProperties() {
        // When
        BuildTool gradle = BuildTool.GRADLE;
        
        // Then
        assertThat(gradle.getTestCommand()).isEqualTo("./gradlew clean build test");
        assertThat(gradle.getBuildFile()).isEqualTo("build.gradle");
    }
    
    @Test
    void shouldHaveCorrectEnumValues() {
        // When
        BuildTool[] values = BuildTool.values();
        
        // Then
        assertThat(values).hasSize(2);
        assertThat(values).containsExactly(BuildTool.MAVEN, BuildTool.GRADLE);
    }
    
    @Test
    void shouldSupportValueOfMethod() {
        // When & Then
        assertThat(BuildTool.valueOf("MAVEN")).isEqualTo(BuildTool.MAVEN);
        assertThat(BuildTool.valueOf("GRADLE")).isEqualTo(BuildTool.GRADLE);
    }
}