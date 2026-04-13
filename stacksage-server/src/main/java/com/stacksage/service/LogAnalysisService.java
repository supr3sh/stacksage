package com.stacksage.service;

import com.stacksage.model.AnalysisResult;
import com.stacksage.parser.ExceptionDetail;

import java.util.List;

public interface LogAnalysisService {

    List<AnalysisResult> analyze(String rawLog);

    List<AnalysisResult> analyzeExceptions(List<ExceptionDetail> exceptions);
}
