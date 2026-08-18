package com.workshop.agent.web;

import com.workshop.agent.config.ApiKeyResolver;
import com.workshop.agent.config.GeminiProperties;
import com.workshop.agent.engine.TransparentAgentEngine;
import com.workshop.agent.logging.SessionDiskLogger;
import com.workshop.agent.model.AgentSession;
import com.workshop.agent.model.ChatMessageDto;
import com.workshop.agent.tools.ToolRegistry;
import com.workshop.agent.util.MarkdownRenderer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Web Controller serving the Glass-Box AI Mini-Agent interactive workshop UI.
 */
@Controller
public class AgentWebController {

    private final TransparentAgentEngine agentEngine;
    private final ToolRegistry toolRegistry;
    private final GeminiProperties geminiProperties;
    private final ApiKeyResolver.ResolvedKey resolvedKey;
    private final SessionDiskLogger sessionDiskLogger;
    private final MarkdownRenderer markdownRenderer;

    public AgentWebController(
            TransparentAgentEngine agentEngine,
            ToolRegistry toolRegistry,
            GeminiProperties geminiProperties,
            ApiKeyResolver.ResolvedKey resolvedKey,
            SessionDiskLogger sessionDiskLogger,
            MarkdownRenderer markdownRenderer
    ) {
        this.agentEngine = agentEngine;
        this.toolRegistry = toolRegistry;
        this.geminiProperties = geminiProperties;
        this.resolvedKey = resolvedKey;
        this.sessionDiskLogger = sessionDiskLogger;
        this.markdownRenderer = markdownRenderer;
    }

    @GetMapping("/")
    public String index(@RequestParam(value = "sessionId", required = false) String sessionId, Model model) {
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = UUID.randomUUID().toString().substring(0, 8);
        }

        AgentSession session = agentEngine.getOrCreateSession(sessionId);
        populateModel(model, session);
        return "index";
    }

    @PostMapping("/chat")
    public String chat(
            @RequestParam("sessionId") String sessionId,
            @RequestParam("prompt") String prompt,
            Model model
    ) {
        AgentSession session = agentEngine.getOrCreateSession(sessionId);
        if (prompt != null && !prompt.isBlank()) {
            agentEngine.processTurn(sessionId, prompt.trim());
        }

        populateModel(model, session);
        return "fragments/chat-panel :: chatContent";
    }

    @PostMapping("/session/clear")
    public String clearSession(@RequestParam("sessionId") String sessionId, Model model) {
        AgentSession session = agentEngine.getOrCreateSession(sessionId);
        session.clearHistory();
        populateModel(model, session);
        return "fragments/chat-panel :: chatContent";
    }

    @GetMapping("/session/{sessionId}/timeline")
    public String getTimeline(@PathVariable("sessionId") String sessionId, Model model) {
        AgentSession session = agentEngine.getOrCreateSession(sessionId);
        model.addAttribute("session", session);
        model.addAttribute("stepEvents", session.getStepEvents());
        return "fragments/execution-step :: stepList";
    }

    @GetMapping("/session/{sessionId}/token-meter")
    public String getTokenMeter(@PathVariable("sessionId") String sessionId, Model model) {
        AgentSession session = agentEngine.getOrCreateSession(sessionId);
        model.addAttribute("tokenStats", session.getTotalTokenStats());
        return "fragments/token-meter :: meter";
    }

    @GetMapping("/session/{sessionId}/transcript")
    @ResponseBody
    public ResponseEntity<String> getTranscript(@PathVariable("sessionId") String sessionId) {
        Path sessionDir = sessionDiskLogger.getSessionDir(sessionId);
        Path transcriptPath = sessionDir.resolve("transcript.md");

        if (Files.exists(transcriptPath)) {
            try {
                String content = Files.readString(transcriptPath);
                return ResponseEntity.ok()
                        .contentType(MediaType.TEXT_MARKDOWN)
                        .body(content);
            } catch (IOException ignored) {}
        }

        AgentSession session = agentEngine.getOrCreateSession(sessionId);
        return ResponseEntity.ok("Transcript not yet written for session " + sessionId);
    }

    @GetMapping("/session/{sessionId}/transcript/view")
    public String viewTranscriptHtml(@PathVariable("sessionId") String sessionId, Model model) {
        Path sessionDir = sessionDiskLogger.getSessionDir(sessionId);
        Path transcriptPath = sessionDir.resolve("transcript.md");

        String md = "";
        if (Files.exists(transcriptPath)) {
            try {
                md = Files.readString(transcriptPath);
            } catch (IOException ignored) {}
        }

        model.addAttribute("sessionId", sessionId);
        model.addAttribute("transcriptHtml", markdownRenderer.renderHtml(md));
        return "fragments/log-viewer-modal :: logContent";
    }

    private void populateModel(Model model, AgentSession session) {
        model.addAttribute("agentSession", session);
        model.addAttribute("sessionId", session.getId());
        model.addAttribute("messages", session.getMessages());
        model.addAttribute("stepEvents", session.getStepEvents());
        model.addAttribute("tokenStats", session.getTotalTokenStats());
        model.addAttribute("tools", toolRegistry.getToolDtos());
        model.addAttribute("modelName", geminiProperties.getModelName());
        model.addAttribute("apiKeyInfo", resolvedKey);
        model.addAttribute("recentSessions", sessionDiskLogger.listRecentSessionSummaries());
    }
}
