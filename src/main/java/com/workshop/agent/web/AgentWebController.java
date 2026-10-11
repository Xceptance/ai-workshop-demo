package com.workshop.agent.web;

import com.workshop.agent.config.ApiKeyResolver;
import com.workshop.agent.config.GeminiProperties;
import com.workshop.agent.engine.TransparentAgentEngine;
import com.workshop.agent.logging.SessionDiskLogger;
import com.workshop.agent.mcp.model.McpServerConfig;
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
import java.util.Map;
import java.util.UUID;

/**
 * Web Controller serving the Glass-Box AI Mini-Agent interactive workshop UI.
 */
@Controller
public class AgentWebController {

    private final TransparentAgentEngine agentEngine;
    private final com.workshop.agent.engine.McpAgentEngine mcpAgentEngine;
    private final com.workshop.agent.engine.DynamicChatModelService dynamicChatModelService;
    private final com.workshop.agent.mcp.client.McpClientManager mcpClientManager;
    private final ToolRegistry toolRegistry;
    private final GeminiProperties geminiProperties;
    private final ApiKeyResolver.ResolvedKey resolvedKey;
    private final SessionDiskLogger sessionDiskLogger;
    private final MarkdownRenderer markdownRenderer;

    public AgentWebController(
            TransparentAgentEngine agentEngine,
            com.workshop.agent.engine.McpAgentEngine mcpAgentEngine,
            com.workshop.agent.engine.DynamicChatModelService dynamicChatModelService,
            com.workshop.agent.mcp.client.McpClientManager mcpClientManager,
            ToolRegistry toolRegistry,
            GeminiProperties geminiProperties,
            ApiKeyResolver.ResolvedKey resolvedKey,
            SessionDiskLogger sessionDiskLogger,
            MarkdownRenderer markdownRenderer
    ) {
        this.agentEngine = agentEngine;
        this.mcpAgentEngine = mcpAgentEngine;
        this.dynamicChatModelService = dynamicChatModelService;
        this.mcpClientManager = mcpClientManager;
        this.toolRegistry = toolRegistry;
        this.geminiProperties = geminiProperties;
        this.resolvedKey = resolvedKey;
        this.sessionDiskLogger = sessionDiskLogger;
        this.markdownRenderer = markdownRenderer;
    }

    @GetMapping("/")
    public String index(
            @RequestParam(value = "sessionId", required = false) String sessionId,
            @RequestParam(value = "mode", required = false) String mode,
            Model model
    ) {
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = UUID.randomUUID().toString().substring(0, 8);
        }

        AgentSession session = agentEngine.getOrCreateSession(sessionId);
        if (mode != null && !mode.isBlank()) {
            session.setMode(mode.trim().toLowerCase());
        }
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
            if ("mcp".equalsIgnoreCase(session.getMode())) {
                mcpAgentEngine.processTurn(sessionId, prompt.trim());
            } else {
                agentEngine.processTurn(sessionId, prompt.trim());
            }
        }

        populateModel(model, session);
        return "fragments/chat-panel :: chatContent";
    }

    @PostMapping("/session/{sessionId}/mode")
    public String switchMode(
            @PathVariable("sessionId") String sessionId,
            @RequestParam("mode") String mode,
            Model model
    ) {
        AgentSession session = agentEngine.getOrCreateSession(sessionId);
        session.setMode(mode);
        populateModel(model, session);
        return "redirect:/?sessionId=" + sessionId + "&mode=" + mode;
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

    @GetMapping("/api/mcp/servers")
    @ResponseBody
    public ResponseEntity<?> getMcpServers() {
        return ResponseEntity.ok(mcpClientManager.getServerConfigs());
    }

    @GetMapping("/api/mcp/traffic/{sessionId}")
    @ResponseBody
    public ResponseEntity<?> getMcpSessionTraffic(@PathVariable("sessionId") String sessionId) {
        return ResponseEntity.ok(mcpClientManager.getSessionTraffic(sessionId));
    }

    @PostMapping("/session/{sessionId}/settings")
    public String updateSettings(
            @PathVariable("sessionId") String sessionId,
            @RequestParam(value = "modelName", required = false) String modelName,
            @RequestParam(value = "customModelName", required = false) String customModelName,
            @RequestParam(value = "thinkingLevel", required = false) String thinkingLevel,
            @RequestParam(value = "showReasoning", required = false) Boolean showReasoning,
            Model model
    ) {
        AgentSession session = agentEngine.getOrCreateSession(sessionId);
        String selectedModel = null;
        if (modelName != null && !modelName.isBlank() && !modelName.equalsIgnoreCase("__custom__")) {
            selectedModel = modelName.trim();
        } else if (customModelName != null && !customModelName.isBlank() && !customModelName.equalsIgnoreCase("__custom__")) {
            selectedModel = customModelName.trim();
        } else {
            selectedModel = session.getModelName();
        }

        if (selectedModel != null && !selectedModel.isBlank()) {
            dynamicChatModelService.registerModel(selectedModel);
            session.setModelName(selectedModel);
        }

        if (thinkingLevel != null && !thinkingLevel.isBlank()) {
            session.setThinkingLevel(thinkingLevel.trim().toLowerCase());
        }

        if (showReasoning != null) {
            session.setShowReasoning(showReasoning);
        }

        populateModel(model, session);
        return "redirect:/?sessionId=" + sessionId + "&mode=" + session.getMode();
    }

    @PostMapping("/session/{sessionId}/toggle-reasoning")
    @ResponseBody
    public ResponseEntity<?> toggleReasoning(
            @PathVariable("sessionId") String sessionId,
            @RequestParam(value = "show", required = false) Boolean show
    ) {
        AgentSession session = agentEngine.getOrCreateSession(sessionId);
        boolean newState = (show != null) ? show : !session.isShowReasoning();
        session.setShowReasoning(newState);
        return ResponseEntity.ok(java.util.Map.of(
                "status", "success",
                "sessionId", sessionId,
                "showReasoning", session.isShowReasoning()
        ));
    }

    @PostMapping(value = "/api/session/{sessionId}/model")
    @ResponseBody
    public ResponseEntity<?> setModelApi(
            @PathVariable("sessionId") String sessionId,
            @RequestBody(required = false) java.util.Map<String, String> body,
            @RequestParam(value = "modelName", required = false) String paramModelName,
            @RequestParam(value = "thinkingLevel", required = false) String paramThinkingLevel
    ) {
        String effectiveModelName = (body != null && body.get("modelName") != null)
                ? body.get("modelName")
                : paramModelName;
        String effectiveThinkingLevel = (body != null && body.get("thinkingLevel") != null)
                ? body.get("thinkingLevel")
                : paramThinkingLevel;

        AgentSession session = agentEngine.getOrCreateSession(sessionId);
        if (effectiveModelName != null && !effectiveModelName.isBlank()) {
            String cleanName = dynamicChatModelService.registerModel(effectiveModelName);
            session.setModelName(cleanName);
        }
        if (effectiveThinkingLevel != null && !effectiveThinkingLevel.isBlank()) {
            session.setThinkingLevel(effectiveThinkingLevel.trim().toLowerCase());
        }
        return ResponseEntity.ok(java.util.Map.of(
                "status", "success",
                "sessionId", sessionId,
                "modelName", session.getModelName(),
                "thinkingLevel", session.getThinkingLevel(),
                "availableModels", dynamicChatModelService.getAvailableModels()
        ));
    }

    @PostMapping("/api/session/{sessionId}/tools/toggle")
    @ResponseBody
    public ResponseEntity<?> toggleTool(
            @PathVariable("sessionId") String sessionId,
            @RequestParam("toolName") String toolName,
            @RequestParam(value = "enabled", required = false) Boolean enabled
    ) {
        AgentSession session = agentEngine.getOrCreateSession(sessionId);
        boolean newState = (enabled != null) ? enabled : !session.isToolEnabled(toolName);
        session.setToolEnabled(toolName, newState);

        var toolDtos = toolRegistry.getToolDtos();
        long activeCount = toolDtos.stream().filter(t -> session.isToolEnabled(t.name())).count();

        return ResponseEntity.ok(Map.of(
                "status", "success",
                "sessionId", sessionId,
                "toolName", toolName,
                "enabled", newState,
                "activeCount", activeCount,
                "totalCount", toolDtos.size()
        ));
    }

    @PostMapping("/api/session/{sessionId}/mcp/servers/toggle")
    @ResponseBody
    public ResponseEntity<?> toggleMcpServer(
            @PathVariable("sessionId") String sessionId,
            @RequestParam("serverId") String serverId,
            @RequestParam(value = "enabled", required = false) Boolean enabled
    ) {
        AgentSession session = agentEngine.getOrCreateSession(sessionId);
        boolean newState = (enabled != null) ? enabled : !session.isMcpServerEnabled(serverId);
        session.setMcpServerEnabled(serverId, newState);

        mcpClientManager.refreshAllServers(sessionId, session.getDisabledMcpServers());
        var configs = mcpClientManager.getServerConfigs(session.getDisabledMcpServers());
        long activeServers = configs.stream().filter(McpServerConfig::isConnected).count();
        var specs = mcpClientManager.getDiscoveredToolSpecifications();
        long activeTools = specs.stream().filter(s -> session.isMcpToolEnabled(s.name())).count();

        return ResponseEntity.ok(Map.of(
                "status", "success",
                "sessionId", sessionId,
                "serverId", serverId,
                "enabled", newState,
                "activeServerCount", activeServers,
                "totalServerCount", configs.size(),
                "activeToolCount", activeTools,
                "totalToolCount", specs.size()
        ));
    }

    @PostMapping("/api/session/{sessionId}/mcp/tools/toggle")
    @ResponseBody
    public ResponseEntity<?> toggleMcpTool(
            @PathVariable("sessionId") String sessionId,
            @RequestParam("toolName") String toolName,
            @RequestParam(value = "enabled", required = false) Boolean enabled
    ) {
        AgentSession session = agentEngine.getOrCreateSession(sessionId);
        boolean newState = (enabled != null) ? enabled : !session.isMcpToolEnabled(toolName);
        session.setMcpToolEnabled(toolName, newState);

        var specs = mcpClientManager.getDiscoveredToolSpecifications();
        long activeTools = specs.stream().filter(s -> session.isMcpToolEnabled(s.name())).count();

        return ResponseEntity.ok(Map.of(
                "status", "success",
                "sessionId", sessionId,
                "toolName", toolName,
                "enabled", newState,
                "activeCount", activeTools,
                "totalCount", specs.size()
        ));
    }

    private void populateModel(Model model, AgentSession session) {
        var toolDtos = toolRegistry.getToolDtos();
        long activeTools = toolDtos.stream().filter(t -> session.isToolEnabled(t.name())).count();
        var serverConfigs = mcpClientManager.getServerConfigs(session.getDisabledMcpServers());
        long activeServers = serverConfigs.stream().filter(McpServerConfig::isConnected).count();
        var mcpToolSpecs = mcpClientManager.getDiscoveredToolSpecifications();
        long activeMcpTools = mcpToolSpecs.stream().filter(s -> session.isMcpToolEnabled(s.name())).count();

        model.addAttribute("agentSession", session);
        model.addAttribute("sessionId", session.getId());
        model.addAttribute("mode", session.getMode());
        model.addAttribute("messages", session.getMessages());
        model.addAttribute("stepEvents", session.getStepEvents());
        model.addAttribute("tokenStats", session.getTotalTokenStats());
        model.addAttribute("tools", toolDtos);
        model.addAttribute("activeToolCount", activeTools);
        model.addAttribute("totalToolCount", toolDtos.size());
        model.addAttribute("mcpServers", serverConfigs);
        model.addAttribute("activeMcpServerCount", activeServers);
        model.addAttribute("totalMcpServerCount", serverConfigs.size());
        model.addAttribute("mcpTools", mcpToolSpecs);
        model.addAttribute("activeMcpToolCount", activeMcpTools);
        model.addAttribute("totalMcpToolCount", mcpToolSpecs.size());
        model.addAttribute("mcpTraffic", mcpClientManager.getSessionTraffic(session.getId()));
        model.addAttribute("modelName", session.getModelName() != null ? session.getModelName() : geminiProperties.getModelName());
        model.addAttribute("thinkingLevel", session.getThinkingLevel() != null ? session.getThinkingLevel() : "medium");
        model.addAttribute("showReasoning", session.isShowReasoning());
        model.addAttribute("availableModels", dynamicChatModelService.getAvailableModels());
        model.addAttribute("apiKeyInfo", resolvedKey);
        model.addAttribute("recentSessions", sessionDiskLogger.listRecentSessionSummaries());
    }
}
