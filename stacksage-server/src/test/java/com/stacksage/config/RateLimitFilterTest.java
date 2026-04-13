package com.stacksage.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "app.rate-limit.enabled=true",
        "app.rate-limit.max-requests=3",
        "app.rate-limit.window-seconds=60"
})
class RateLimitFilterTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void rateLimitExceeded_returns429() throws Exception {
        // Use a non-existent endpoint path to keep requests lightweight (404 still counts)
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/api/v1/uploads/rate-test-" + i))
                    .andExpect(status().isNotFound());
        }

        // 4th request should be throttled
        mockMvc.perform(get("/api/v1/uploads/rate-test-blocked"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status", is(429)))
                .andExpect(jsonPath("$.error", is("Rate limit exceeded. Try again later.")))
                .andExpect(header().exists("Retry-After"))
                .andExpect(header().string("X-RateLimit-Limit", "3"))
                .andExpect(header().string("X-RateLimit-Remaining", "0"))
                .andExpect(header().exists("X-RateLimit-Reset"));
    }

    @Test
    void tokenBucket_notEvictable_whenRecentlyCreated() {
        TokenBucket bucket = new TokenBucket(5, 1);
        assertThat(bucket.isEvictable(2000)).isFalse();
    }

    @Test
    void tokenBucket_notEvictable_whenTokensConsumed() {
        TokenBucket bucket = new TokenBucket(5, 1);
        bucket.tryConsume();
        // Even with threshold=0, a partially consumed bucket should not be evicted
        assertThat(bucket.isEvictable(0)).isFalse();
    }

    @Test
    void tokenBucket_evictable_whenIdleAndFull() throws InterruptedException {
        TokenBucket bucket = new TokenBucket(5, 1);
        // Wait just enough for the bucket to be considered idle (threshold = 50ms)
        Thread.sleep(60);
        assertThat(bucket.isEvictable(50)).isTrue();
    }

    @Test
    void tokenBucket_gradualRefill() throws InterruptedException {
        // 10 tokens over 1 second = 1 token every 100ms
        TokenBucket bucket = new TokenBucket(10, 1);

        // Drain all tokens
        for (int i = 0; i < 10; i++) {
            assertThat(bucket.tryConsume()).isTrue();
        }
        assertThat(bucket.tryConsume()).isFalse();

        // Wait ~350ms — should recover ~3 tokens (not all 10)
        Thread.sleep(350);
        assertThat(bucket.getRemaining()).isBetween(2, 5);
    }

    @Test
    void tokenBucket_secondsUntilNextToken_zeroWhenAvailable() {
        TokenBucket bucket = new TokenBucket(5, 1);
        assertThat(bucket.getSecondsUntilNextToken()).isZero();
    }

    @Test
    void tokenBucket_secondsUntilNextToken_positiveWhenDrained() {
        TokenBucket bucket = new TokenBucket(5, 60);
        for (int i = 0; i < 5; i++) {
            bucket.tryConsume();
        }
        assertThat(bucket.getSecondsUntilNextToken()).isGreaterThan(0);
    }

    @Test
    void tokenBucket_neverExceedsMax() throws InterruptedException {
        TokenBucket bucket = new TokenBucket(5, 1);
        // Let it sit well beyond the full window
        Thread.sleep(1200);
        assertThat(bucket.getRemaining()).isEqualTo(5);
    }
}
