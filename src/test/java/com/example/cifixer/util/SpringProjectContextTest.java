package com.example.cifixer.util;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

class SpringProjectContextTest {
    
    @Test
    void shouldCreateEmptyContext() {
        // When
        SpringProjectContext context = new SpringProjectContext();
        
        // Then
        assertThat(context.getSpringBootVersion()).isNull();
        assertThat(context.getBuildTool()).isNull();
        assertThat(context.getMavenModules()).isNull();
        assertThat(context.getSpringAnnotations()).isNull();
        assertThat(context.getDependencies()).isNull();
        assertThat(context.getActiveProfiles()).isNull();
    }
    
    @Test
    void shouldCreateContextWithAllFields() {
        // Given
        String version = "2.7.8";
        BuildTool buildTool = BuildTool.MAVEN;
        List<String> modules = Arrays.asList("core", "web");
        Set<String> annotations = Set.of("@RestController", "@Service");
        Map<String, String> dependencies = Map.of("spring-boot:starter-web", "2.7.8");
        List<String> profiles = Arrays.asList("dev", "test");
        
        // When
        SpringProjectContext context = new SpringProjectContext(
            version, buildTool, modules, annotations, dependencies, profiles);
        
        // Then
        assertThat(context.getSpringBootVersion()).isEqualTo(version);
        assertThat(context.getBuildTool()).isEqualTo(buildTool);
        assertThat(context.getMavenModules()).isEqualTo(modules);
        assertThat(context.getSpringAnnotations()).isEqualTo(annotations);
        assertThat(context.getDependencies()).isEqualTo(dependencies);
        assertThat(context.getActiveProfiles()).isEqualTo(profiles);
    }
    
    @Test
    void shouldFormatRelevantAnnotationsWhenPresent() {
        // Given
        SpringProjectContext context = new SpringProjectContext();
        context.setSpringAnnotations(Set.of("@RestController", "@Service", "@Repository"));
        
        // When
        String formatted = context.getRelevantAnnotations();
        
        // Then
        assertThat(formatted).contains("@RestController", "@Service", "@Repository");
        assertThat(formatted.split(", ")).hasSize(3);
    }
    
    @Test
    void shouldReturnNoneFoundWhenNoAnnotations() {
        // Given
        SpringProjectContext context = new SpringProjectContext();
        context.setSpringAnnotations(Collections.emptySet());
        
        // When
        String formatted = context.getRelevantAnnotations();
        
        // Then
        assertThat(formatted).isEqualTo("None found");
    }
    
    @Test
    void shouldReturnNoneFoundWhenAnnotationsNull() {
        // Given
        SpringProjectContext context = new SpringProjectContext();
        
        // When
        String formatted = context.getRelevantAnnotations();
        
        // Then
        assertThat(formatted).isEqualTo("None found");
    }
    
    @Test
    void shouldFormatDependencyInfoWhenPresent() {
        // Given
        SpringProjectContext context = new SpringProjectContext();
        Map<String, String> dependencies = new LinkedHashMap<>();
        dependencies.put("org.springframework.boot:spring-boot-starter-web", "2.7.8");
        dependencies.put("org.springframework.boot:spring-boot-starter-data-jpa", "managed");
        context.setDependencies(dependencies);
        
        // When
        String formatted = context.getDependencyInfo();
        
        // Then
        assertThat(formatted).contains("org.springframework.boot:spring-boot-starter-web:2.7.8");
        assertThat(formatted).contains("org.springframework.boot:spring-boot-starter-data-jpa:managed");
    }
    
    @Test
    void shouldLimitDependencyInfoToTenEntries() {
        // Given
        SpringProjectContext context = new SpringProjectContext();
        Map<String, String> dependencies = new LinkedHashMap<>();
        for (int i = 1; i <= 15; i++) {
            dependencies.put("group" + i + ":artifact" + i, "1.0." + i);
        }
        context.setDependencies(dependencies);
        
        // When
        String formatted = context.getDependencyInfo();
        
        // Then
        String[] parts = formatted.split(", ");
        assertThat(parts).hasSizeLessThanOrEqualTo(10);
    }
    
    @Test
    void shouldReturnNoDependenciesWhenEmpty() {
        // Given
        SpringProjectContext context = new SpringProjectContext();
        context.setDependencies(Collections.emptyMap());
        
        // When
        String formatted = context.getDependencyInfo();
        
        // Then
        assertThat(formatted).isEqualTo("No dependencies parsed");
    }
    
    @Test
    void shouldReturnNoDependenciesWhenNull() {
        // Given
        SpringProjectContext context = new SpringProjectContext();
        
        // When
        String formatted = context.getDependencyInfo();
        
        // Then
        assertThat(formatted).isEqualTo("No dependencies parsed");
    }
    
    @Test
    void shouldProvideToStringRepresentation() {
        // Given
        SpringProjectContext context = new SpringProjectContext();
        context.setSpringBootVersion("2.7.8");
        context.setBuildTool(BuildTool.MAVEN);
        context.setMavenModules(Arrays.asList("core"));
        context.setSpringAnnotations(Set.of("@RestController"));
        context.setDependencies(Map.of("spring:web", "2.7.8"));
        context.setActiveProfiles(Arrays.asList("dev"));
        
        // When
        String toString = context.toString();
        
        // Then
        assertThat(toString).contains("springBootVersion='2.7.8'");
        assertThat(toString).contains("buildTool=MAVEN");
        assertThat(toString).contains("mavenModules=[core]");
        assertThat(toString).contains("springAnnotations=[@RestController]");
        assertThat(toString).contains("dependencies={spring:web=2.7.8}");
        assertThat(toString).contains("activeProfiles=[dev]");
    }
    
    @Test
    void shouldSetAndGetAllProperties() {
        // Given
        SpringProjectContext context = new SpringProjectContext();
        String version = "2.7.8";
        BuildTool buildTool = BuildTool.GRADLE;
        List<String> modules = Arrays.asList("api", "impl");
        Set<String> annotations = Set.of("@Component");
        Map<String, String> dependencies = Map.of("test:lib", "1.0");
        List<String> profiles = Arrays.asList("prod");
        
        // When
        context.setSpringBootVersion(version);
        context.setBuildTool(buildTool);
        context.setMavenModules(modules);
        context.setSpringAnnotations(annotations);
        context.setDependencies(dependencies);
        context.setActiveProfiles(profiles);
        
        // Then
        assertThat(context.getSpringBootVersion()).isEqualTo(version);
        assertThat(context.getBuildTool()).isEqualTo(buildTool);
        assertThat(context.getMavenModules()).isEqualTo(modules);
        assertThat(context.getSpringAnnotations()).isEqualTo(annotations);
        assertThat(context.getDependencies()).isEqualTo(dependencies);
        assertThat(context.getActiveProfiles()).isEqualTo(profiles);
    }
}