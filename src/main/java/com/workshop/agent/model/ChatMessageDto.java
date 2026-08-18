package com.workshop.agent.model;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;
import java.util.List;

public record ChatMessageDto(
        String id,
        String role, // "user", "assistant", "system", "tool"
        String content,
        String formattedContent,
        List<AgentStepEvent> executionSteps,
        TokenUsageStats tokenStats,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Instant timestamp
) {
    public static ChatMessageDto user(String id, String content) {
        return new ChatMessageDto(id, "user", content, null, List.of(), TokenUsageStats.empty(), Instant.now());
    }

    public static ChatMessageDto assistant(String id, String content, String formattedContent, List<AgentStepEvent> steps, TokenUsageStats stats) {
        return new ChatMessageDto(id, "assistant", content, formattedContent, steps, stats, Instant.now());
    }
}
