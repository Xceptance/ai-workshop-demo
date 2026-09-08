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
    private final String modelName;
    private final String systemPrompt;
    private final double temperature;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private final Instant createdAt;

    private final List<ChatMessageDto> messages = new CopyOnWriteArrayList<>();
    private final List<AgentStepEvent> stepEvents = new CopyOnWriteArrayList<>();
    private TokenUsageStats totalTokenStats = TokenUsageStats.empty();

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
}
