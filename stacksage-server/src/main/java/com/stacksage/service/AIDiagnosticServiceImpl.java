package com.stacksage.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stacksage.config.AIProviderConfig;
import com.stacksage.exception.AIDiagnosticException;
import com.stacksage.model.AIDiagnosisResult;
import com.stacksage.parser.ExceptionDetail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AIDiagnosticServiceImpl implements AIDiagnosticService {

    private static final Logger log = LoggerFactory.getLogger(AIDiagnosticServiceImpl.class);

    private static final int MAX_STACK_FRAMES = 10;
    private static final int MAX_MESSAGE_LENGTH = 500;
    private static final int MAX_TYPE_LENGTH = 200;
    private static final int MAX_RETRIES = 1;
    private static final int MAX_CACHE_SIZE = 500;
    private static final long CACHE_TTL_MS = 30 * 60 * 1000L;
    private static final long FAILURE_CACHE_TTL_MS = 5 * 60 * 1000L;
    private static final int CACHE_KEY_FRAMES = 3;

    private static final Set<String> VALID_SEVERITIES = Set.of("CRITICAL", "HIGH", "MEDIUM", "LOW");
    private static final Set<Integer> NON_RETRYABLE_STATUSES = Set.of(400, 401, 403);

    private static final String SYSTEM_PROMPT = """
            You are a senior Java backend engineer diagnosing production exceptions.
            Analyze the exception provided and respond ONLY with a valid JSON object \
            (no markdown fences, no text outside the JSON) containing exactly these fields:
            - "rootCause": a single sentence identifying the root cause
            - "explanation": 2-3 sentence technical explanation of why this happened
            - "suggestedFix": a concrete, code-level fix suggestion
            - "severity": one of "CRITICAL", "HIGH", "MEDIUM", "LOW"
            """;

    private static final Pattern JSON_FENCE = Pattern.compile(
            "```(?:json)?\\s*(\\{.*?})\\s*```", Pattern.DOTALL);

    private static final AIDiagnosisResult FAILURE_SENTINEL = AIDiagnosisResult.builder()
            .rootCause("__FAILURE__").build();

    private final RestClient restClient;
    private final AIProviderConfig config;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public AIDiagnosticServiceImpl(RestClient aiRestClient,
                                   AIProviderConfig config,
                                   ObjectMapper objectMapper) {
        this.restClient = aiRestClient;
        this.config = config;
        this.objectMapper = objectMapper;
    }

    @Override
    public AIDiagnosisResult diagnose(ExceptionDetail exception) {
        if (!config.isConfigured()) {
            log.warn("AI API key not configured — skipping AI diagnosis");
            return null;
        }

        String cacheKey = buildCacheKey(exception);

        CacheEntry entry = cache.get(cacheKey);
        if (entry != null && !entry.isExpired()) {
            log.debug("Cache hit for: {}", exception.getExceptionType());
            return entry.isFailure() ? null : entry.result;
        }

        String userMessage = buildUserMessage(exception);
        Map<String, Object> requestBody = buildRequestBody(userMessage);

        log.debug("Sending diagnosis request for: {}", exception.getExceptionType());
        try {
            String rawResponse = callWithRetry(requestBody);
            AIDiagnosisResult result = parseResponse(rawResponse);
            result = normalizeResult(result);
            putCache(cacheKey, new CacheEntry(result, CACHE_TTL_MS));
            return result;
        } catch (AIDiagnosticException e) {
            putCache(cacheKey, new CacheEntry(FAILURE_SENTINEL, FAILURE_CACHE_TTL_MS));
            throw e;
        }
    }

    int getCacheSize() {
        return cache.size();
    }

    @Scheduled(fixedDelay = 300_000)
    void evictExpiredCacheEntries() {
        int before = cache.size();
        cache.entrySet().removeIf(e -> e.getValue().isExpired());
        int evicted = before - cache.size();
        if (evicted > 0) {
            log.info("Evicted {} expired AI diagnosis cache entries, {} remaining", evicted, cache.size());
        }
    }

    String buildUserMessage(ExceptionDetail exception) {
        StringBuilder sb = new StringBuilder();
        sb.append("Exception: ").append(sanitize(truncate(exception.getExceptionType(), MAX_TYPE_LENGTH))).append('\n');

        if (exception.getMessage() != null && !exception.getMessage().isBlank()) {
            sb.append("Message: ").append(sanitize(truncate(exception.getMessage(), MAX_MESSAGE_LENGTH))).append('\n');
        }

        List<String> frames = exception.getStackTrace();
        if (frames != null && !frames.isEmpty()) {
            int limit = Math.min(frames.size(), MAX_STACK_FRAMES);
            sb.append("Stack Trace (top ").append(limit).append(" frames):\n");
            for (int i = 0; i < limit; i++) {
                sb.append("  ").append(sanitize(frames.get(i))).append('\n');
            }
            if (frames.size() > limit) {
                sb.append("  ... ").append(frames.size() - limit).append(" more frames\n");
            }
        }

        return sb.toString();
    }

    private String buildCacheKey(ExceptionDetail exception) {
        String type = Objects.toString(exception.getExceptionType(), "");
        String msg = Objects.toString(exception.getMessage(), "");
        List<String> frames = exception.getStackTrace();

        StringBuilder key = new StringBuilder(type).append('|').append(msg);
        if (frames != null) {
            int limit = Math.min(frames.size(), CACHE_KEY_FRAMES);
            for (int i = 0; i < limit; i++) {
                key.append('|').append(frames.get(i));
            }
        }
        return key.toString();
    }

    private void putCache(String key, CacheEntry entry) {
        if (cache.size() >= MAX_CACHE_SIZE) {
            cache.entrySet().removeIf(e -> e.getValue().isExpired());
        }
        if (cache.size() < MAX_CACHE_SIZE) {
            cache.put(key, entry);
        }
    }

    private String truncate(String input, int maxLen) {
        if (input == null) return "";
        if (input.length() <= maxLen) return input;
        return input.substring(0, maxLen) + "... (truncated)";
    }

    String sanitize(String input) {
        if (input == null) return "";
        return input
                .replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", "")
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }

    private Map<String, Object> buildRequestBody(String userMessage) {
        return Map.of(
                "model", config.getModel(),
                "max_tokens", config.getMaxTokens(),
                "temperature", config.getTemperature(),
                "messages", List.of(
                        Map.of("role", "system", "content", SYSTEM_PROMPT),
                        Map.of("role", "user", "content", userMessage)
                )
        );
    }

    private String callWithRetry(Map<String, Object> requestBody) {
        AIDiagnosticException lastException = null;
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                return callAIProvider(requestBody);
            } catch (AIDiagnosticException e) {
                lastException = e;
                if (!isRetryable(e) || attempt >= MAX_RETRIES) {
                    break;
                }
                log.warn("AI provider call failed (attempt {}), retrying: {}",
                        attempt + 1, e.getMessage());
                sleep(1000L * (attempt + 1));
            }
        }
        throw lastException;
    }

    private boolean isRetryable(AIDiagnosticException e) {
        Throwable cause = e.getCause();
        if (cause instanceof RestClientResponseException rcre) {
            return !NON_RETRYABLE_STATUSES.contains(rcre.getStatusCode().value());
        }
        return true;
    }

    private String callAIProvider(Map<String, Object> requestBody) {
        try {
            String responseJson = restClient.post()
                    .uri("/v1/chat/completions")
                    .body(requestBody)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        byte[] bodyBytes = res.getBody().readNBytes(8192);
                        String body = new String(bodyBytes, java.nio.charset.StandardCharsets.UTF_8);
                        int status = res.getStatusCode().value();
                        throw new RestClientResponseException(
                                "AI provider error " + status + ": " + body,
                                status, res.getStatusText(), res.getHeaders(), bodyBytes, null);
                    })
                    .body(String.class);

            if (responseJson == null || responseJson.isBlank()) {
                throw new AIDiagnosticException("AI provider returned empty response body");
            }

            JsonNode root = objectMapper.readTree(responseJson);

            logTokenUsage(root);

            JsonNode choices = root.path("choices");
            if (choices.isEmpty() || choices.isMissingNode()) {
                throw new AIDiagnosticException("AI provider returned no choices in response");
            }

            String content = choices.get(0).path("message").path("content").asText();
            if (content == null || content.isBlank()) {
                throw new AIDiagnosticException("AI provider returned empty content");
            }

            log.debug("Received AI response ({} chars)", content.length());
            return content;
        } catch (RestClientResponseException e) {
            throw new AIDiagnosticException(
                    "AI provider error (HTTP " + e.getStatusCode().value() + ")", e);
        } catch (RestClientException e) {
            throw new AIDiagnosticException("Failed to call AI provider", e);
        } catch (Exception e) {
            if (e instanceof AIDiagnosticException ade) throw ade;
            throw new AIDiagnosticException("Unexpected error calling AI provider", e);
        }
    }

    private void logTokenUsage(JsonNode root) {
        JsonNode usage = root.path("usage");
        if (!usage.isMissingNode()) {
            log.debug("AI token usage — prompt: {}, completion: {}, total: {}",
                    usage.path("prompt_tokens").asInt(),
                    usage.path("completion_tokens").asInt(),
                    usage.path("total_tokens").asInt());
        }
    }

    AIDiagnosisResult parseResponse(String rawContent) {
        String json = extractJson(rawContent.trim());
        try {
            return objectMapper.readValue(json, AIDiagnosisResult.class);
        } catch (JsonProcessingException e) {
            throw new AIDiagnosticException(
                    "Failed to parse AI diagnosis from response: " + json, e);
        }
    }

    private AIDiagnosisResult normalizeResult(AIDiagnosisResult result) {
        return AIDiagnosisResult.builder()
                .rootCause(defaultIfBlank(result.getRootCause(), "Unable to determine root cause"))
                .explanation(defaultIfBlank(result.getExplanation(), "No explanation available"))
                .suggestedFix(defaultIfBlank(result.getSuggestedFix(), "No fix suggested"))
                .severity(normalizeSeverity(result.getSeverity()))
                .build();
    }

    private String normalizeSeverity(String severity) {
        if (severity == null) return "MEDIUM";
        String upper = severity.trim().toUpperCase();
        return VALID_SEVERITIES.contains(upper) ? upper : "MEDIUM";
    }

    private String defaultIfBlank(String value, String fallback) {
        return (value != null && !value.isBlank()) ? value : fallback;
    }

    String extractJson(String content) {
        Matcher fenceMatcher = JSON_FENCE.matcher(content);
        if (fenceMatcher.find()) {
            return fenceMatcher.group(1);
        }

        int start = content.indexOf('{');
        if (start == -1) return content;

        int depth = 0;
        for (int i = start; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') depth--;
            if (depth == 0) return content.substring(start, i + 1);
        }

        return content;
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AIDiagnosticException("Retry interrupted", e);
        }
    }

    private static class CacheEntry {
        final AIDiagnosisResult result;
        final long expiresAt;

        CacheEntry(AIDiagnosisResult result, long ttlMs) {
            this.result = result;
            this.expiresAt = System.currentTimeMillis() + ttlMs;
        }

        boolean isExpired() {
            return System.currentTimeMillis() >= expiresAt;
        }

        boolean isFailure() {
            return result == FAILURE_SENTINEL;
        }
    }
}
