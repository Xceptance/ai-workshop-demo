package com.workshop.agent.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workshop.agent.engine.TransparentAgentEngine;
import com.workshop.agent.model.AgentStepEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.function.Consumer;

@RestController
public class StreamSseController {

    private static final Logger log = LoggerFactory.getLogger(StreamSseController.class);

    private final TransparentAgentEngine agentEngine;
    private final com.workshop.agent.engine.McpAgentEngine mcpAgentEngine;
    private final ObjectMapper objectMapper;

    public StreamSseController(
            TransparentAgentEngine agentEngine,
            com.workshop.agent.engine.McpAgentEngine mcpAgentEngine,
            ObjectMapper objectMapper
    ) {
        this.agentEngine = agentEngine;
        this.mcpAgentEngine = mcpAgentEngine;
        this.objectMapper = objectMapper;
    }

    @GetMapping(value = "/api/stream/{sessionId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamSessionEvents(@PathVariable("sessionId") String sessionId) {
        // 30-minute timeout for workshop live debugging sessions
        SseEmitter emitter = new SseEmitter(1800_000L);

        Consumer<AgentStepEvent> listener = event -> {
            try {
                String json = objectMapper.writeValueAsString(event);
                emitter.send(SseEmitter.event()
                        .name("agent-step")
                        .data(json));
            } catch (IOException e) {
                log.debug("SSE client disconnected for session {}: {}", sessionId, e.getMessage());
            }
        };

        agentEngine.registerEventListener(sessionId, listener);

        Runnable cleanup = () -> {
            agentEngine.removeEventListener(sessionId, listener);
        };

        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> cleanup.run());

        try {
            // Send initial connection heartbeat
            emitter.send(SseEmitter.event().name("connected").data("{\"status\": \"connected\", \"sessionId\": \"" + sessionId + "\"}"));
        } catch (IOException ignored) {}

        return emitter;
    }
}
