package com.workshop.agent.mcp.model;

import java.util.List;

/**
 * Metadata and operational status of a configured MCP Server.
 */
public record McpServerConfig(
        String id,
        String name,
        String description,
        String transport,
        String endpoint,
        String status,
        int toolCount,
        List<String> toolNames,
        long lastLatencyMs,
        String diagnosticNote,
        boolean isEnabled
) {
    public McpServerConfig(
            String id,
            String name,
            String description,
            String transport,
            String endpoint,
            String status,
            int toolCount,
            List<String> toolNames,
            long lastLatencyMs,
            String diagnosticNote
    ) {
        this(id, name, description, transport, endpoint, status, toolCount, toolNames, lastLatencyMs, diagnosticNote, true);
    }

    public boolean isConnected() {
        return ("CONNECTED".equalsIgnoreCase(status) || "ACTIVE".equalsIgnoreCase(status)) && isEnabled;
    }
}
