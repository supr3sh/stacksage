package com.stacksage.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SseService {

    private static final Logger log = LoggerFactory.getLogger(SseService.class);
    private static final long EMITTER_TIMEOUT = 5 * 60 * 1000L;
    private static final int MAX_LINKS = 10_000;
    private static final long LINK_TTL_MS = 24 * 60 * 60 * 1000L;

    private final ConcurrentHashMap<String, SseEmitter> sessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LinkEntry> uploadToSession = new ConcurrentHashMap<>();

    public record SseSession(String sessionId, SseEmitter emitter) {}

    public SseSession register() {
        String sessionId = UUID.randomUUID().toString();
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT);
        sessions.put(sessionId, emitter);

        Runnable cleanup = () -> {
            sessions.remove(sessionId);
            uploadToSession.entrySet().removeIf(e -> sessionId.equals(e.getValue().sessionId()));
        };
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> cleanup.run());

        log.debug("SSE session registered: {}, {} active", sessionId, sessions.size());
        return new SseSession(sessionId, emitter);
    }

    public void link(String uploadId, String sessionId) {
        if (sessionId == null || !sessions.containsKey(sessionId)) {
            return;
        }
        if (uploadToSession.size() >= MAX_LINKS) {
            log.warn("Upload-to-session link map at capacity ({}), skipping link for upload {}",
                    MAX_LINKS, uploadId);
            return;
        }
        uploadToSession.put(uploadId, new LinkEntry(sessionId, System.currentTimeMillis()));
    }

    public void publish(String eventName, String uploadId, Map<String, Object> data) {
        if (uploadId == null) {
            return;
        }
        LinkEntry link = uploadToSession.get(uploadId);
        if (link == null) {
            return;
        }
        SseEmitter emitter = sessions.get(link.sessionId());
        if (emitter == null) {
            return;
        }
        try {
            emitter.send(SseEmitter.event().name(eventName).data(data));
        } catch (IOException e) {
            sessions.remove(link.sessionId());
        }
    }

    @Scheduled(fixedDelay = 30_000)
    public void heartbeat() {
        for (var entry : sessions.entrySet()) {
            try {
                entry.getValue().send(SseEmitter.event().comment("heartbeat"));
            } catch (IOException e) {
                sessions.remove(entry.getKey());
            }
        }
    }

    @Scheduled(fixedDelay = 300_000)
    public void evictExpiredLinks() {
        long now = System.currentTimeMillis();
        int before = uploadToSession.size();
        uploadToSession.entrySet().removeIf(e -> (now - e.getValue().linkedAt()) > LINK_TTL_MS);
        int evicted = before - uploadToSession.size();
        if (evicted > 0) {
            log.info("Evicted {} expired upload-session links, {} remaining",
                    evicted, uploadToSession.size());
        }
    }

    int getConnectionCount() {
        return sessions.size();
    }

    int getLinkCount() {
        return uploadToSession.size();
    }

    private record LinkEntry(String sessionId, long linkedAt) {}
}
