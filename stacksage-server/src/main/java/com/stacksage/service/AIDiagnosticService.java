package com.stacksage.service;

import com.stacksage.model.AIDiagnosisResult;
import com.stacksage.parser.ExceptionDetail;

public interface AIDiagnosticService {

    AIDiagnosisResult diagnose(ExceptionDetail exception);
}
