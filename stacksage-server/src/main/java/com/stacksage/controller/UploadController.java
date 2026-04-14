package com.stacksage.controller;

import com.stacksage.model.UploadResponse;
import com.stacksage.service.FileUploadService;
import com.stacksage.service.SseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/uploads")
@Tag(name = "Uploads", description = "Log file upload and management")
public class UploadController {

    private final FileUploadService fileUploadService;
    private final SseService sseService;

    public UploadController(FileUploadService fileUploadService, SseService sseService) {
        this.fileUploadService = fileUploadService;
        this.sseService = sseService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a log file",
            description = "Upload a .log or .txt file for AI analysis. "
                    + "Analysis is triggered asynchronously after upload.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "File uploaded, analysis triggered"),
            @ApiResponse(responseCode = "400", description = "Invalid file (empty, wrong type, or binary)"),
            @ApiResponse(responseCode = "413", description = "File exceeds size limit"),
            @ApiResponse(responseCode = "429", description = "Rate limit exceeded")
    })
    public ResponseEntity<UploadResponse> uploadFile(
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "Keep uploaded file on disk after analysis completes")
            @RequestParam(value = "retain", defaultValue = "false") boolean retain,
            @RequestHeader(value = "X-Session-Id", required = false) String sessionId) {
        UploadResponse response = fileUploadService.uploadFile(file, retain);
        if (sessionId != null && !sessionId.isBlank()) {
            sseService.link(response.getId(), sessionId);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get upload details",
            description = "Retrieve metadata for an uploaded file. Optionally include file content.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Upload found"),
            @ApiResponse(responseCode = "404", description = "Upload not found")
    })
    public ResponseEntity<UploadResponse> getUpload(
            @PathVariable String id,
            @Parameter(description = "Include file content in response")
            @RequestParam(value = "content", defaultValue = "false") boolean includeContent) {

        UploadResponse response = includeContent
                ? fileUploadService.getUploadWithContent(id)
                : fileUploadService.getUpload(id);

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete an upload",
            description = "Delete an uploaded file and its metadata.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Upload deleted"),
            @ApiResponse(responseCode = "404", description = "Upload not found")
    })
    public ResponseEntity<Void> deleteUpload(@PathVariable String id) {
        fileUploadService.deleteUpload(id);
        return ResponseEntity.noContent().build();
    }
}
