package com.workshop.agent.mcp.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * Record of raw wire traffic exchanged between the MCP Client and MCP Servers.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record McpWireRecord(
        String id,
        Instant timestamp,
        String sessionId,
        String serverName,
        String transport,
        String direction,
        String method,
        Object messageId,
        String payloadJson,
        long latencyMs,
        boolean isSuccess,
        String errorMessage
) {
    public static McpWireRecord of(
            String sessionId,
            String serverName,
            String transport,
            String direction,
            String method,
            Object messageId,
            String payloadJson,
            long latencyMs,
            boolean isSuccess,
            String errorMessage
    ) {
        return new McpWireRecord(
                java.util.UUID.randomUUID().toString(),
                Instant.now(),
                sessionId,
                serverName,
                transport,
                direction,
                method,
                messageId,
                payloadJson,
                latencyMs,
                isSuccess,
                errorMessage
        );
    }
}
