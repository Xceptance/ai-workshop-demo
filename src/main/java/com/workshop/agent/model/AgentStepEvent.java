package com.workshop.agent.model;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * An individual event emitted during an agent execution cycle.
 * Contains detailed payload information to make agent execution completely transparent.
 */
public record AgentStepEvent(
        String eventId,
        String sessionId,
        int stepNumber,
        StepType type,
        String title,
        String summary,
        String rawContent,
        String requestPayload,
        String responsePayload,
        String formattedHtml,
        TokenUsageStats tokenStats,
        Map<String, Object> metadata,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Instant timestamp
) {
    public static AgentStepEvent of(
            String sessionId,
            int stepNumber,
            StepType type,
            String title,
            String summary,
            String rawContent,
            TokenUsageStats tokenStats,
            Map<String, Object> metadata
    ) {
        return of(sessionId, stepNumber, type, title, summary, rawContent, null, null, tokenStats, metadata);
    }

    public static AgentStepEvent of(
            String sessionId,
            int stepNumber,
            StepType type,
            String title,
            String summary,
            String rawContent,
            String requestPayload,
            String responsePayload,
            TokenUsageStats tokenStats,
            Map<String, Object> metadata
    ) {
        return new AgentStepEvent(
                UUID.randomUUID().toString(),
                sessionId,
                stepNumber,
                type,
                title,
                summary,
                rawContent,
                requestPayload,
                responsePayload,
                null,
                tokenStats != null ? tokenStats : TokenUsageStats.empty(),
                metadata != null ? metadata : Map.of(),
                Instant.now()
        );
    }
}
