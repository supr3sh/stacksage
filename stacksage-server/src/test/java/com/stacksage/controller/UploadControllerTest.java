package com.stacksage.controller;

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
import java.util.stream.Stream;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UploadControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UploadRepository uploadRepository;

    @Value("${app.upload-dir}")
    private String uploadDir;

    @AfterEach
    void cleanup() throws IOException {
        uploadRepository.deleteAll();
        Path dir = Paths.get(uploadDir).toAbsolutePath().normalize();
        if (Files.exists(dir)) {
            try (Stream<Path> entries = Files.list(dir)) {
                entries.forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) {} });
            }
        } else {
            Files.createDirectories(dir);
        }
    }

    @Test
    void uploadFile_returnsCreatedWithMetadata() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "server.log", "text/plain",
                "java.lang.NullPointerException at com.example.Main.run(Main.java:42)".getBytes());

        mockMvc.perform(multipart("/api/v1/uploads").file(file))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.filename", is("server.log")))
                .andExpect(jsonPath("$.status", is("UPLOADED")));
    }

    @Test
    void uploadFile_emptyFile_returnsBadRequest() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "empty.log", "text/plain", new byte[0]);

        mockMvc.perform(multipart("/api/v1/uploads").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("File is empty")));
    }

    @Test
    void uploadFile_invalidExtension_returnsBadRequest() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "data.csv", "text/csv", "col1,col2\na,b".getBytes());

        mockMvc.perform(multipart("/api/v1/uploads").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void getUpload_existingId_returnsMetadata() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "app.log", "text/plain", "some log content".getBytes());

        MvcResult uploadResult = mockMvc.perform(multipart("/api/v1/uploads").file(file))
                .andExpect(status().isCreated())
                .andReturn();

        String id = com.jayway.jsonpath.JsonPath.read(
                uploadResult.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(get("/api/v1/uploads/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(id)))
                .andExpect(jsonPath("$.filename", is("app.log")));
    }

    @Test
    void getUpload_withContent_returnsFileContent() throws Exception {
        String logContent = "ERROR 2024-01-15 com.example.Service - NullPointerException";
        MockMultipartFile file = new MockMultipartFile(
                "file", "debug.log", "text/plain", logContent.getBytes());

        MvcResult uploadResult = mockMvc.perform(multipart("/api/v1/uploads").file(file))
                .andExpect(status().isCreated())
                .andReturn();

        String id = com.jayway.jsonpath.JsonPath.read(
                uploadResult.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(get("/api/v1/uploads/{id}", id).param("content", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", is(logContent)));
    }

    @Test
    void getUpload_unknownId_returnsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/uploads/{id}", "nonexistent-id"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void deleteUpload_existingId_returnsNoContent() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "todelete.log", "text/plain", "some content".getBytes());

        MvcResult uploadResult = mockMvc.perform(multipart("/api/v1/uploads").file(file))
                .andExpect(status().isCreated())
                .andReturn();

        String id = com.jayway.jsonpath.JsonPath.read(
                uploadResult.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(delete("/api/v1/uploads/{id}", id))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/uploads/{id}", id))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteUpload_unknownId_returnsNotFound() throws Exception {
        mockMvc.perform(delete("/api/v1/uploads/{id}", "nonexistent-id"))
                .andExpect(status().isNotFound());
    }

    @Test
    void uploadFile_pathTraversal_isSanitized() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "../../../etc/passwd.log", "text/plain", "malicious".getBytes());

        mockMvc.perform(multipart("/api/v1/uploads").file(file))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.filename", is("passwd.log")));
    }

    @Test
    void uploadFile_binaryContent_returnsBadRequest() throws Exception {
        byte[] binaryContent = new byte[]{0x50, 0x4B, 0x03, 0x04, 0x00, 0x00, 0x00};
        MockMultipartFile file = new MockMultipartFile(
                "file", "sneaky.log", "text/plain", binaryContent);

        mockMvc.perform(multipart("/api/v1/uploads").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("File appears to be binary, not a text log file")));
    }
}
