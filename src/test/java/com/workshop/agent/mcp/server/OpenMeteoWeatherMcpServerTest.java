package com.workshop.agent.mcp.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workshop.agent.mcp.model.McpProtocolMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class OpenMeteoWeatherMcpServerTest {

    private OpenMeteoWeatherMcpServer server;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        server = new OpenMeteoWeatherMcpServer(objectMapper);
    }

    @Test
    void testInitialize() {
        McpProtocolMessage request = McpProtocolMessage.initializeRequest(1, "test-client", "1.0.0");
        McpProtocolMessage response = server.handleRequest(request);

        assertNotNull(response);
        assertEquals(1, response.id());
        assertNull(response.error());

        Map<?, ?> result = (Map<?, ?>) response.result();
        Map<?, ?> serverInfo = (Map<?, ?>) result.get("serverInfo");
        assertEquals("open-meteo-weather", serverInfo.get("name"));
    }

    @Test
    void testToolsList() {
        McpProtocolMessage request = McpProtocolMessage.toolsListRequest(2);
        McpProtocolMessage response = server.handleRequest(request);

        assertNotNull(response);
        Map<?, ?> result = (Map<?, ?>) response.result();
        List<?> tools = (List<?>) result.get("tools");
        assertTrue(tools.size() >= 2);

        boolean hasWeather = tools.stream().anyMatch(t -> "get_current_weather".equals(((Map<?, ?>) t).get("name")));
        boolean hasForecast = tools.stream().anyMatch(t -> "get_weather_forecast".equals(((Map<?, ?>) t).get("name")));
        assertTrue(hasWeather);
        assertTrue(hasForecast);
    }

    @Test
    void testGetCurrentWeather() {
        McpProtocolMessage request = McpProtocolMessage.toolsCallRequest(3, "get_current_weather", Map.of("location", "Berlin"));
        McpProtocolMessage response = server.handleRequest(request);

        assertNotNull(response);
        assertNull(response.error());

        Map<?, ?> result = (Map<?, ?>) response.result();
        List<?> content = (List<?>) result.get("content");
        Map<?, ?> item = (Map<?, ?>) content.get(0);
        String text = (String) item.get("text");
        assertTrue(text.contains("Berlin"));
        assertTrue(text.contains("Temperature"));
    }
}
