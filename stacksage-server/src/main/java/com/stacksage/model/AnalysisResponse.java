package com.stacksage.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AnalysisResponse {

    private String analysisId;
    private String uploadId;
    private String source;
    private AnalysisStatus status;
    private List<AnalysisResult> results;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;

    public static AnalysisResponse fromRecord(AnalysisRecord record, List<AnalysisResult> results) {
        return AnalysisResponse.builder()
                .analysisId(record.getId())
                .uploadId(record.getUploadId())
                .source(record.getSource())
                .status(record.getStatus())
                .results(results)
                .errorMessage(record.getErrorMessage())
                .createdAt(record.getCreatedAt())
                .completedAt(record.getCompletedAt())
                .build();
    }
}
