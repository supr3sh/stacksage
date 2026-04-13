package com.stacksage.service;

import com.stacksage.exception.AIDiagnosticException;
import com.stacksage.model.AIDiagnosisResult;
import com.stacksage.model.AnalysisResult;
import com.stacksage.parser.ExceptionDetail;
import com.stacksage.parser.LogParser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LogAnalysisServiceImplTest {

    @Test
    void analyze_pairsExceptionsWithDiagnosis() {
        ExceptionDetail npe = ExceptionDetail.builder()
                .exceptionType("java.lang.NullPointerException")
                .message("null ref")
                .stackTrace(List.of("at com.example.Main.run(Main.java:10)"))
                .build();

        LogParser parser = rawLog -> List.of(npe);

        AIDiagnosisResult diagnosis = AIDiagnosisResult.builder()
                .rootCause("Null ref")
                .explanation("Variable not initialized")
                .suggestedFix("Add null check")
                .severity("HIGH")
                .build();

        AIDiagnosticService aiService = exception -> diagnosis;

        LogAnalysisServiceImpl service = new LogAnalysisServiceImpl(parser, aiService, Runnable::run);
        List<AnalysisResult> results = service.analyze("some log");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getException().getExceptionType())
                .isEqualTo("java.lang.NullPointerException");
        assertThat(results.get(0).getDiagnosis().getRootCause()).isEqualTo("Null ref");
    }

    @Test
    void analyze_returnsNullDiagnosis_whenAIFails() {
        ExceptionDetail npe = ExceptionDetail.builder()
                .exceptionType("java.lang.NullPointerException")
                .message("null ref")
                .stackTrace(List.of())
                .build();

        LogParser parser = rawLog -> List.of(npe);

        AIDiagnosticService aiService = exception -> {
            throw new AIDiagnosticException("API down");
        };

        LogAnalysisServiceImpl service = new LogAnalysisServiceImpl(parser, aiService, Runnable::run);
        List<AnalysisResult> results = service.analyze("some log");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getException()).isNotNull();
        assertThat(results.get(0).getDiagnosis()).isNull();
    }

    @Test
    void analyze_returnsNullDiagnosis_whenAIReturnsNull() {
        ExceptionDetail npe = ExceptionDetail.builder()
                .exceptionType("java.lang.NullPointerException")
                .message("null ref")
                .stackTrace(List.of())
                .build();

        LogParser parser = rawLog -> List.of(npe);
        AIDiagnosticService aiService = exception -> null;

        LogAnalysisServiceImpl service = new LogAnalysisServiceImpl(parser, aiService, Runnable::run);
        List<AnalysisResult> results = service.analyze("some log");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getDiagnosis()).isNull();
    }

    @Test
    void analyze_returnsEmptyList_whenNoExceptionsFound() {
        LogParser parser = rawLog -> List.of();
        AIDiagnosticService aiService = exception -> null;

        LogAnalysisServiceImpl service = new LogAnalysisServiceImpl(parser, aiService, Runnable::run);
        List<AnalysisResult> results = service.analyze("clean log with no exceptions");

        assertThat(results).isEmpty();
    }
}
