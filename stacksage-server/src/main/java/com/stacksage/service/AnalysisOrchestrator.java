package com.stacksage.service;

import com.stacksage.model.AnalysisRequest;
import com.stacksage.model.AnalysisResponse;

public interface AnalysisOrchestrator {

    void triggerAsync(String uploadId);

    void submitExceptionsAsync(String analysisId, AnalysisRequest request);

    String createAnalysis(AnalysisRequest request);

    AnalysisResponse getAnalysis(String uploadId);

    AnalysisResponse getAnalysisById(String analysisId);
}
