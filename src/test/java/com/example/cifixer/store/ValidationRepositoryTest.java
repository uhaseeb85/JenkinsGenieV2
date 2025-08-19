package com.example.cifixer.store;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class ValidationRepositoryTest {
    
    @Autowired
    private ValidationRepository validationRepository;
    
    @Autowired
    private BuildRepository buildRepository;
    
    private Build testBuild;
    
    @BeforeEach
    void setUp() {
        testBuild = new Build("test-job", 123, "main", "https://github.com/test/repo", "abc123");
        testBuild = buildRepository.save(testBuild);
    }
    
    @Test
    void shouldSaveAndRetrieveValidation() {
        // Given
        Validation validation = new Validation(testBuild, ValidationType.COMPILE, 0, "BUILD SUCCESS", "");
        
        // When
        Validation saved = validationRepository.save(validation);
        
        // Then
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getBuild().getId()).isEqualTo(testBuild.getId());
        assertThat(saved.getValidationType()).isEqualTo(ValidationType.COMPILE);
        assertThat(saved.getExitCode()).isEqualTo(0);
        assertThat(saved.isSuccessful()).isTrue();
    }
    
    @Test
    void shouldFindValidationsByBuildIdOrderedByCreatedAt() {
        // Given
        Validation compile = new Validation(testBuild, ValidationType.COMPILE, 0, "COMPILE SUCCESS", "");
        Validation test = new Validation(testBuild, ValidationType.TEST, 1, "TEST FAILED", "Error");
        
        validationRepository.save(compile);
        try {
            Thread.sleep(10); // Ensure different timestamps
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        validationRepository.save(test);
        
        // When
        List<Validation> validations = validationRepository.findByBuildIdOrderByCreatedAtDesc(testBuild.getId());
        
        // Then
        assertThat(validations).hasSize(2);
        assertThat(validations.get(0).getValidationType()).isEqualTo(ValidationType.TEST); // Most recent first
        assertThat(validations.get(1).getValidationType()).isEqualTo(ValidationType.COMPILE);
    }
    
    @Test
    void shouldFindLatestValidationByType() {
        // Given
        Validation compile1 = new Validation(testBuild, ValidationType.COMPILE, 1, "FAILED", "Error");
        Validation compile2 = new Validation(testBuild, ValidationType.COMPILE, 0, "SUCCESS", "");
        
        validationRepository.save(compile1);
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        validationRepository.save(compile2);
        
        // When
        Optional<Validation> latest = validationRepository.findLatestByBuildIdAndType(testBuild.getId(), ValidationType.COMPILE);
        
        // Then
        assertThat(latest).isPresent();
        assertThat(latest.get().getExitCode()).isEqualTo(0); // Should be the latest (successful) one
        assertThat(latest.get().getStdout()).isEqualTo("SUCCESS");
    }
    
    @Test
    void shouldFindSuccessfulValidations() {
        // Given
        Validation successCompile = new Validation(testBuild, ValidationType.COMPILE, 0, "SUCCESS", "");
        Validation failedTest = new Validation(testBuild, ValidationType.TEST, 1, "FAILED", "Error");
        Validation successTest = new Validation(testBuild, ValidationType.TEST, 0, "SUCCESS", "");
        
        validationRepository.save(successCompile);
        validationRepository.save(failedTest);
        validationRepository.save(successTest);
        
        // When
        List<Validation> successful = validationRepository.findSuccessfulByBuildId(testBuild.getId());
        
        // Then
        assertThat(successful).hasSize(2);
        assertThat(successful).allMatch(Validation::isSuccessful);
        assertThat(successful).extracting(Validation::getValidationType)
                .containsExactlyInAnyOrder(ValidationType.COMPILE, ValidationType.TEST);
    }
    
    @Test
    void shouldCheckIfHasSuccessfulValidation() {
        // Given - No validations initially
        assertThat(validationRepository.hasSuccessfulValidation(testBuild.getId())).isFalse();
        
        // When - Add failed validation
        Validation failed = new Validation(testBuild, ValidationType.COMPILE, 1, "FAILED", "Error");
        validationRepository.save(failed);
        
        // Then - Still no successful validation
        assertThat(validationRepository.hasSuccessfulValidation(testBuild.getId())).isFalse();
        
        // When - Add successful validation
        Validation success = new Validation(testBuild, ValidationType.TEST, 0, "SUCCESS", "");
        validationRepository.save(success);
        
        // Then - Now has successful validation
        assertThat(validationRepository.hasSuccessfulValidation(testBuild.getId())).isTrue();
    }
    
    @Test
    void shouldCountValidationsByBuildAndType() {
        // Given
        Validation compile1 = new Validation(testBuild, ValidationType.COMPILE, 0, "SUCCESS", "");
        Validation compile2 = new Validation(testBuild, ValidationType.COMPILE, 1, "FAILED", "Error");
        Validation test1 = new Validation(testBuild, ValidationType.TEST, 0, "SUCCESS", "");
        
        validationRepository.save(compile1);
        validationRepository.save(compile2);
        validationRepository.save(test1);
        
        // When & Then
        assertThat(validationRepository.countByBuildIdAndValidationType(testBuild.getId(), ValidationType.COMPILE)).isEqualTo(2);
        assertThat(validationRepository.countByBuildIdAndValidationType(testBuild.getId(), ValidationType.TEST)).isEqualTo(1);
        assertThat(validationRepository.countByBuildIdAndValidationType(testBuild.getId(), ValidationType.BUILD)).isEqualTo(0);
    }
    
    @Test
    void shouldHandleEmptyResults() {
        // Given - Different build
        Build otherBuild = new Build("other-job", 456, "develop", "https://github.com/other/repo", "def456");
        otherBuild = buildRepository.save(otherBuild);
        
        // When & Then
        assertThat(validationRepository.findByBuildIdOrderByCreatedAtDesc(otherBuild.getId())).isEmpty();
        assertThat(validationRepository.findLatestByBuildIdAndType(otherBuild.getId(), ValidationType.COMPILE)).isEmpty();
        assertThat(validationRepository.findSuccessfulByBuildId(otherBuild.getId())).isEmpty();
        assertThat(validationRepository.hasSuccessfulValidation(otherBuild.getId())).isFalse();
        assertThat(validationRepository.countByBuildIdAndValidationType(otherBuild.getId(), ValidationType.COMPILE)).isEqualTo(0);
    }
    
    @Test
    void shouldHandleValidationWithNullOutputs() {
        // Given
        Validation validation = new Validation(testBuild, ValidationType.COMPILE, 0);
        validation.setStdout(null);
        validation.setStderr(null);
        
        // When
        Validation saved = validationRepository.save(validation);
        
        // Then
        assertThat(saved.getStdout()).isNull();
        assertThat(saved.getStderr()).isNull();
        assertThat(saved.isSuccessful()).isTrue(); // Exit code 0 means successful
    }
}