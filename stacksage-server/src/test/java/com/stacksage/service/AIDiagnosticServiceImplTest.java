package com.stacksage.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stacksage.config.AIProviderConfig;
import com.stacksage.exception.AIDiagnosticException;
import com.stacksage.model.AIDiagnosisResult;
import com.stacksage.parser.ExceptionDetail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AIDiagnosticServiceImplTest {

    private AIProviderConfig config;
    private ObjectMapper objectMapper;
    private AIDiagnosticServiceImpl service;

    @BeforeEach
    void setUp() {
        config = new AIProviderConfig();
        config.setApiKey("test-key");
        config.setModel("meta-llama/llama-3.3-70b-instruct:free");
        config.setMaxTokens(1024);
        config.setTemperature(0.3);
        objectMapper = new ObjectMapper();
        service = new AIDiagnosticServiceImpl(config.aiRestClient(), config, objectMapper);
    }

    @Test
    void diagnose_returnsNull_whenApiKeyNotConfigured() {
        config.setApiKey("");
        AIDiagnosisResult result = service.diagnose(sampleException());
        assertThat(result).isNull();
    }

    // --- buildUserMessage tests ---

    @Test
    void buildUserMessage_containsExceptionDetails() {
        ExceptionDetail ex = ExceptionDetail.builder()
                .exceptionType("java.lang.NullPointerException")
                .message("Cannot invoke \"String.length()\"")
                .stackTrace(List.of(
                        "at com.example.Service.process(Service.java:42)",
                        "at com.example.Controller.handle(Controller.java:18)"
                ))
                .build();

        String message = service.buildUserMessage(ex);

        assertThat(message).contains("java.lang.NullPointerException");
        assertThat(message).contains("String.length()");
        assertThat(message).contains("Service.java:42");
        assertThat(message).contains("top 2 frames");
    }

    @Test
    void buildUserMessage_truncatesLongStackTraces() {
        List<String> frames = new java.util.ArrayList<>();
        for (int i = 0; i < 25; i++) {
            frames.add("at com.example.Class.method" + i + "(Class.java:" + i + ")");
        }
        ExceptionDetail ex = ExceptionDetail.builder()
                .exceptionType("java.lang.RuntimeException")
                .message("test")
                .stackTrace(frames)
                .build();

        String message = service.buildUserMessage(ex);

        assertThat(message).contains("top 10 frames");
        assertThat(message).contains("15 more frames");
        assertThat(message).doesNotContain("method15");
    }

    @Test
    void buildUserMessage_truncatesLongMessages() {
        String longMessage = "A".repeat(1000);
        ExceptionDetail ex = ExceptionDetail.builder()
                .exceptionType("java.lang.RuntimeException")
                .message(longMessage)
                .stackTrace(List.of())
                .build();

        String message = service.buildUserMessage(ex);

        assertThat(message).contains("(truncated)");
        assertThat(message.length()).isLessThan(1000);
    }

    @Test
    void buildUserMessage_truncatesLongExceptionType() {
        String longType = "com.example." + "A".repeat(300);
        ExceptionDetail ex = ExceptionDetail.builder()
                .exceptionType(longType)
                .message("test")
                .stackTrace(List.of())
                .build();

        String message = service.buildUserMessage(ex);

        assertThat(message).contains("(truncated)");
        assertThat(message).doesNotContain(longType);
    }

    @Test
    void buildUserMessage_sanitizesControlCharacters() {
        ExceptionDetail ex = ExceptionDetail.builder()
                .exceptionType("java.lang.RuntimeException")
                .message("bad\u0000input\u0007here")
                .stackTrace(List.of())
                .build();

        String message = service.buildUserMessage(ex);

        assertThat(message).doesNotContain("\u0000");
        assertThat(message).doesNotContain("\u0007");
        assertThat(message).contains("badinputhere");
    }

    @Test
    void buildUserMessage_handlesNullMessage() {
        ExceptionDetail ex = ExceptionDetail.builder()
                .exceptionType("java.lang.StackOverflowError")
                .message(null)
                .stackTrace(List.of("at com.example.Recursive.call(Recursive.java:10)"))
                .build();

        String message = service.buildUserMessage(ex);

        assertThat(message).contains("java.lang.StackOverflowError");
        assertThat(message).doesNotContain("Message:");
    }

    @Test
    void buildUserMessage_handlesEmptyStackTrace() {
        ExceptionDetail ex = ExceptionDetail.builder()
                .exceptionType("java.lang.RuntimeException")
                .message("no stack")
                .stackTrace(List.of())
                .build();

        String message = service.buildUserMessage(ex);

        assertThat(message).contains("RuntimeException");
        assertThat(message).doesNotContain("Stack Trace");
    }

    // --- sanitize tests ---

    @Test
    void sanitize_removesControlCharsThenEscapes() {
        String result = service.sanitize("a\"b\\c\u0001d");
        assertThat(result).isEqualTo("a\\\"b\\\\cd");
        assertThat(result).doesNotContain("\u0001");
    }

    @Test
    void sanitize_handlesNull() {
        assertThat(service.sanitize(null)).isEmpty();
    }

    // --- parseResponse tests ---

    @Test
    void parseResponse_parsesValidJson() {
        String json = """
                {
                  "rootCause": "Null reference passed to method",
                  "explanation": "The variable was not initialized before use.",
                  "suggestedFix": "Add a null check before calling the method.",
                  "severity": "HIGH"
                }
                """;

        AIDiagnosisResult result = service.parseResponse(json);

        assertThat(result.getRootCause()).isEqualTo("Null reference passed to method");
        assertThat(result.getExplanation()).contains("not initialized");
        assertThat(result.getSuggestedFix()).contains("null check");
        assertThat(result.getSeverity()).isEqualTo("HIGH");
    }

    @Test
    void parseResponse_stripsMarkdownFences() {
        String fenced = """
                ```json
                {
                  "rootCause": "Connection timeout",
                  "explanation": "The database is unreachable.",
                  "suggestedFix": "Check DB connection config.",
                  "severity": "CRITICAL"
                }
                ```
                """;

        AIDiagnosisResult result = service.parseResponse(fenced);

        assertThat(result.getRootCause()).isEqualTo("Connection timeout");
        assertThat(result.getSeverity()).isEqualTo("CRITICAL");
    }

    @Test
    void parseResponse_throwsOnInvalidJson() {
        assertThatThrownBy(() -> service.parseResponse("not json at all"))
                .isInstanceOf(AIDiagnosticException.class)
                .hasMessageContaining("Failed to parse AI diagnosis");
    }

    @Test
    void parseResponse_ignoresUnknownFields() {
        String json = """
                {
                  "rootCause": "OOM",
                  "explanation": "Heap exhausted.",
                  "suggestedFix": "Increase heap size.",
                  "severity": "CRITICAL",
                  "extraField": "should be ignored"
                }
                """;

        AIDiagnosisResult result = service.parseResponse(json);
        assertThat(result.getRootCause()).isEqualTo("OOM");
    }

    // --- extractJson tests ---

    @Test
    void extractJson_handlesTextBeforeJson() {
        String input = "Here is the analysis: {\"rootCause\":\"NPE\",\"explanation\":\"null\",\"suggestedFix\":\"fix\",\"severity\":\"HIGH\"}";
        AIDiagnosisResult result = service.parseResponse(input);
        assertThat(result.getRootCause()).isEqualTo("NPE");
    }

    @Test
    void extractJson_handlesTextAfterJson() {
        String input = "{\"rootCause\":\"NPE\",\"explanation\":\"null\",\"suggestedFix\":\"fix\",\"severity\":\"HIGH\"} Hope this helps!";
        AIDiagnosisResult result = service.parseResponse(input);
        assertThat(result.getRootCause()).isEqualTo("NPE");
    }

    @Test
    void extractJson_prefersMarkdownFenceOverBareJson() {
        String input = """
                Some text ```json
                {"rootCause":"from fence","explanation":"x","suggestedFix":"y","severity":"LOW"}
                ``` more text {"rootCause":"bare"}
                """;

        String extracted = service.extractJson(input.trim());
        assertThat(extracted).contains("from fence");
    }

    @Test
    void extractJson_usesNonGreedyMatch_stopsAtFirstClosingBrace() {
        String input = "{\"rootCause\":\"a\"} some text {\"rootCause\":\"b\"}";
        String extracted = service.extractJson(input);
        assertThat(extracted).isEqualTo("{\"rootCause\":\"a\"}");
    }

    // --- severity normalization ---

    @Test
    void normalizeResult_defaultsSeverityForInvalidValue() {
        String json = """
                {"rootCause":"test","explanation":"test","suggestedFix":"test","severity":"MODERATE"}
                """;
        AIDiagnosisResult raw = service.parseResponse(json);
        assertThat(raw.getSeverity()).isEqualTo("MODERATE");
    }

    @Test
    void normalizeResult_fillsNullFieldsWithDefaults() {
        String json = """
                {"rootCause":null,"explanation":"","suggestedFix":null,"severity":null}
                """;
        AIDiagnosisResult raw = service.parseResponse(json);
        assertThat(raw.getRootCause()).isNull();
        assertThat(raw.getSeverity()).isNull();
    }

    // --- cache eviction scheduled test ---

    @Test
    void evictExpiredCacheEntries_doesNotThrowOnEmptyCache() {
        service.evictExpiredCacheEntries();
        assertThat(service.getCacheSize()).isZero();
    }

    private ExceptionDetail sampleException() {
        return ExceptionDetail.builder()
                .exceptionType("java.lang.NullPointerException")
                .message("test")
                .stackTrace(List.of("at com.example.Main.run(Main.java:10)"))
                .build();
    }
}
