package com.workshop.agent.mcp.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workshop.agent.mcp.model.McpProtocolMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LocalMcpServerTest {

    private LocalMcpServer server;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        server = new LocalMcpServer(objectMapper);
    }

    @Test
    void testInitialize() {
        McpProtocolMessage request = McpProtocolMessage.initializeRequest(1, "test-client", "1.0.0");
        McpProtocolMessage response = server.handleRequest(request);

        assertNotNull(response);
        assertEquals("2.0", response.jsonrpc());
        assertEquals(1, response.id());
        assertNull(response.error());

        Map<?, ?> result = (Map<?, ?>) response.result();
        assertNotNull(result);
        assertEquals("2024-11-05", result.get("protocolVersion"));
        Map<?, ?> serverInfo = (Map<?, ?>) result.get("serverInfo");
        assertEquals("local-workshop-tools", serverInfo.get("name"));
    }

    @Test
    void testToolsList() {
        McpProtocolMessage request = McpProtocolMessage.toolsListRequest(2);
        McpProtocolMessage response = server.handleRequest(request);

        assertNotNull(response);
        assertEquals(2, response.id());

        Map<?, ?> result = (Map<?, ?>) response.result();
        List<?> tools = (List<?>) result.get("tools");
        assertTrue(tools.size() >= 4);

        boolean hasCalculate = tools.stream().anyMatch(t -> "calculate".equals(((Map<?, ?>) t).get("name")));
        boolean hasSystem = tools.stream().anyMatch(t -> "get_system_info".equals(((Map<?, ?>) t).get("name")));
        assertTrue(hasCalculate);
        assertTrue(hasSystem);
    }

    @Test
    void testToolsCallCalculate() {
        McpProtocolMessage request = McpProtocolMessage.toolsCallRequest(3, "calculate", Map.of("expression", "42 * 10"));
        McpProtocolMessage response = server.handleRequest(request);

        assertNotNull(response);
        assertEquals(3, response.id());
        assertNull(response.error());

        Map<?, ?> result = (Map<?, ?>) response.result();
        List<?> content = (List<?>) result.get("content");
        assertEquals(1, content.size());
        Map<?, ?> item = (Map<?, ?>) content.get(0);
        assertEquals("text", item.get("type"));
        String text = (String) item.get("text");
        assertTrue(text.contains("420"));
    }

    @Test
    void testToolsCallCalculateCompoundExpression() {
        // Test expression from user's workshop session: 2^8 * 15 = 256 * 15 = 3840
        McpProtocolMessage request = McpProtocolMessage.toolsCallRequest(31, "calculate", Map.of("expression", "2^8 * 15"));
        McpProtocolMessage response = server.handleRequest(request);

        assertNotNull(response);
        assertNull(response.error());

        Map<?, ?> result = (Map<?, ?>) response.result();
        List<?> content = (List<?>) result.get("content");
        Map<?, ?> item = (Map<?, ?>) content.get(0);
        String text = (String) item.get("text");
        assertTrue(text.contains("3840"), "Expected 3840, got: " + text);
    }

    @Test
    void testToolsCallCalculateWithJsonNodeArguments() {
        // Ensure that JsonNode arguments passed from ObjectMapper readTree are properly extracted
        com.fasterxml.jackson.databind.node.ObjectNode argsNode = objectMapper.createObjectNode();
        argsNode.put("expression", "sqrt(144) + 2^4");

        McpProtocolMessage request = McpProtocolMessage.toolsCallRequest(32, "calculate", argsNode);
        McpProtocolMessage response = server.handleRequest(request);

        assertNotNull(response);
        assertNull(response.error());

        Map<?, ?> result = (Map<?, ?>) response.result();
        List<?> content = (List<?>) result.get("content");
        Map<?, ?> item = (Map<?, ?>) content.get(0);
        String text = (String) item.get("text");
        // sqrt(144) + 2^4 = 12 + 16 = 28
        assertTrue(text.contains("28"), "Expected 28, got: " + text);
    }

    @Test
    void testToolsCallSystemInfo() {
        McpProtocolMessage request = McpProtocolMessage.toolsCallRequest(4, "get_system_info", Map.of());
        McpProtocolMessage response = server.handleRequest(request);

        assertNotNull(response);
        assertNull(response.error());

        Map<?, ?> result = (Map<?, ?>) response.result();
        List<?> content = (List<?>) result.get("content");
        Map<?, ?> item = (Map<?, ?>) content.get(0);
        String text = (String) item.get("text");
        assertTrue(text.contains("Host System Metrics"));
        assertTrue(text.contains("Java:"));
    }

    @Test
    void testToolsCallUnknownMethod() {
        McpProtocolMessage request = McpProtocolMessage.request(99, "unknown/method", Map.of());
        McpProtocolMessage response = server.handleRequest(request);

        assertNotNull(response);
        assertNotNull(response.error());
        assertEquals(-32601, response.error().code());
    }
}
