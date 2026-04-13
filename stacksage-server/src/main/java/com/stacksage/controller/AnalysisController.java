package com.stacksage.controller;

import com.stacksage.model.AnalysisRequest;
import com.stacksage.model.AnalysisResponse;
import com.stacksage.service.AnalysisOrchestrator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Analysis", description = "AI-powered log analysis and result retrieval")
public class AnalysisController {

    private static final int MAX_EXCEPTIONS = 50;

    private final AnalysisOrchestrator analysisOrchestrator;

    public AnalysisController(AnalysisOrchestrator analysisOrchestrator) {
        this.analysisOrchestrator = analysisOrchestrator;
    }

    @GetMapping("/uploads/{uploadId}/analysis")
    @Operation(summary = "Get analysis by upload ID",
            description = "Poll for analysis results associated with a file upload. "
                    + "Returns PENDING, IN_PROGRESS, COMPLETED, or FAILED status.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Analysis status returned"),
            @ApiResponse(responseCode = "404", description = "No analysis found for this upload")
    })
    public ResponseEntity<AnalysisResponse> getAnalysisByUpload(@PathVariable String uploadId) {
        AnalysisResponse response = analysisOrchestrator.getAnalysis(uploadId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/analyses")
    @Operation(summary = "Submit pre-parsed exceptions for analysis",
            description = "Used by the CLI tool to submit locally-parsed exception data. "
                    + "The server runs AI diagnosis asynchronously and returns an analysis ID for polling.")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Analysis accepted and processing"),
            @ApiResponse(responseCode = "400", description = "No exceptions provided in request")
    })
    public ResponseEntity<AnalysisResponse> submitAnalysis(@RequestBody AnalysisRequest request) {
        if (request.getExceptions() == null || request.getExceptions().isEmpty()) {
            throw new IllegalArgumentException("At least one exception is required");
        }
        if (request.getExceptions().size() > MAX_EXCEPTIONS) {
            request = AnalysisRequest.builder()
                    .source(request.getSource())
                    .exceptions(request.getExceptions().subList(0, MAX_EXCEPTIONS))
                    .build();
        }
        String analysisId = analysisOrchestrator.createAnalysis(request);
        analysisOrchestrator.submitExceptionsAsync(analysisId, request);
        AnalysisResponse response = analysisOrchestrator.getAnalysisById(analysisId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping("/analyses/{analysisId}")
    @Operation(summary = "Get analysis by ID",
            description = "Retrieve analysis results by analysis ID. "
                    + "Use this to check status and results for CLI-submitted analyses.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Analysis found"),
            @ApiResponse(responseCode = "404", description = "Analysis not found")
    })
    public ResponseEntity<AnalysisResponse> getAnalysisById(@PathVariable String analysisId) {
        AnalysisResponse response = analysisOrchestrator.getAnalysisById(analysisId);
        return ResponseEntity.ok(response);
    }
}
