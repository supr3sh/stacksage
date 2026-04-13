package com.stacksage.config;

import com.stacksage.repository.UploadRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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

    public OrphanFileCleanupJob(StorageConfig storageConfig, UploadRepository uploadRepository) {
        this.storageConfig = storageConfig;
        this.uploadRepository = uploadRepository;
    }

    @Scheduled(fixedDelayString = "${app.cleanup.interval-ms:900000}")
    public void cleanupOrphanedFiles() {
        Path uploadDir = storageConfig.getUploadPath();
        if (!Files.exists(uploadDir)) {
            return;
        }

        Set<String> knownFilenames = uploadRepository.findAll().stream()
                .map(r -> r.getStoredFilename())
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
}
