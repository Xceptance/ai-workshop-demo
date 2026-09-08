package com.workshop.agent.mcp.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * Standard JSON-RPC 2.0 message representation used by the Model Context Protocol (MCP).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record McpProtocolMessage(
        @JsonProperty("jsonrpc") String jsonrpc,
        @JsonProperty("id") Object id,
        @JsonProperty("method") String method,
        @JsonProperty("params") Object params,
        @JsonProperty("result") Object result,
        @JsonProperty("error") McpError error
) {
    public static final String JSONRPC_VERSION = "2.0";

    public record McpError(
            @JsonProperty("code") int code,
            @JsonProperty("message") String message,
            @JsonProperty("data") Object data
    ) {}

    public static McpProtocolMessage request(Object id, String method, Object params) {
        return new McpProtocolMessage(JSONRPC_VERSION, id, method, params, null, null);
    }

    public static McpProtocolMessage notification(String method, Object params) {
        return new McpProtocolMessage(JSONRPC_VERSION, null, method, params, null, null);
    }

    public static McpProtocolMessage success(Object id, Object result) {
        return new McpProtocolMessage(JSONRPC_VERSION, id, null, null, result, null);
    }

    public static McpProtocolMessage error(Object id, int code, String message) {
        return new McpProtocolMessage(JSONRPC_VERSION, id, null, null, null, new McpError(code, message, null));
    }

    public static McpProtocolMessage initializeRequest(Object id, String clientName, String clientVersion) {
        Map<String, Object> params = Map.of(
                "protocolVersion", "2024-11-05",
                "capabilities", Map.of(),
                "clientInfo", Map.of(
                        "name", clientName,
                        "version", clientVersion
                )
        );
        return request(id, "initialize", params);
    }

    public static McpProtocolMessage initializedNotification() {
        return notification("notifications/initialized", Map.of());
    }

    public static McpProtocolMessage toolsListRequest(Object id) {
        return request(id, "tools/list", Map.of());
    }

    public static McpProtocolMessage toolsCallRequest(Object id, String toolName, Object arguments) {
        Map<String, Object> params = Map.of(
                "name", toolName,
                "arguments", arguments != null ? arguments : Map.of()
        );
        return request(id, "tools/call", params);
    }
}
