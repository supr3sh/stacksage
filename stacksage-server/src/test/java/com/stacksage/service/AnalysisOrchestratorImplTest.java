package com.stacksage.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stacksage.config.StorageConfig;
import com.stacksage.exception.ResourceNotFoundException;
import com.stacksage.model.AnalysisRecord;
import com.stacksage.model.AnalysisResponse;
import com.stacksage.model.AnalysisResult;
import com.stacksage.model.AnalysisStatus;
import com.stacksage.model.UploadRecord;
import com.stacksage.model.UploadStatus;
import com.stacksage.repository.AnalysisRepository;
import com.stacksage.repository.UploadRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnalysisOrchestratorImplTest {

    @TempDir
    Path tempDir;

    private AnalysisOrchestratorImpl orchestrator;
    private FakeAnalysisRepository analysisRepo;
    private FakeUploadRepository uploadRepo;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        analysisRepo = new FakeAnalysisRepository();
        uploadRepo = new FakeUploadRepository();
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();

        StorageConfig storageConfig = new StorageConfig();
        storageConfig.setUploadDir(tempDir.toString());
        try {
            storageConfig.init();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        LogAnalysisService logAnalysisService = new LogAnalysisService() {
            @Override
            public List<AnalysisResult> analyze(String rawLog) { return List.of(); }
            @Override
            public List<AnalysisResult> analyzeExceptions(java.util.List<com.stacksage.parser.ExceptionDetail> exceptions) { return List.of(); }
        };

        orchestrator = new AnalysisOrchestratorImpl(
                analysisRepo, uploadRepo, logAnalysisService, storageConfig, objectMapper,
                new SseService());
    }

    @Test
    void triggerAsync_completesSuccessfully() throws Exception {
        String uploadId = "upload-1";
        setupUploadWithFile(uploadId, "test.log", "some log content");

        orchestrator.triggerAsync(uploadId);

        AnalysisRecord saved = analysisRepo.lastSaved.get();
        assertThat(saved).isNotNull();
        assertThat(saved.getStatus()).isEqualTo(AnalysisStatus.COMPLETED);
        assertThat(saved.getCompletedAt()).isNotNull();
        assertThat(saved.getErrorMessage()).isNull();
    }

    @Test
    void triggerAsync_setsFailedStatus_whenUploadNotFound() {
        orchestrator.triggerAsync("nonexistent");

        AnalysisRecord saved = analysisRepo.lastSaved.get();
        assertThat(saved).isNotNull();
        assertThat(saved.getStatus()).isEqualTo(AnalysisStatus.FAILED);
        assertThat(saved.getErrorMessage()).contains("Upload not found");
    }

    @Test
    void getAnalysis_returnsResponse_whenRecordExists() throws Exception {
        String uploadId = "upload-2";
        String resultsJson = "[]";
        AnalysisRecord record = AnalysisRecord.builder()
                .id("analysis-1")
                .uploadId(uploadId)
                .status(AnalysisStatus.COMPLETED)
                .resultsJson(resultsJson)
                .createdAt(LocalDateTime.now())
                .completedAt(LocalDateTime.now())
                .build();
        analysisRepo.savedRecord = record;

        AnalysisResponse response = orchestrator.getAnalysis(uploadId);

        assertThat(response.getAnalysisId()).isEqualTo("analysis-1");
        assertThat(response.getStatus()).isEqualTo(AnalysisStatus.COMPLETED);
    }

    @Test
    void getAnalysis_throwsNotFound_whenNoRecordExists() {
        assertThatThrownBy(() -> orchestrator.getAnalysis("unknown"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("No analysis found");
    }

    @Test
    void createAnalysis_returnsId() {
        com.stacksage.model.AnalysisRequest request = com.stacksage.model.AnalysisRequest.builder()
                .source("test-cli.log")
                .exceptions(List.of(
                        com.stacksage.parser.ExceptionDetail.builder()
                                .exceptionType("java.lang.NullPointerException")
                                .message("test")
                                .stackTrace(List.of("at Foo.bar(Foo.java:1)"))
                                .build()))
                .build();

        String id = orchestrator.createAnalysis(request);

        assertThat(id).isNotNull();
        AnalysisRecord record = analysisRepo.lastSaved.get();
        assertThat(record.getSource()).isEqualTo("test-cli.log");
        assertThat(record.getStatus()).isEqualTo(AnalysisStatus.PENDING);
    }

    @Test
    void submitExceptionsAsync_completesSuccessfully() {
        com.stacksage.model.AnalysisRequest request = com.stacksage.model.AnalysisRequest.builder()
                .source("test-cli.log")
                .exceptions(List.of(
                        com.stacksage.parser.ExceptionDetail.builder()
                                .exceptionType("java.lang.NullPointerException")
                                .message("test")
                                .stackTrace(List.of("at Foo.bar(Foo.java:1)"))
                                .build()))
                .build();

        String analysisId = orchestrator.createAnalysis(request);
        orchestrator.submitExceptionsAsync(analysisId, request);

        AnalysisRecord saved = analysisRepo.lastSaved.get();
        assertThat(saved.getStatus()).isEqualTo(AnalysisStatus.COMPLETED);
        assertThat(saved.getCompletedAt()).isNotNull();
    }

    private void setupUploadWithFile(String uploadId, String filename, String content) throws Exception {
        Path file = tempDir.resolve("stored_" + filename);
        Files.writeString(file, content);

        UploadRecord upload = UploadRecord.builder()
                .id(uploadId)
                .originalFilename(filename)
                .storedFilename("stored_" + filename)
                .fileSize(content.length())
                .status(UploadStatus.UPLOADED)
                .createdAt(LocalDateTime.now())
                .build();
        uploadRepo.record = upload;
    }

    private static class FakeAnalysisRepository implements AnalysisRepository {
        final AtomicReference<AnalysisRecord> lastSaved = new AtomicReference<>();
        AnalysisRecord savedRecord;
        private int idCounter = 0;

        @Override
        public <S extends AnalysisRecord> S save(S entity) {
            if (entity.getId() == null) {
                entity.setId("fake-analysis-" + (++idCounter));
                if (entity.getCreatedAt() == null) {
                    entity.setCreatedAt(LocalDateTime.now());
                }
            }
            lastSaved.set(entity);
            savedRecord = entity;
            return entity;
        }

        @Override
        public Optional<AnalysisRecord> findTopByUploadIdOrderByCreatedAtDesc(String uploadId) {
            if (savedRecord != null && uploadId.equals(savedRecord.getUploadId())) {
                return Optional.of(savedRecord);
            }
            return Optional.empty();
        }

        @Override
        public List<AnalysisRecord> findByUploadIdIn(List<String> uploadIds) {
            if (savedRecord != null && uploadIds.contains(savedRecord.getUploadId())) {
                return List.of(savedRecord);
            }
            return List.of();
        }

        @Override
        public Optional<AnalysisRecord> findById(String s) {
            if (savedRecord != null && s.equals(savedRecord.getId())) {
                return Optional.of(savedRecord);
            }
            return Optional.empty();
        }
        @Override public boolean existsById(String s) { return false; }
        @Override public List<AnalysisRecord> findAll() { return List.of(); }
        @Override public List<AnalysisRecord> findAllById(Iterable<String> strings) { return List.of(); }
        @Override public long count() { return 0; }
        @Override public void deleteById(String s) {}
        @Override public void delete(AnalysisRecord entity) {}
        @Override public void deleteAllById(Iterable<? extends String> strings) {}
        @Override public void deleteAll(Iterable<? extends AnalysisRecord> entities) {}
        @Override public void deleteAll() {}
        @Override public <S extends AnalysisRecord> List<S> saveAll(Iterable<S> entities) { return List.of(); }
        @Override public void flush() {}
        @Override public <S extends AnalysisRecord> S saveAndFlush(S entity) { return save(entity); }
        @Override public <S extends AnalysisRecord> List<S> saveAllAndFlush(Iterable<S> entities) { return List.of(); }
        @Override public void deleteAllInBatch(Iterable<AnalysisRecord> entities) {}
        @Override public void deleteAllByIdInBatch(Iterable<String> strings) {}
        @Override public void deleteAllInBatch() {}
        @Override public AnalysisRecord getOne(String s) { return null; }
        @Override public AnalysisRecord getById(String s) { return null; }
        @Override public AnalysisRecord getReferenceById(String s) { return null; }
        @Override public <S extends AnalysisRecord> Optional<S> findOne(org.springframework.data.domain.Example<S> example) { return Optional.empty(); }
        @Override public <S extends AnalysisRecord> List<S> findAll(org.springframework.data.domain.Example<S> example) { return List.of(); }
        @Override public <S extends AnalysisRecord> List<S> findAll(org.springframework.data.domain.Example<S> example, org.springframework.data.domain.Sort sort) { return List.of(); }
        @Override public <S extends AnalysisRecord> org.springframework.data.domain.Page<S> findAll(org.springframework.data.domain.Example<S> example, org.springframework.data.domain.Pageable pageable) { return org.springframework.data.domain.Page.empty(); }
        @Override public <S extends AnalysisRecord> long count(org.springframework.data.domain.Example<S> example) { return 0; }
        @Override public <S extends AnalysisRecord> boolean exists(org.springframework.data.domain.Example<S> example) { return false; }
        @Override public <S extends AnalysisRecord, R> R findBy(org.springframework.data.domain.Example<S> example, java.util.function.Function<org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery<S>, R> queryFunction) { return null; }
        @Override public List<AnalysisRecord> findAll(org.springframework.data.domain.Sort sort) { return List.of(); }
        @Override public org.springframework.data.domain.Page<AnalysisRecord> findAll(org.springframework.data.domain.Pageable pageable) { return org.springframework.data.domain.Page.empty(); }
    }

    private static class FakeUploadRepository implements UploadRepository {
        UploadRecord record;

        @Override
        public Optional<UploadRecord> findById(String s) {
            if (record != null && record.getId().equals(s)) {
                return Optional.of(record);
            }
            return Optional.empty();
        }

        @Override
        public List<UploadRecord> findByRetainTrueAndCreatedAtBefore(LocalDateTime cutoff) {
            return List.of();
        }

        @Override
        public List<UploadRecord> findByCreatedAtBefore(LocalDateTime cutoff) {
            return List.of();
        }

        @Override public <S extends UploadRecord> S save(S entity) { return entity; }
        @Override public boolean existsById(String s) { return false; }
        @Override public List<UploadRecord> findAll() { return List.of(); }
        @Override public List<UploadRecord> findAllById(Iterable<String> strings) { return List.of(); }
        @Override public long count() { return 0; }
        @Override public void deleteById(String s) {}
        @Override public void delete(UploadRecord entity) {}
        @Override public void deleteAllById(Iterable<? extends String> strings) {}
        @Override public void deleteAll(Iterable<? extends UploadRecord> entities) {}
        @Override public void deleteAll() {}
        @Override public <S extends UploadRecord> List<S> saveAll(Iterable<S> entities) { return List.of(); }
        @Override public void flush() {}
        @Override public <S extends UploadRecord> S saveAndFlush(S entity) { return entity; }
        @Override public <S extends UploadRecord> List<S> saveAllAndFlush(Iterable<S> entities) { return List.of(); }
        @Override public void deleteAllInBatch(Iterable<UploadRecord> entities) {}
        @Override public void deleteAllByIdInBatch(Iterable<String> strings) {}
        @Override public void deleteAllInBatch() {}
        @Override public UploadRecord getOne(String s) { return null; }
        @Override public UploadRecord getById(String s) { return null; }
        @Override public UploadRecord getReferenceById(String s) { return null; }
        @Override public <S extends UploadRecord> Optional<S> findOne(org.springframework.data.domain.Example<S> example) { return Optional.empty(); }
        @Override public <S extends UploadRecord> List<S> findAll(org.springframework.data.domain.Example<S> example) { return List.of(); }
        @Override public <S extends UploadRecord> List<S> findAll(org.springframework.data.domain.Example<S> example, org.springframework.data.domain.Sort sort) { return List.of(); }
        @Override public <S extends UploadRecord> org.springframework.data.domain.Page<S> findAll(org.springframework.data.domain.Example<S> example, org.springframework.data.domain.Pageable pageable) { return org.springframework.data.domain.Page.empty(); }
        @Override public <S extends UploadRecord> long count(org.springframework.data.domain.Example<S> example) { return 0; }
        @Override public <S extends UploadRecord> boolean exists(org.springframework.data.domain.Example<S> example) { return false; }
        @Override public <S extends UploadRecord, R> R findBy(org.springframework.data.domain.Example<S> example, java.util.function.Function<org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery<S>, R> queryFunction) { return null; }
        @Override public List<UploadRecord> findAll(org.springframework.data.domain.Sort sort) { return List.of(); }
        @Override public org.springframework.data.domain.Page<UploadRecord> findAll(org.springframework.data.domain.Pageable pageable) { return org.springframework.data.domain.Page.empty(); }
    }
}
