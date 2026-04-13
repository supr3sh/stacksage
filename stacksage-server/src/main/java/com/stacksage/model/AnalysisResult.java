package com.stacksage.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.stacksage.parser.ExceptionDetail;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AnalysisResult {

    private ExceptionDetail exception;
    private AIDiagnosisResult diagnosis;
}
