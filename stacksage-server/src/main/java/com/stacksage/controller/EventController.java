package com.stacksage.controller;

import com.stacksage.service.SseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/events")
@Tag(name = "Events", description = "Server-Sent Events for real-time notifications")
public class EventController {

    private final SseService sseService;

    public EventController(SseService sseService) {
        this.sseService = sseService;
    }

    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Subscribe to server events",
            description = "SSE stream for file cleanup and analysis completion notifications. "
                    + "Returns a sessionId in the first event for use in X-Session-Id header on uploads.")
    public SseEmitter subscribe() {
        SseService.SseSession session = sseService.register();
        try {
            session.emitter().send(
                    SseEmitter.event()
                            .name("connected")
                            .data(Map.of("sessionId", session.sessionId())));
        } catch (IOException e) {
            // emitter will be cleaned up by its error/completion callbacks
        }
        return session.emitter();
    }
}
