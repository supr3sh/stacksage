package com.stacksage.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UploadResponse {

    private String id;
    private String filename;
    private long fileSize;
    private UploadStatus status;
    private boolean retain;
    private LocalDateTime createdAt;
    private String content;

    public static UploadResponse fromRecord(UploadRecord record) {
        return UploadResponse.builder()
                .id(record.getId())
                .filename(record.getOriginalFilename())
                .fileSize(record.getFileSize())
                .status(record.getStatus())
                .retain(record.isRetain())
                .createdAt(record.getCreatedAt())
                .build();
    }

    public static UploadResponse fromRecordWithContent(UploadRecord record, String content) {
        return UploadResponse.builder()
                .id(record.getId())
                .filename(record.getOriginalFilename())
                .fileSize(record.getFileSize())
                .status(record.getStatus())
                .retain(record.isRetain())
                .createdAt(record.getCreatedAt())
                .content(content)
                .build();
    }
}
