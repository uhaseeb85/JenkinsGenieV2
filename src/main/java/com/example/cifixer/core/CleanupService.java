package com.example.cifixer.core;

import com.example.cifixer.store.Build;
import com.example.cifixer.store.BuildRepository;
import com.example.cifixer.store.BuildStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service for cleaning up old working directories and maintaining system hygiene.
 */
@Service
public class CleanupService {
    
    private static final Logger logger = LoggerFactory.getLogger(CleanupService.class);
    
    private final BuildRepository buildRepository;
    
    @Value("${cleanup.working.directory.base:/work}")
    private String workingDirectoryBase;
    
    @Value("${cleanup.retention.days:7}")
    private int retentionDays;
    
    @Value("${cleanup.max.directory.size.gb:10}")
    private long maxDirectorySizeGb;
    
    @Value("${cleanup.enabled:true}")
    private boolean cleanupEnabled;
    
    @Value("${cleanup.dry.run:false}")
    private boolean dryRun;
    
    public CleanupService(BuildRepository buildRepository) {
        this.buildRepository = buildRepository;
    }
    
    /**
     * Scheduled cleanup job that runs every hour to clean old working directories.
     */
    @Scheduled(fixedRate = 3600000) // 1 hour
    public void scheduledCleanup() {
        if (!cleanupEnabled) {
            logger.debug("Cleanup is disabled, skipping scheduled cleanup");
            return;
        }
        
        String correlationId = "cleanup-" + System.currentTimeMillis();
        
        try {
            MDC.put("correlationId", correlationId);
            logger.info("Starting scheduled cleanup of working directories");
            
            CleanupResult result = cleanupOldWorkingDirectories();
            
            logger.info("Scheduled cleanup completed: deleted={} directories, freed={} MB, errors={}", 
                result.getDeletedDirectories(), result.getFreedSpaceMb(), result.getErrors());
            
        } catch (Exception e) {
            logger.error("Error during scheduled cleanup", e);
        } finally {
            MDC.clear();
        }
    }
    
    /**
     * Cleans up old working directories based on retention policy.
     */
    public CleanupResult cleanupOldWorkingDirectories() {
        CleanupResult result = new CleanupResult();
        
        try {
            Path workingDir = Paths.get(workingDirectoryBase);
            
            if (!Files.exists(workingDir)) {
                logger.warn("Working directory base does not exist: {}", workingDirectoryBase);
                return result;
            }
            
            logger.info("Cleaning up working directories older than {} days in: {}", 
                retentionDays, workingDirectoryBase);
            
            LocalDateTime cutoffTime = LocalDateTime.now().minus(retentionDays, ChronoUnit.DAYS);
            
            // Find old build directories
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(workingDir)) {
                for (Path buildDir : stream) {
                    if (Files.isDirectory(buildDir)) {
                        processWorkingDirectory(buildDir, cutoffTime, result);
                    }
                }
            }
            
            // Clean up orphaned directories (no corresponding build record)
            cleanupOrphanedDirectories(workingDir, result);
            
        } catch (IOException e) {
            logger.error("Error accessing working directory: {}", workingDirectoryBase, e);
            result.addError("Failed to access working directory: " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * Processes a single working directory for cleanup.
     */
    private void processWorkingDirectory(Path buildDir, LocalDateTime cutoffTime, CleanupResult result) {
        try {
            String dirName = buildDir.getFileName().toString();
            
            // Extract build ID from directory name (assuming format: build-{id} or {id})
            Long buildId = extractBuildId(dirName);
            
            if (buildId == null) {
                logger.warn("Cannot extract build ID from directory name: {}", dirName);
                return;
            }
            
            // Check if build exists and its status
            Build build = buildRepository.findById(buildId).orElse(null);
            
            boolean shouldDelete = false;
            String reason = "";
            
            if (build == null) {
                shouldDelete = true;
                reason = "orphaned (no build record)";
            } else if (isOldEnoughForCleanup(build, cutoffTime)) {
                shouldDelete = true;
                reason = "older than " + retentionDays + " days";
            } else if (build.getStatus() == BuildStatus.FAILED && 
                      isOldEnoughForFailedBuildCleanup(build)) {
                shouldDelete = true;
                reason = "failed build older than 1 day";
            }
            
            if (shouldDelete) {
                long sizeMb = calculateDirectorySize(buildDir);
                
                if (dryRun) {
                    logger.info("DRY RUN: Would delete directory {} ({} MB) - {}", 
                        buildDir, sizeMb, reason);
                } else {
                    deleteDirectory(buildDir);
                    result.addDeletedDirectory(sizeMb);
                    logger.info("Deleted working directory {} ({} MB) - {}", 
                        buildDir, sizeMb, reason);
                }
            }
            
        } catch (Exception e) {
            logger.error("Error processing working directory: {}", buildDir, e);
            result.addError("Failed to process " + buildDir + ": " + e.getMessage());
        }
    }
    
    /**
     * Cleans up directories that don't have corresponding build records.
     */
    private void cleanupOrphanedDirectories(Path workingDir, CleanupResult result) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(workingDir)) {
            for (Path dir : stream) {
                if (Files.isDirectory(dir)) {
                    String dirName = dir.getFileName().toString();
                    Long buildId = extractBuildId(dirName);
                    
                    if (buildId != null && !buildRepository.existsById(buildId)) {
                        long sizeMb = calculateDirectorySize(dir);
                        
                        if (dryRun) {
                            logger.info("DRY RUN: Would delete orphaned directory {} ({} MB)", 
                                dir, sizeMb);
                        } else {
                            deleteDirectory(dir);
                            result.addDeletedDirectory(sizeMb);
                            logger.info("Deleted orphaned directory {} ({} MB)", dir, sizeMb);
                        }
                    }
                }
            }
        } catch (IOException e) {
            logger.error("Error cleaning orphaned directories", e);
            result.addError("Failed to clean orphaned directories: " + e.getMessage());
        }
    }
    
    /**
     * Extracts build ID from directory name.
     */
    private Long extractBuildId(String dirName) {
        try {
            // Try format: build-{id}
            if (dirName.startsWith("build-")) {
                return Long.parseLong(dirName.substring(6));
            }
            
            // Try format: {id}
            return Long.parseLong(dirName);
            
        } catch (NumberFormatException e) {
            return null;
        }
    }
    
    /**
     * Checks if a build is old enough for cleanup.
     */
    private boolean isOldEnoughForCleanup(Build build, LocalDateTime cutoffTime) {
        return build.getCreatedAt().isBefore(cutoffTime);
    }
    
    /**
     * Checks if a failed build is old enough for cleanup (shorter retention).
     */
    private boolean isOldEnoughForFailedBuildCleanup(Build build) {
        LocalDateTime oneDayAgo = LocalDateTime.now().minus(1, ChronoUnit.DAYS);
        return build.getCreatedAt().isBefore(oneDayAgo);
    }
    
    /**
     * Calculates the size of a directory in MB.
     */
    private long calculateDirectorySize(Path directory) {
        AtomicLong size = new AtomicLong(0);
        
        try {
            Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    size.addAndGet(attrs.size());
                    return FileVisitResult.CONTINUE;
                }
                
                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    logger.warn("Failed to visit file during size calculation: {}", file, exc);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            logger.warn("Failed to calculate directory size: {}", directory, e);
        }
        
        return size.get() / (1024 * 1024); // Convert to MB
    }
    
    /**
     * Deletes a directory and all its contents.
     */
    private void deleteDirectory(Path directory) throws IOException {
        Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }
            
            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
    
    /**
     * Performs emergency cleanup when disk space is low.
     */
    public CleanupResult emergencyCleanup() {
        logger.warn("Performing emergency cleanup due to low disk space");
        
        CleanupResult result = new CleanupResult();
        
        try {
            Path workingDir = Paths.get(workingDirectoryBase);
            
            if (!Files.exists(workingDir)) {
                return result;
            }
            
            // More aggressive cleanup - delete all completed builds older than 1 day
            LocalDateTime emergencyCutoff = LocalDateTime.now().minus(1, ChronoUnit.DAYS);
            
            List<Build> oldBuilds = buildRepository.findByStatusAndCreatedAtBefore(
                BuildStatus.COMPLETED, emergencyCutoff);
            
            for (Build build : oldBuilds) {
                Path buildDir = workingDir.resolve("build-" + build.getId());
                
                if (Files.exists(buildDir)) {
                    long sizeMb = calculateDirectorySize(buildDir);
                    deleteDirectory(buildDir);
                    result.addDeletedDirectory(sizeMb);
                    
                    logger.info("Emergency cleanup: deleted directory for build {} ({} MB)", 
                        build.getId(), sizeMb);
                }
            }
            
        } catch (Exception e) {
            logger.error("Error during emergency cleanup", e);
            result.addError("Emergency cleanup failed: " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * Gets cleanup statistics.
     */
    public CleanupStatistics getCleanupStatistics() {
        try {
            Path workingDir = Paths.get(workingDirectoryBase);
            
            if (!Files.exists(workingDir)) {
                return new CleanupStatistics(0, 0, 0);
            }
            
            AtomicLong totalDirectories = new AtomicLong(0);
            AtomicLong totalSize = new AtomicLong(0);
            AtomicLong oldDirectories = new AtomicLong(0);
            
            LocalDateTime cutoffTime = LocalDateTime.now().minus(retentionDays, ChronoUnit.DAYS);
            
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(workingDir)) {
                for (Path buildDir : stream) {
                    if (Files.isDirectory(buildDir)) {
                        totalDirectories.incrementAndGet();
                        long size = calculateDirectorySize(buildDir);
                        totalSize.addAndGet(size);
                        
                        // Check if directory is old
                        try {
                            BasicFileAttributes attrs = Files.readAttributes(buildDir, BasicFileAttributes.class);
                            LocalDateTime creationTime = LocalDateTime.ofInstant(
                                attrs.creationTime().toInstant(), 
                                java.time.ZoneId.systemDefault());
                            
                            if (creationTime.isBefore(cutoffTime)) {
                                oldDirectories.incrementAndGet();
                            }
                        } catch (IOException e) {
                            logger.warn("Failed to read attributes for directory: {}", buildDir);
                        }
                    }
                }
            }
            
            return new CleanupStatistics(
                totalDirectories.get(), 
                totalSize.get(), 
                oldDirectories.get());
            
        } catch (IOException e) {
            logger.error("Failed to get cleanup statistics", e);
            return new CleanupStatistics(0, 0, 0);
        }
    }
    
    /**
     * Result of cleanup operations.
     */
    public static class CleanupResult {
        private int deletedDirectories = 0;
        private long freedSpaceMb = 0;
        private int errors = 0;
        
        public void addDeletedDirectory(long sizeMb) {
            deletedDirectories++;
            freedSpaceMb += sizeMb;
        }
        
        public void addError(String error) {
            errors++;
        }
        
        public int getDeletedDirectories() { return deletedDirectories; }
        public long getFreedSpaceMb() { return freedSpaceMb; }
        public int getErrors() { return errors; }
    }
    
    /**
     * Statistics about cleanup state.
     */
    public static class CleanupStatistics {
        private final long totalDirectories;
        private final long totalSizeMb;
        private final long oldDirectories;
        
        public CleanupStatistics(long totalDirectories, long totalSizeMb, long oldDirectories) {
            this.totalDirectories = totalDirectories;
            this.totalSizeMb = totalSizeMb;
            this.oldDirectories = oldDirectories;
        }
        
        public long getTotalDirectories() { return totalDirectories; }
        public long getTotalSizeMb() { return totalSizeMb; }
        public long getOldDirectories() { return oldDirectories; }
    }
}