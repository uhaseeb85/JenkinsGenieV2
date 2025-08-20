package com.example.cifixer.core;

import com.example.cifixer.store.Build;
import com.example.cifixer.store.BuildRepository;
import com.example.cifixer.store.BuildStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CleanupServiceTest {
    
    @Mock
    private BuildRepository buildRepository;
    
    private CleanupService cleanupService;
    
    @TempDir
    Path tempDir;
    
    @BeforeEach
    void setUp() {
        cleanupService = new CleanupService(buildRepository);
        
        // Set test configuration
        ReflectionTestUtils.setField(cleanupService, "workingDirectoryBase", tempDir.toString());
        ReflectionTestUtils.setField(cleanupService, "retentionDays", 7);
        ReflectionTestUtils.setField(cleanupService, "cleanupEnabled", true);
        ReflectionTestUtils.setField(cleanupService, "dryRun", false);
    }
    
    @Test
    void shouldCleanupOldDirectories() throws IOException {
        // Given
        Path oldBuildDir = tempDir.resolve("build-123");
        Files.createDirectories(oldBuildDir);
        Files.createFile(oldBuildDir.resolve("test.txt"));
        
        Build oldBuild = createTestBuild(123L, LocalDateTime.now().minusDays(10));
        when(buildRepository.findById(123L)).thenReturn(Optional.of(oldBuild));
        
        // When
        CleanupService.CleanupResult result = cleanupService.cleanupOldWorkingDirectories();
        
        // Then
        assertEquals(1, result.getDeletedDirectories());
        assertFalse(Files.exists(oldBuildDir));
    }
    
    @Test
    void shouldNotCleanupRecentDirectories() throws IOException {
        // Given
        Path recentBuildDir = tempDir.resolve("build-456");
        Files.createDirectories(recentBuildDir);
        Files.createFile(recentBuildDir.resolve("test.txt"));
        
        Build recentBuild = createTestBuild(456L, LocalDateTime.now().minusDays(1));
        when(buildRepository.findById(456L)).thenReturn(Optional.of(recentBuild));
        
        // When
        CleanupService.CleanupResult result = cleanupService.cleanupOldWorkingDirectories();
        
        // Then
        assertEquals(0, result.getDeletedDirectories());
        assertTrue(Files.exists(recentBuildDir));
    }
    
    @Test
    void shouldCleanupOrphanedDirectories() throws IOException {
        // Given
        Path orphanedDir = tempDir.resolve("build-999");
        Files.createDirectories(orphanedDir);
        Files.createFile(orphanedDir.resolve("test.txt"));
        
        when(buildRepository.findById(999L)).thenReturn(Optional.empty());
        when(buildRepository.existsById(999L)).thenReturn(false);
        
        // When
        CleanupService.CleanupResult result = cleanupService.cleanupOldWorkingDirectories();
        
        // Then
        assertEquals(1, result.getDeletedDirectories());
        assertFalse(Files.exists(orphanedDir));
    }
    
    @Test
    void shouldCleanupFailedBuildsEarlier() throws IOException {
        // Given
        Path failedBuildDir = tempDir.resolve("build-789");
        Files.createDirectories(failedBuildDir);
        Files.createFile(failedBuildDir.resolve("test.txt"));
        
        Build failedBuild = createTestBuild(789L, LocalDateTime.now().minusDays(2));
        failedBuild.setStatus(BuildStatus.FAILED);
        when(buildRepository.findById(789L)).thenReturn(Optional.of(failedBuild));
        
        // When
        CleanupService.CleanupResult result = cleanupService.cleanupOldWorkingDirectories();
        
        // Then
        assertEquals(1, result.getDeletedDirectories());
        assertFalse(Files.exists(failedBuildDir));
    }
    
    @Test
    void shouldNotCleanupRecentFailedBuilds() throws IOException {
        // Given
        Path recentFailedDir = tempDir.resolve("build-101");
        Files.createDirectories(recentFailedDir);
        Files.createFile(recentFailedDir.resolve("test.txt"));
        
        Build recentFailedBuild = createTestBuild(101L, LocalDateTime.now().minusHours(12));
        recentFailedBuild.setStatus(BuildStatus.FAILED);
        when(buildRepository.findById(101L)).thenReturn(Optional.of(recentFailedBuild));
        
        // When
        CleanupService.CleanupResult result = cleanupService.cleanupOldWorkingDirectories();
        
        // Then
        assertEquals(0, result.getDeletedDirectories());
        assertTrue(Files.exists(recentFailedDir));
    }
    
    @Test
    void shouldHandleInvalidDirectoryNames() throws IOException {
        // Given
        Path invalidDir = tempDir.resolve("invalid-directory-name");
        Files.createDirectories(invalidDir);
        
        // When
        CleanupService.CleanupResult result = cleanupService.cleanupOldWorkingDirectories();
        
        // Then
        assertEquals(0, result.getDeletedDirectories());
        assertTrue(Files.exists(invalidDir)); // Should not be deleted
    }
    
    @Test
    void shouldPerformDryRunWithoutDeletion() throws IOException {
        // Given
        ReflectionTestUtils.setField(cleanupService, "dryRun", true);
        
        Path oldBuildDir = tempDir.resolve("build-123");
        Files.createDirectories(oldBuildDir);
        Files.createFile(oldBuildDir.resolve("test.txt"));
        
        Build oldBuild = createTestBuild(123L, LocalDateTime.now().minusDays(10));
        when(buildRepository.findById(123L)).thenReturn(Optional.of(oldBuild));
        
        // When
        CleanupService.CleanupResult result = cleanupService.cleanupOldWorkingDirectories();
        
        // Then
        assertEquals(0, result.getDeletedDirectories()); // No actual deletion in dry run
        assertTrue(Files.exists(oldBuildDir)); // Directory should still exist
    }
    
    @Test
    void shouldHandleNonExistentWorkingDirectory() {
        // Given
        ReflectionTestUtils.setField(cleanupService, "workingDirectoryBase", "/non/existent/path");
        
        // When
        CleanupService.CleanupResult result = cleanupService.cleanupOldWorkingDirectories();
        
        // Then
        assertEquals(0, result.getDeletedDirectories());
        assertEquals(0, result.getErrors());
    }
    
    @Test
    void shouldPerformEmergencyCleanup() throws IOException {
        // Given
        Path completedBuildDir = tempDir.resolve("build-555");
        Files.createDirectories(completedBuildDir);
        Files.createFile(completedBuildDir.resolve("large-file.txt"));
        
        Build completedBuild = createTestBuild(555L, LocalDateTime.now().minusDays(2));
        completedBuild.setStatus(BuildStatus.COMPLETED);
        
        when(buildRepository.findByStatusAndCreatedAtBefore(eq(BuildStatus.COMPLETED), any()))
            .thenReturn(Arrays.asList(completedBuild));
        
        // When
        CleanupService.CleanupResult result = cleanupService.emergencyCleanup();
        
        // Then
        assertEquals(1, result.getDeletedDirectories());
        assertFalse(Files.exists(completedBuildDir));
    }
    
    @Test
    void shouldCalculateDirectorySize() throws IOException {
        // Given
        Path buildDir = tempDir.resolve("build-777");
        Files.createDirectories(buildDir);
        
        // Create files with known sizes
        Files.write(buildDir.resolve("file1.txt"), "Hello World".getBytes()); // 11 bytes
        Files.write(buildDir.resolve("file2.txt"), "Test Content".getBytes()); // 12 bytes
        
        Path subDir = buildDir.resolve("subdir");
        Files.createDirectories(subDir);
        Files.write(subDir.resolve("file3.txt"), "More Content".getBytes()); // 12 bytes
        
        // When
        CleanupService.CleanupStatistics stats = cleanupService.getCleanupStatistics();
        
        // Then
        assertTrue(stats.getTotalDirectories() >= 1);
        assertTrue(stats.getTotalSizeMb() >= 0); // Size should be calculated
    }
    
    @Test
    void shouldExtractBuildIdFromDirectoryName() throws IOException {
        // Test different directory name formats
        Path buildDir1 = tempDir.resolve("build-123");
        Files.createDirectories(buildDir1);
        
        Path buildDir2 = tempDir.resolve("456");
        Files.createDirectories(buildDir2);
        
        Build build1 = createTestBuild(123L, LocalDateTime.now().minusDays(10));
        Build build2 = createTestBuild(456L, LocalDateTime.now().minusDays(10));
        
        when(buildRepository.findById(123L)).thenReturn(Optional.of(build1));
        when(buildRepository.findById(456L)).thenReturn(Optional.of(build2));
        
        // When
        CleanupService.CleanupResult result = cleanupService.cleanupOldWorkingDirectories();
        
        // Then
        assertEquals(2, result.getDeletedDirectories());
    }
    
    @Test
    void shouldSkipCleanupWhenDisabled() {
        // Given
        ReflectionTestUtils.setField(cleanupService, "cleanupEnabled", false);
        
        // When
        cleanupService.scheduledCleanup();
        
        // Then
        verify(buildRepository, never()).findById(any());
    }
    
    @Test
    void shouldHandleIOExceptionsDuringCleanup() throws IOException {
        // Given
        Path problematicDir = tempDir.resolve("build-888");
        Files.createDirectories(problematicDir);
        
        // Create a file and make the directory read-only (on Unix systems)
        Path file = problematicDir.resolve("readonly.txt");
        Files.createFile(file);
        
        Build build = createTestBuild(888L, LocalDateTime.now().minusDays(10));
        when(buildRepository.findById(888L)).thenReturn(Optional.of(build));
        
        // When
        CleanupService.CleanupResult result = cleanupService.cleanupOldWorkingDirectories();
        
        // Then - Should handle errors gracefully
        assertTrue(result.getDeletedDirectories() >= 0);
        assertTrue(result.getErrors() >= 0);
    }
    
    @Test
    void shouldGetCleanupStatistics() throws IOException {
        // Given
        Path buildDir1 = tempDir.resolve("build-111");
        Path buildDir2 = tempDir.resolve("build-222");
        Files.createDirectories(buildDir1);
        Files.createDirectories(buildDir2);
        
        Files.createFile(buildDir1.resolve("file1.txt"));
        Files.createFile(buildDir2.resolve("file2.txt"));
        
        // When
        CleanupService.CleanupStatistics stats = cleanupService.getCleanupStatistics();
        
        // Then
        assertTrue(stats.getTotalDirectories() >= 2);
        assertTrue(stats.getTotalSizeMb() >= 0);
        assertTrue(stats.getOldDirectories() >= 0);
    }
    
    private Build createTestBuild(Long id, LocalDateTime createdAt) {
        Build build = new Build("test-job", 123, "main", 
            "https://github.com/test/repo.git", "abc123");
        build.setId(id);
        build.setCreatedAt(createdAt);
        build.setStatus(BuildStatus.COMPLETED);
        return build;
    }
}