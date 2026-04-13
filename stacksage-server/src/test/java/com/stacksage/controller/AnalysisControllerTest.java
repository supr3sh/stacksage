package com.stacksage.controller;

import com.jayway.jsonpath.JsonPath;
import com.stacksage.model.AnalysisRecord;
import com.stacksage.model.AnalysisStatus;
import com.stacksage.repository.AnalysisRepository;
import com.stacksage.repository.UploadRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AnalysisControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UploadRepository uploadRepository;

    @Autowired
    private AnalysisRepository analysisRepository;

    @Value("${app.upload-dir}")
    private String uploadDir;

    @AfterEach
    void cleanup() throws IOException {
        analysisRepository.deleteAll();
        uploadRepository.deleteAll();
        Path dir = Paths.get(uploadDir).toAbsolutePath().normalize();
        if (Files.exists(dir)) {
            try (Stream<Path> entries = Files.list(dir)) {
                entries.forEach(p -> {
                    try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                });
            }
        } else {
            Files.createDirectories(dir);
        }
    }

    @Test
    void getAnalysis_pendingRecord_returnsPendingStatus() throws Exception {
        String uploadId = "fake-upload-pending";

        AnalysisRecord record = AnalysisRecord.builder()
                .uploadId(uploadId)
                .status(AnalysisStatus.PENDING)
                .build();
        analysisRepository.save(record);

        mockMvc.perform(get("/api/v1/uploads/{uploadId}/analysis", uploadId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uploadId", is(uploadId)))
                .andExpect(jsonPath("$.status", is("PENDING")));
    }

    @Test
    void getAnalysis_completedRecord_returnsResults() throws Exception {
        String uploadId = "fake-upload-completed";

        String resultsJson = """
                [{"exception":{"exceptionType":"java.lang.NullPointerException","message":"test","stackTrace":["at Foo.bar(Foo.java:1)"]},"diagnosis":{"rootCause":"NPE","explanation":"null ref","suggestedFix":"add null check","severity":"HIGH"}}]
                """;

        AnalysisRecord record = AnalysisRecord.builder()
                .uploadId(uploadId)
                .status(AnalysisStatus.COMPLETED)
                .resultsJson(resultsJson.trim())
                .completedAt(LocalDateTime.now())
                .build();
        analysisRepository.save(record);

        mockMvc.perform(get("/api/v1/uploads/{uploadId}/analysis", uploadId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("COMPLETED")))
                .andExpect(jsonPath("$.results", hasSize(1)))
                .andExpect(jsonPath("$.results[0].exception.exceptionType",
                        is("java.lang.NullPointerException")))
                .andExpect(jsonPath("$.results[0].diagnosis.rootCause", is("NPE")))
                .andExpect(jsonPath("$.completedAt", notNullValue()));
    }

    @Test
    void getAnalysis_failedRecord_returnsErrorMessage() throws Exception {
        String uploadId = "fake-upload-failed";

        AnalysisRecord record = AnalysisRecord.builder()
                .uploadId(uploadId)
                .status(AnalysisStatus.FAILED)
                .errorMessage("AI service unavailable")
                .completedAt(LocalDateTime.now())
                .build();
        analysisRepository.save(record);

        mockMvc.perform(get("/api/v1/uploads/{uploadId}/analysis", uploadId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("FAILED")))
                .andExpect(jsonPath("$.errorMessage", is("AI service unavailable")));
    }

    @Test
    void getAnalysis_noAnalysisRecord_returnsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/uploads/{uploadId}/analysis", "nonexistent-id"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void upload_triggersAsyncAnalysis_recordCreated() throws Exception {
        String uploadId = uploadFileAndGetId("trigger.log",
                "java.lang.NullPointerException: test\n\tat com.example.Main.run(Main.java:10)");

        await().atMost(5, TimeUnit.SECONDS)
                .pollInterval(200, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    AnalysisRecord record = analysisRepository
                            .findTopByUploadIdOrderByCreatedAtDesc(uploadId).orElse(null);
                    assertThat(record).isNotNull();
                    assertThat(record.getStatus()).isIn(
                            AnalysisStatus.PENDING, AnalysisStatus.IN_PROGRESS, AnalysisStatus.COMPLETED);
                });
    }

    @Test
    void submitAnalysis_returnsAccepted() throws Exception {
        String requestJson = """
                {
                  "source": "cli-test.log",
                  "exceptions": [
                    {
                      "exceptionType": "java.lang.NullPointerException",
                      "message": "test message",
                      "stackTrace": ["at com.example.Foo.bar(Foo.java:1)"]
                    }
                  ]
                }
                """;

        mockMvc.perform(post("/api/v1/analyses")
                        .contentType("application/json")
                        .content(requestJson))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.analysisId", notNullValue()))
                .andExpect(jsonPath("$.source", is("cli-test.log")))
                .andExpect(jsonPath("$.status", is("PENDING")));
    }

    @Test
    void submitAnalysis_emptyExceptions_returnsBadRequest() throws Exception {
        String requestJson = """
                {
                  "source": "cli-test.log",
                  "exceptions": []
                }
                """;

        mockMvc.perform(post("/api/v1/analyses")
                        .contentType("application/json")
                        .content(requestJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("At least one exception is required")));
    }

    @Test
    void submitAnalysis_noExceptions_returnsBadRequest() throws Exception {
        String requestJson = """
                {
                  "source": "cli-test.log"
                }
                """;

        mockMvc.perform(post("/api/v1/analyses")
                        .contentType("application/json")
                        .content(requestJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getAnalysisById_returnsRecord() throws Exception {
        AnalysisRecord record = AnalysisRecord.builder()
                .source("test-source.log")
                .status(AnalysisStatus.COMPLETED)
                .resultsJson("[]")
                .completedAt(LocalDateTime.now())
                .build();
        AnalysisRecord saved = analysisRepository.save(record);

        mockMvc.perform(get("/api/v1/analyses/{analysisId}", saved.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analysisId", is(saved.getId())))
                .andExpect(jsonPath("$.source", is("test-source.log")))
                .andExpect(jsonPath("$.status", is("COMPLETED")));
    }

    @Test
    void getAnalysisById_notFound_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/analyses/{analysisId}", "nonexistent-id"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").exists());
    }

    private String uploadFileAndGetId(String filename, String content) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", filename, "text/plain", content.getBytes());

        MvcResult result = mockMvc.perform(multipart("/api/v1/uploads").file(file))
                .andExpect(status().isCreated())
                .andReturn();

        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }
}
