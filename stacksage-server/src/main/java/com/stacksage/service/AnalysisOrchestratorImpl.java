package com.stacksage.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stacksage.config.StorageConfig;
import com.stacksage.exception.ResourceNotFoundException;
import com.stacksage.model.AnalysisRecord;
import com.stacksage.model.AnalysisRequest;
import com.stacksage.model.AnalysisResponse;
import com.stacksage.model.AnalysisResult;
import com.stacksage.model.AnalysisStatus;
import com.stacksage.parser.ExceptionDetail;
import com.stacksage.repository.AnalysisRepository;
import com.stacksage.repository.UploadRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AnalysisOrchestratorImpl implements AnalysisOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(AnalysisOrchestratorImpl.class);
    private static final int MAX_EXCEPTIONS = 50;
    private static final String ANALYZING_EXTENSION = ".analyzing";

    private final AnalysisRepository analysisRepository;
    private final UploadRepository uploadRepository;
    private final LogAnalysisService logAnalysisService;
    private final StorageConfig storageConfig;
    private final ObjectMapper objectMapper;
    private final SseService sseService;

    public AnalysisOrchestratorImpl(AnalysisRepository analysisRepository,
                                    UploadRepository uploadRepository,
                                    LogAnalysisService logAnalysisService,
                                    StorageConfig storageConfig,
                                    ObjectMapper objectMapper,
                                    SseService sseService) {
        this.analysisRepository = analysisRepository;
        this.uploadRepository = uploadRepository;
        this.logAnalysisService = logAnalysisService;
        this.storageConfig = storageConfig;
        this.objectMapper = objectMapper;
        this.sseService = sseService;
    }

    @Override
    @Async("analysisExecutor")
    public void triggerAsync(String uploadId) {
        AnalysisRecord record = analysisRepository.save(
                AnalysisRecord.builder()
                        .uploadId(uploadId)
                        .status(AnalysisStatus.PENDING)
                        .build()
        );

        log.info("Analysis triggered: analysisId={}, uploadId={}", record.getId(), uploadId);

        Path originalPath = resolveUploadPath(uploadId);
        Path analyzingPath = null;
        if (originalPath != null) {
            analyzingPath = renameToAnalyzing(originalPath);
        }

        try {
            executeAnalysis(record, () -> {
                String rawLog = readUploadedFile(uploadId);
                return logAnalysisService.analyze(rawLog);
            });
        } finally {
            handlePostAnalysisFile(uploadId, analyzingPath, originalPath);
        }
    }

    private void handlePostAnalysisFile(String uploadId, Path analyzingPath, Path originalPath) {
        boolean retain = uploadRepository.findById(uploadId)
                .map(r -> r.isRetain())
                .orElse(true);

        if (retain) {
            if (analyzingPath != null) {
                renameFromAnalyzing(analyzingPath, originalPath);
            }
        } else {
            Path toDelete = (analyzingPath != null && Files.exists(analyzingPath))
                    ? analyzingPath : originalPath;
            if (toDelete != null) {
                deleteFileQuietly(toDelete);
            }
        }
    }

    private void deleteFileQuietly(Path path) {
        try {
            if (Files.deleteIfExists(path)) {
                log.info("Deleted non-retained upload file: {}", path.getFileName());
            }
        } catch (IOException e) {
            log.warn("Failed to delete non-retained file {}: {}", path.getFileName(), e.getMessage());
        }
    }

    @Override
    @Transactional
    public String createAnalysis(AnalysisRequest request) {
        AnalysisRecord record = analysisRepository.save(
                AnalysisRecord.builder()
                        .source(request.getSource())
                        .status(AnalysisStatus.PENDING)
                        .build()
        );
        log.info("Analysis created: analysisId={}, source={}, exceptions={}",
                record.getId(), request.getSource(),
                request.getExceptions() != null ? request.getExceptions().size() : 0);
        return record.getId();
    }

    @Override
    @Async("analysisExecutor")
    public void submitExceptionsAsync(String analysisId, AnalysisRequest request) {
        AnalysisRecord record = analysisRepository.findById(analysisId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Analysis not found: " + analysisId));

        if (request.getExceptions() == null || request.getExceptions().isEmpty()) {
            record.setStatus(AnalysisStatus.FAILED);
            record.setErrorMessage("No exceptions provided");
            record.setCompletedAt(LocalDateTime.now());
            analysisRepository.save(record);
            return;
        }

        List<ExceptionDetail> capped = capExceptions(request.getExceptions());

        log.info("Analysis processing: analysisId={}, source={}", analysisId, request.getSource());
        executeAnalysis(record, () ->
                logAnalysisService.analyzeExceptions(capped));
    }

    @Override
    public AnalysisResponse getAnalysis(String uploadId) {
        AnalysisRecord record = analysisRepository.findTopByUploadIdOrderByCreatedAtDesc(uploadId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No analysis found for upload: " + uploadId));

        List<AnalysisResult> results = deserializeResults(record.getResultsJson());
        return AnalysisResponse.fromRecord(record, results);
    }

    @Override
    public AnalysisResponse getAnalysisById(String analysisId) {
        AnalysisRecord record = analysisRepository.findById(analysisId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Analysis not found: " + analysisId));

        List<AnalysisResult> results = deserializeResults(record.getResultsJson());
        return AnalysisResponse.fromRecord(record, results);
    }

    private void executeAnalysis(AnalysisRecord record, AnalysisTask task) {
        try {
            record.setStatus(AnalysisStatus.IN_PROGRESS);
            analysisRepository.save(record);

            List<AnalysisResult> results = task.execute();

            record.setStatus(AnalysisStatus.COMPLETED);
            record.setResultsJson(objectMapper.writeValueAsString(results));
            record.setCompletedAt(LocalDateTime.now());
            analysisRepository.save(record);

            log.info("Analysis completed: analysisId={}, exceptions={}",
                    record.getId(), results.size());

            sseService.publish("analysis.completed", record.getUploadId(), Map.of(
                    "analysisId", record.getId(),
                    "uploadId", String.valueOf(record.getUploadId())));
        } catch (Exception e) {
            log.error("Analysis failed: analysisId={}: {}",
                    record.getId(), e.getMessage(), e);
            record.setStatus(AnalysisStatus.FAILED);
            record.setErrorMessage(summarizeError(e));
            record.setCompletedAt(LocalDateTime.now());
            analysisRepository.save(record);

            Map<String, Object> eventData = new HashMap<>();
            eventData.put("analysisId", record.getId());
            eventData.put("uploadId", record.getUploadId());
            eventData.put("error", record.getErrorMessage());
            sseService.publish("analysis.failed", record.getUploadId(), eventData);
        }
    }

    @FunctionalInterface
    private interface AnalysisTask {
        List<AnalysisResult> execute() throws Exception;
    }

    private Path resolveUploadPath(String uploadId) {
        try {
            var uploadRecord = uploadRepository.findById(uploadId).orElse(null);
            if (uploadRecord == null) return null;
            return storageConfig.getUploadPath().resolve(uploadRecord.getStoredFilename());
        } catch (Exception e) {
            log.warn("Could not resolve upload path for {}: {}", uploadId, e.getMessage());
            return null;
        }
    }

    private Path renameToAnalyzing(Path original) {
        Path analyzingPath = original.resolveSibling(original.getFileName() + ANALYZING_EXTENSION);
        try {
            return Files.move(original, analyzingPath, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            log.warn("Could not rename file to .analyzing: {}", e.getMessage());
            return null;
        }
    }

    private void renameFromAnalyzing(Path analyzingPath, Path originalPath) {
        try {
            if (Files.exists(analyzingPath)) {
                Files.move(analyzingPath, originalPath, StandardCopyOption.ATOMIC_MOVE);
            }
        } catch (IOException e) {
            log.warn("Could not rename file back from .analyzing: {}", e.getMessage());
        }
    }

    private String readUploadedFile(String uploadId) throws IOException {
        var uploadRecord = uploadRepository.findById(uploadId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Upload not found with id: " + uploadId));

        Path filePath = storageConfig.getUploadPath().resolve(uploadRecord.getStoredFilename());
        Path analyzingPath = filePath.resolveSibling(filePath.getFileName() + ANALYZING_EXTENSION);
        Path readPath = Files.exists(analyzingPath) ? analyzingPath : filePath;
        return Files.readString(readPath, StandardCharsets.UTF_8);
    }

    private List<ExceptionDetail> capExceptions(List<ExceptionDetail> exceptions) {
        if (exceptions.size() > MAX_EXCEPTIONS) {
            log.info("Capping exceptions from {} to {}", exceptions.size(), MAX_EXCEPTIONS);
            return exceptions.subList(0, MAX_EXCEPTIONS);
        }
        return exceptions;
    }

    List<AnalysisResult> deserializeResults(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize analysis results: {}", e.getMessage());
            return List.of();
        }
    }

    private String summarizeError(Exception e) {
        String msg = e.getMessage();
        if (msg == null) return "Unknown error";
        return msg.length() > 200 ? msg.substring(0, 200) + "..." : msg;
    }
}
