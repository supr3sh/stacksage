package com.stacksage.service;

import com.stacksage.config.StorageConfig;
import com.stacksage.exception.FileUploadException;
import com.stacksage.exception.ResourceNotFoundException;
import com.stacksage.exception.StorageException;
import com.stacksage.model.UploadRecord;
import com.stacksage.model.UploadResponse;
import com.stacksage.model.UploadStatus;
import com.stacksage.repository.UploadRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.UUID;

@Service
public class FileUploadServiceImpl implements FileUploadService {

    private static final Logger log = LoggerFactory.getLogger(FileUploadServiceImpl.class);
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(".log", ".txt");
    private static final int CONTENT_PROBE_SIZE = 8192;

    private final StorageConfig storageConfig;
    private final UploadRepository uploadRepository;
    private final AnalysisOrchestrator analysisOrchestrator;

    public FileUploadServiceImpl(StorageConfig storageConfig,
                                 UploadRepository uploadRepository,
                                 AnalysisOrchestrator analysisOrchestrator) {
        this.storageConfig = storageConfig;
        this.uploadRepository = uploadRepository;
        this.analysisOrchestrator = analysisOrchestrator;
    }

    @Override
    public UploadResponse uploadFile(MultipartFile file) {
        validateFile(file);

        String originalFilename = sanitizeFilename(file.getOriginalFilename());
        String storedFilename = UUID.randomUUID() + "_" + originalFilename;

        // Save DB record first with PENDING status to avoid orphaned files
        UploadRecord record = UploadRecord.builder()
                .originalFilename(originalFilename)
                .storedFilename(storedFilename)
                .fileSize(Math.max(0, file.getSize()))
                .status(UploadStatus.PENDING)
                .build();
        UploadRecord saved = uploadRepository.save(record);

        // Write file to disk
        Path targetPath = storageConfig.getUploadPath().resolve(storedFilename);
        try {
            Files.copy(file.getInputStream(), targetPath);
        } catch (IOException e) {
            uploadRepository.delete(saved);
            throw new StorageException("Failed to store file: " + originalFilename, e);
        }

        // Mark as UPLOADED after successful write
        saved.setStatus(UploadStatus.UPLOADED);
        uploadRepository.save(saved);

        log.info("File uploaded: {} -> {} (id={})", originalFilename, storedFilename, saved.getId());

        analysisOrchestrator.triggerAsync(saved.getId());

        return UploadResponse.fromRecord(saved);
    }

    @Override
    public UploadResponse getUpload(String id) {
        UploadRecord record = findRecordOrThrow(id);
        return UploadResponse.fromRecord(record);
    }

    @Override
    public UploadResponse getUploadWithContent(String id) {
        UploadRecord record = findRecordOrThrow(id);
        String content = readFileContent(record.getStoredFilename());
        return UploadResponse.fromRecordWithContent(record, content);
    }

    @Override
    public void deleteUpload(String id) {
        UploadRecord record = findRecordOrThrow(id);
        uploadRepository.delete(record);

        Path filePath = storageConfig.getUploadPath().resolve(record.getStoredFilename());
        try {
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            log.warn("DB record deleted but file cleanup failed for {}: {}",
                    record.getStoredFilename(), e.getMessage());
        }
        log.info("Upload deleted: id={}, file={}", id, record.getOriginalFilename());
    }

    private UploadRecord findRecordOrThrow(String id) {
        return uploadRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Upload not found with id: " + id));
    }

    private String readFileContent(String storedFilename) {
        Path filePath = storageConfig.getUploadPath().resolve(storedFilename);
        Path analyzingPath = filePath.resolveSibling(filePath.getFileName() + ".analyzing");
        Path readPath = Files.exists(filePath) ? filePath : analyzingPath;
        try {
            return Files.readString(readPath, java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new StorageException("Failed to read file: " + storedFilename, e);
        }
    }

    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new FileUploadException("File is empty");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new FileUploadException("Filename is missing");
        }

        String extension = getExtension(originalFilename);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new FileUploadException(
                    "File type not allowed: " + extension + ". Accepted: " + ALLOWED_EXTENSIONS);
        }

        validateTextContent(file);
    }

    private void validateTextContent(MultipartFile file) {
        try (InputStream is = file.getInputStream()) {
            byte[] sample = is.readNBytes(CONTENT_PROBE_SIZE);
            for (byte b : sample) {
                // Allow printable ASCII, tabs, newlines, carriage returns, and UTF-8 continuation bytes
                if (b == 0) {
                    throw new FileUploadException(
                            "File appears to be binary, not a text log file");
                }
            }
        } catch (IOException e) {
            throw new FileUploadException("Unable to read file for validation", e);
        }
    }

    private String sanitizeFilename(String filename) {
        // Strip any path components to prevent directory traversal
        String safe = Paths.get(filename).getFileName().toString();
        if (safe.isBlank()) {
            throw new FileUploadException("Invalid filename");
        }
        return safe;
    }

    private String getExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        return (dotIndex == -1) ? "" : filename.substring(dotIndex).toLowerCase();
    }
}
