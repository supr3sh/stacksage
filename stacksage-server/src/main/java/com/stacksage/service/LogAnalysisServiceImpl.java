package com.stacksage.service;

import com.stacksage.model.AIDiagnosisResult;
import com.stacksage.model.AnalysisResult;
import com.stacksage.parser.ExceptionDetail;
import com.stacksage.parser.LogParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Service
public class LogAnalysisServiceImpl implements LogAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(LogAnalysisServiceImpl.class);

    private final LogParser logParser;
    private final AIDiagnosticService aiDiagnosticService;
    private final Executor aiExecutor;

    public LogAnalysisServiceImpl(LogParser logParser,
                                  AIDiagnosticService aiDiagnosticService,
                                  @Qualifier("aiExecutor") Executor aiExecutor) {
        this.logParser = logParser;
        this.aiDiagnosticService = aiDiagnosticService;
        this.aiExecutor = aiExecutor;
    }

    @Override
    public List<AnalysisResult> analyze(String rawLog) {
        List<ExceptionDetail> exceptions = logParser.parse(rawLog);
        log.info("Parsed {} exception(s) from log input ({} chars)",
                exceptions.size(), rawLog == null ? 0 : rawLog.length());
        return analyzeExceptions(exceptions);
    }

    @Override
    public List<AnalysisResult> analyzeExceptions(List<ExceptionDetail> exceptions) {
        List<CompletableFuture<AnalysisResult>> futures = exceptions.stream()
                .map(ex -> CompletableFuture.supplyAsync(() ->
                        AnalysisResult.builder()
                                .exception(ex)
                                .diagnosis(diagnoseQuietly(ex))
                                .build(),
                        aiExecutor))
                .toList();

        return futures.stream()
                .map(CompletableFuture::join)
                .toList();
    }

    private AIDiagnosisResult diagnoseQuietly(ExceptionDetail exception) {
        try {
            return aiDiagnosticService.diagnose(exception);
        } catch (Exception e) {
            log.warn("AI diagnosis failed for {}: {}", exception.getExceptionType(), e.getMessage());
            return null;
        }
    }
}
