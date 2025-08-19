package com.example.cifixer.agents;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PlanStepTest {
    
    @Test
    void shouldCreatePlanStepWithDescriptionAndAction() {
        PlanStep step = new PlanStep("Fix compilation error", "ADD_IMPORT");
        
        assertThat(step.getDescription()).isEqualTo("Fix compilation error");
        assertThat(step.getAction()).isEqualTo("ADD_IMPORT");
    }
    
    @Test
    void shouldCreateEmptyPlanStep() {
        PlanStep step = new PlanStep();
        
        assertThat(step.getDescription()).isNull();
        assertThat(step.getAction()).isNull();
        assertThat(step.getTargetFile()).isNull();
        assertThat(step.getSpringComponents()).isNull();
        assertThat(step.getDependencies()).isNull();
        assertThat(step.getReasoning()).isNull();
        assertThat(step.getPriority()).isEqualTo(0);
    }
    
    @Test
    void shouldSetAllProperties() {
        PlanStep step = new PlanStep();
        List<String> springComponents = Arrays.asList("@Service", "@Repository");
        List<String> dependencies = Arrays.asList("spring-boot-starter-web", "spring-boot-starter-data-jpa");
        
        step.setDescription("Add Spring annotation");
        step.setAction("ADD_SPRING_ANNOTATION");
        step.setTargetFile("src/main/java/UserService.java");
        step.setSpringComponents(springComponents);
        step.setDependencies(dependencies);
        step.setReasoning("Missing @Service annotation");
        step.setPriority(1);
        
        assertThat(step.getDescription()).isEqualTo("Add Spring annotation");
        assertThat(step.getAction()).isEqualTo("ADD_SPRING_ANNOTATION");
        assertThat(step.getTargetFile()).isEqualTo("src/main/java/UserService.java");
        assertThat(step.getSpringComponents()).isEqualTo(springComponents);
        assertThat(step.getDependencies()).isEqualTo(dependencies);
        assertThat(step.getReasoning()).isEqualTo("Missing @Service annotation");
        assertThat(step.getPriority()).isEqualTo(1);
    }
    
    @Test
    void shouldGenerateToString() {
        PlanStep step = new PlanStep("Fix error", "ADD_IMPORT");
        step.setTargetFile("User.java");
        step.setPriority(2);
        
        String toString = step.toString();
        
        assertThat(toString).contains("Fix error");
        assertThat(toString).contains("ADD_IMPORT");
        assertThat(toString).contains("User.java");
        assertThat(toString).contains("2");
    }
}