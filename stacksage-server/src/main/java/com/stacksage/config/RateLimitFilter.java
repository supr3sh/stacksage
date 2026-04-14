package com.stacksage.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitConfig config;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    public RateLimitFilter(RateLimitConfig config, ObjectMapper objectMapper) {
        this.config = config;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator")
            || path.startsWith("/swagger")
            || path.startsWith("/v3/api-docs")
            || path.startsWith("/error")
            || path.startsWith("/api/v1/events");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!config.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientKey = resolveClientKey(request);
        TokenBucket bucket = buckets.computeIfAbsent(clientKey,
                k -> new TokenBucket(config.getMaxRequests(), config.getWindowSeconds()));

        if (bucket.tryConsume()) {
            response.setHeader("X-RateLimit-Limit", String.valueOf(config.getMaxRequests()));
            response.setHeader("X-RateLimit-Remaining", String.valueOf(bucket.getRemaining()));
            response.setHeader("X-RateLimit-Reset", String.valueOf(bucket.getSecondsUntilNextToken()));
            filterChain.doFilter(request, response);
        } else {
            long retryAfter = bucket.getSecondsUntilNextToken();
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setHeader("X-RateLimit-Limit", String.valueOf(config.getMaxRequests()));
            response.setHeader("X-RateLimit-Remaining", "0");
            response.setHeader("X-RateLimit-Reset", String.valueOf(retryAfter));
            response.setHeader("Retry-After", String.valueOf(retryAfter));

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("status", 429);
            body.put("error", "Rate limit exceeded. Try again later.");
            body.put("timestamp", LocalDateTime.now().toString());
            objectMapper.writeValue(response.getWriter(), body);
        }
    }

    @Scheduled(fixedDelayString = "#{${app.rate-limit.window-seconds:60} * 2 * 1000}")
    public void evictIdleBuckets() {
        if (!config.isEnabled()) {
            return;
        }
        long evictionThreshold = config.getWindowSeconds() * 2 * 1000L;
        int before = buckets.size();

        buckets.entrySet().removeIf(entry -> entry.getValue().isEvictable(evictionThreshold));

        int evicted = before - buckets.size();
        if (evicted > 0) {
            log.info("Evicted {} idle rate-limit buckets, {} remaining", evicted, buckets.size());
        }
    }

    int getBucketCount() {
        return buckets.size();
    }

    private String resolveClientKey(HttpServletRequest request) {
        if (config.isTrustProxy()) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }
}
