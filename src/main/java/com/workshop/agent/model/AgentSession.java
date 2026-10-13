package com.workshop.agent.model;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manages in-memory state and history for an active agent conversation session.
 */
public class AgentSession {

    private final String id;
    private String title;
    private String mode = "classic"; // "classic" or "mcp"
    private String modelName;
    private String thinkingLevel = "medium"; // "off", "low", "medium", "high", "dynamic"
    private boolean showReasoning = true; // Toggle to expose model reasoning / internal thoughts in execution inspector
    private final String systemPrompt;
    private final double temperature;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private final Instant createdAt;

    private final List<ChatMessageDto> messages = new CopyOnWriteArrayList<>();
    private final List<AgentStepEvent> stepEvents = new CopyOnWriteArrayList<>();
    private TokenUsageStats totalTokenStats = TokenUsageStats.empty();

    public static final java.util.Set<String> DEFAULT_DISABLED_MCP_SERVERS = java.util.Set.of("random-names-server");

    // Enabled/Disabled state tracking (per-session isolation)
    private final java.util.Set<String> disabledTools = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final java.util.Set<String> disabledMcpServers = initDefaultDisabledMcpServers();
    private final java.util.Set<String> disabledMcpTools = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private static java.util.Set<String> initDefaultDisabledMcpServers() {
        var set = java.util.concurrent.ConcurrentHashMap.<String>newKeySet();
        set.addAll(DEFAULT_DISABLED_MCP_SERVERS);
        return set;
    }

    public AgentSession(String id, String title, String modelName, String systemPrompt, double temperature) {
        this.id = id;
        this.title = title;
        this.modelName = modelName;
        this.systemPrompt = systemPrompt;
        this.temperature = temperature;
        this.createdAt = Instant.now();
    }

    public AgentSession(String id, String title, String mode, String modelName, String systemPrompt, double temperature) {
        this.id = id;
        this.title = title;
        this.mode = mode != null ? mode : "classic";
        this.modelName = modelName;
        this.systemPrompt = systemPrompt;
        this.temperature = temperature;
        this.createdAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public String id() {
        return id;
    }

    public String title() {
        return title;
    }

    public String modelName() {
        return modelName;
    }

    public String systemPrompt() {
        return systemPrompt;
    }

    public double temperature() {
        return temperature;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public List<ChatMessageDto> messages() {
        return getMessages();
    }

    public List<AgentStepEvent> stepEvents() {
        return getStepEvents();
    }

    public TokenUsageStats totalTokenStats() {
        return getTotalTokenStats();
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getMode() {
        return mode;
    }

    public String mode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode != null ? mode : "classic";
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        if (modelName != null && !modelName.isBlank()) {
            this.modelName = modelName.trim();
        }
    }

    public String getThinkingLevel() {
        return thinkingLevel;
    }

    public void setThinkingLevel(String thinkingLevel) {
        this.thinkingLevel = thinkingLevel != null ? thinkingLevel.trim().toLowerCase() : "medium";
    }

    public boolean isShowReasoning() {
        return showReasoning;
    }

    public boolean showReasoning() {
        return showReasoning;
    }

    public void setShowReasoning(boolean showReasoning) {
        this.showReasoning = showReasoning;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public double getTemperature() {
        return temperature;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<ChatMessageDto> getMessages() {
        return Collections.unmodifiableList(messages);
    }

    public void addMessage(ChatMessageDto message) {
        this.messages.add(message);
    }

    public List<AgentStepEvent> getStepEvents() {
        return Collections.unmodifiableList(stepEvents);
    }

    public void addStepEvent(AgentStepEvent event) {
        this.stepEvents.add(event);
        if (event.tokenStats() != null) {
            this.totalTokenStats = this.totalTokenStats.add(event.tokenStats());
        }
    }

    public void clearStepEvents() {
        this.stepEvents.clear();
    }

    public TokenUsageStats getTotalTokenStats() {
        return totalTokenStats;
    }

    public void clearHistory() {
        this.messages.clear();
        this.stepEvents.clear();
        this.totalTokenStats = TokenUsageStats.empty();
    }

    public boolean isToolEnabled(String toolName) {
        if (toolName == null) return false;
        return !disabledTools.contains(toolName.trim());
    }

    public void setToolEnabled(String toolName, boolean enabled) {
        if (toolName == null || toolName.isBlank()) return;
        String name = toolName.trim();
        if (enabled) {
            disabledTools.remove(name);
        } else {
            disabledTools.add(name);
        }
    }

    public boolean isMcpServerEnabled(String serverId) {
        if (serverId == null) return false;
        return !disabledMcpServers.contains(serverId.trim());
    }

    public void setMcpServerEnabled(String serverId, boolean enabled) {
        if (serverId == null || serverId.isBlank()) return;
        String id = serverId.trim();
        if (enabled) {
            disabledMcpServers.remove(id);
        } else {
            disabledMcpServers.add(id);
        }
    }

    public boolean isMcpToolEnabled(String toolName) {
        if (toolName == null) return false;
        return !disabledMcpTools.contains(toolName.trim());
    }

    public void setMcpToolEnabled(String toolName, boolean enabled) {
        if (toolName == null || toolName.isBlank()) return;
        String name = toolName.trim();
        if (enabled) {
            disabledMcpTools.remove(name);
        } else {
            disabledMcpTools.add(name);
        }
    }

    public java.util.Set<String> getDisabledTools() {
        return Collections.unmodifiableSet(new java.util.HashSet<>(disabledTools));
    }

    public java.util.Set<String> getDisabledMcpServers() {
        return Collections.unmodifiableSet(new java.util.HashSet<>(disabledMcpServers));
    }

    public java.util.Set<String> getDisabledMcpTools() {
        return Collections.unmodifiableSet(new java.util.HashSet<>(disabledMcpTools));
    }
}

