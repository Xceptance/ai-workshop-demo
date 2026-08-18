package com.workshop.agent.web;

import com.workshop.agent.engine.TransparentAgentEngine;
import com.workshop.agent.model.AgentSession;
import com.workshop.agent.model.ChatMessageDto;
import com.workshop.agent.tools.ToolRegistry;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class AgentApiController {

    private final TransparentAgentEngine agentEngine;
    private final ToolRegistry toolRegistry;

    public AgentApiController(TransparentAgentEngine agentEngine, ToolRegistry toolRegistry) {
        this.agentEngine = agentEngine;
        this.toolRegistry = toolRegistry;
    }

    public record ChatRequest(String sessionId, String prompt) {}

    @PostMapping("/chat")
    public ResponseEntity<ChatMessageDto> chat(@RequestBody ChatRequest request) {
        String sessionId = request.sessionId();
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = UUID.randomUUID().toString().substring(0, 8);
        }
        ChatMessageDto result = agentEngine.processTurn(sessionId, request.prompt());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/sessions/{sessionId}")
    public ResponseEntity<AgentSession> getSession(@PathVariable("sessionId") String sessionId) {
        AgentSession session = agentEngine.getOrCreateSession(sessionId);
        return ResponseEntity.ok(session);
    }

    @GetMapping("/tools")
    public ResponseEntity<List<Map<String, Object>>> listTools() {
        var tools = toolRegistry.getRegisteredTools().stream()
                .map(t -> Map.<String, Object>of(
                        "name", t.method().getName(),
                        "description", t.description(),
                        "parameters", t.specification() != null ? t.specification().parameters() : Map.of()
                ))
                .toList();
        return ResponseEntity.ok(tools);
    }
}
