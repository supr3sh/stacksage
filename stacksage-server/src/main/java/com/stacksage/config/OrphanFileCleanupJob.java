package com.stacksage.config;

import com.stacksage.model.UploadRecord;
import com.stacksage.repository.AnalysisRepository;
import com.stacksage.repository.UploadRepository;
import com.stacksage.service.SseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
@ConditionalOnProperty(name = "app.cleanup.enabled", havingValue = "true", matchIfMissing = false)
public class OrphanFileCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(OrphanFileCleanupJob.class);
    private static final String ANALYZING_EXTENSION = ".analyzing";

    private final StorageConfig storageConfig;
    private final UploadRepository uploadRepository;
    private final AnalysisRepository analysisRepository;
    private final SseService sseService;

    @Value("${app.cleanup.retain-hours:48}")
    private long retainHours;

    @Value("${app.cleanup.delete-records:false}")
    private boolean deleteRecords;

    @Value("${app.cleanup.record-retention-hours:168}")
    private long recordRetentionHours;

    public OrphanFileCleanupJob(StorageConfig storageConfig,
                                UploadRepository uploadRepository,
                                AnalysisRepository analysisRepository,
                                SseService sseService) {
        this.storageConfig = storageConfig;
        this.uploadRepository = uploadRepository;
        this.analysisRepository = analysisRepository;
        this.sseService = sseService;
    }

    @Scheduled(fixedDelayString = "${app.cleanup.interval-ms:900000}")
    public void runCleanup() {
        cleanupOrphanedFiles();
        cleanupExpiredRetainedFiles();
        if (deleteRecords) {
            cleanupOldRecords();
        }
    }

    void cleanupOrphanedFiles() {
        Path uploadDir = storageConfig.getUploadPath();
        if (!Files.exists(uploadDir)) {
            return;
        }

        Set<String> knownFilenames = uploadRepository.findAll().stream()
                .map(UploadRecord::getStoredFilename)
                .collect(Collectors.toSet());

        int deleted = 0;
        try (Stream<Path> files = Files.list(uploadDir)) {
            for (Path file : files.toList()) {
                if (!Files.isRegularFile(file)) continue;

                String filename = file.getFileName().toString();
                if (filename.endsWith(ANALYZING_EXTENSION)) {
                    continue;
                }

                if (!knownFilenames.contains(filename)) {
                    try {
                        Files.delete(file);
                        deleted++;
                        log.info("Deleted orphaned file: {}", filename);
                    } catch (IOException e) {
                        log.warn("Failed to delete orphaned file {}: {}", filename, e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            log.error("Error listing upload directory for cleanup: {}", e.getMessage());
        }

        if (deleted > 0) {
            log.info("Orphan file cleanup complete: {} files deleted", deleted);
        }
    }

    void cleanupExpiredRetainedFiles() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(retainHours);
        List<UploadRecord> expired = uploadRepository.findByRetainTrueAndCreatedAtBefore(cutoff);

        int deleted = 0;
        for (UploadRecord record : expired) {
            Path filePath = storageConfig.getUploadPath().resolve(record.getStoredFilename());
            try {
                if (Files.deleteIfExists(filePath)) {
                    deleted++;
                    log.info("Deleted expired retained file: {} (uploadId={})",
                            record.getStoredFilename(), record.getId());
                    sseService.publish("file.cleanup", record.getId(), Map.of(
                            "uploadId", record.getId(),
                            "filename", record.getOriginalFilename()));
                }
            } catch (IOException e) {
                log.warn("Failed to delete expired retained file {}: {}",
                        record.getStoredFilename(), e.getMessage());
            }
        }

        if (deleted > 0) {
            log.info("Retained file TTL cleanup complete: {} files deleted", deleted);
        }
    }

    void cleanupOldRecords() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(recordRetentionHours);
        List<UploadRecord> oldUploads = uploadRepository.findByCreatedAtBefore(cutoff);

        if (oldUploads.isEmpty()) {
            return;
        }

        List<String> uploadIds = oldUploads.stream()
                .map(UploadRecord::getId)
                .toList();

        // Delete files from disk first
        for (UploadRecord record : oldUploads) {
            Path filePath = storageConfig.getUploadPath().resolve(record.getStoredFilename());
            try {
                Files.deleteIfExists(filePath);
            } catch (IOException e) {
                log.warn("Failed to delete file for expired record {}: {}",
                        record.getId(), e.getMessage());
            }
        }

        // Delete analysis records first (FK constraint), then upload records
        var analysisRecords = analysisRepository.findByUploadIdIn(uploadIds);
        if (!analysisRecords.isEmpty()) {
            analysisRepository.deleteAll(analysisRecords);
            log.info("Deleted {} expired analysis records", analysisRecords.size());
        }

        uploadRepository.deleteAll(oldUploads);
        log.info("Record cleanup complete: {} upload records and {} analysis records deleted",
                oldUploads.size(), analysisRecords.size());

        for (UploadRecord record : oldUploads) {
            sseService.publish("file.cleanup", record.getId(), Map.of(
                    "uploadId", record.getId(),
                    "filename", record.getOriginalFilename()));
        }
    }
}
