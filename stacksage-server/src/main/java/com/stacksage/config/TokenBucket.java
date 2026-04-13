package com.stacksage.config;

public class TokenBucket {

    private final int maxTokens;
    private final long windowMillis;
    private int tokens;
    private long lastRefillTime;
    private volatile long lastAccessTime;

    public TokenBucket(int maxTokens, int windowSeconds) {
        if (maxTokens < 1 || windowSeconds < 1) {
            throw new IllegalArgumentException("maxTokens and windowSeconds must be >= 1");
        }
        this.maxTokens = maxTokens;
        this.windowMillis = windowSeconds * 1000L;
        this.tokens = maxTokens;
        long now = System.currentTimeMillis();
        this.lastRefillTime = now;
        this.lastAccessTime = now;
    }

    public synchronized boolean tryConsume() {
        lastAccessTime = System.currentTimeMillis();
        refill();
        if (tokens > 0) {
            tokens--;
            return true;
        }
        return false;
    }

    public synchronized int getRemaining() {
        refill();
        return tokens;
    }

    /**
     * @return seconds until the next token becomes available, or 0 if tokens are available now.
     */
    public synchronized long getSecondsUntilNextToken() {
        refill();
        if (tokens > 0) {
            return 0;
        }
        long millisPerToken = windowMillis / maxTokens;
        long elapsed = System.currentTimeMillis() - lastRefillTime;
        long waitMillis = millisPerToken - elapsed;
        return Math.max(1, (waitMillis + 999) / 1000);
    }

    /**
     * @return true if the bucket has been idle longer than the eviction threshold
     *         AND has refilled to full capacity (no in-flight rate-limit state worth keeping).
     */
    public synchronized boolean isEvictable(long evictionThresholdMillis) {
        refill();
        long idle = System.currentTimeMillis() - lastAccessTime;
        return idle >= evictionThresholdMillis && tokens == maxTokens;
    }

    private void refill() {
        long now = System.currentTimeMillis();
        long elapsed = now - lastRefillTime;
        if (elapsed > 0) {
            long tokensToAdd = (elapsed * maxTokens) / windowMillis;
            if (tokensToAdd > 0) {
                tokens = (int) Math.min(maxTokens, tokens + tokensToAdd);
                lastRefillTime += (tokensToAdd * windowMillis) / maxTokens;
            }
        }
    }
}
