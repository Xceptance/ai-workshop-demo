package com.workshop.agent.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workshop.agent.config.AgentProperties;
import com.workshop.agent.logging.ConsoleAnsiLogger;
import com.workshop.agent.logging.MarkdownTranscriptGenerator;
import com.workshop.agent.logging.SessionDiskLogger;
import com.workshop.agent.mcp.client.McpClientManager;
import com.workshop.agent.mcp.server.LocalMcpServer;
import com.workshop.agent.mcp.server.OpenMeteoWeatherMcpServer;
import com.workshop.agent.model.AgentSession;
import com.workshop.agent.model.ChatMessageDto;
import com.workshop.agent.tools.*;
import com.workshop.agent.util.MarkdownRenderer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class McpAgentEngineTest {

    @TempDir
    Path tempLogDir;

    private McpAgentEngine mcpAgentEngine;
    private McpClientManager mcpClientManager;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper();
        LocalMcpServer localMcpServer = new LocalMcpServer(mapper);
        OpenMeteoWeatherMcpServer weatherServer = new OpenMeteoWeatherMcpServer(mapper);
        mcpClientManager = new McpClientManager(localMcpServer, weatherServer, mapper);

        AgentProperties properties = new AgentProperties();
        properties.getLogging().setLogDir(tempLogDir.toString());

        MarkdownTranscriptGenerator transcriptGen = new MarkdownTranscriptGenerator();
        SessionDiskLogger diskLogger = new SessionDiskLogger(properties, transcriptGen, mapper);
        ConsoleAnsiLogger consoleLogger = new ConsoleAnsiLogger();
        MarkdownRenderer mdRenderer = new MarkdownRenderer();
        MockLanguageModel mockModel = new MockLanguageModel();

        CalculatorTool calc = new CalculatorTool();
        SystemInfoTool sys = new SystemInfoTool();
        FileInspectionTool fileTool = new FileInspectionTool();
        WebSearchMockTool webTool = new WebSearchMockTool();
        ToolRegistry registry = new ToolRegistry(calc, sys, fileTool, webTool, mapper);

        TransparentAgentEngine transparentEngine = new TransparentAgentEngine(
                mockModel,
                registry,
                properties,
                diskLogger,
                consoleLogger,
                mdRenderer,
                mapper
        );

        mcpAgentEngine = new McpAgentEngine(
                mockModel,
                mcpClientManager,
                transparentEngine,
                properties,
                diskLogger,
                mdRenderer,
                mapper
        );
    }

    @Test
    void testProcessTurnWithMcpWeather() {
        String sessionId = "mcp-test-session-1";
        ChatMessageDto result = mcpAgentEngine.processTurn(sessionId, "What is the current weather in Berlin?");

        assertNotNull(result);
        assertEquals("assistant", result.role());
        assertNotNull(result.content());

        AgentSession session = mcpAgentEngine.getOrCreateSession(sessionId);
        assertEquals("mcp", session.getMode());
        assertEquals(2, session.getMessages().size()); // 1 user + 1 assistant

        // Verify step events include MCP discovery and MCP wire calls
        boolean hasDiscovery = session.getStepEvents().stream()
                .anyMatch(e -> e.type().name().equals("MCP_DISCOVERY"));
        boolean hasMcpCall = session.getStepEvents().stream()
                .anyMatch(e -> e.type().name().equals("MCP_TOOL_CALL"));

        assertTrue(hasDiscovery, "Should have emitted MCP_DISCOVERY step");
        assertTrue(hasMcpCall, "Should have emitted MCP_TOOL_CALL step");

        // Verify wire traffic recorded
        var traffic = mcpClientManager.getSessionTraffic(sessionId);
        assertFalse(traffic.isEmpty(), "MCP traffic should be recorded for session");
    }

    @Test
    void testProcessTurnWithMcpMath() {
        String sessionId = "mcp-test-session-2";
        ChatMessageDto result = mcpAgentEngine.processTurn(sessionId, "Calculate 42 * 8 using MCP");

        assertNotNull(result);
        AgentSession session = mcpAgentEngine.getOrCreateSession(sessionId);
        assertTrue(session.getStepEvents().size() >= 4);
    }

    @Test
    void testProcessTurnWithDisabledMcpServer() {
        String sessionId = "mcp-test-session-disabled-server";
        AgentSession session = mcpAgentEngine.getOrCreateSession(sessionId);
        session.setMcpServerEnabled("open-meteo-weather", false);

        ChatMessageDto result = mcpAgentEngine.processTurn(sessionId, "What is the current weather in Berlin?");
        assertNotNull(result);

        var discoveryEvent = session.getStepEvents().stream()
                .filter(e -> e.type().name().equals("MCP_DISCOVERY"))
                .findFirst()
                .orElseThrow();

        @SuppressWarnings("unchecked")
        java.util.Set<String> disabledServers = (java.util.Set<String>) discoveryEvent.metadata().get("disabledServers");
        assertNotNull(disabledServers);
        assertTrue(disabledServers.contains("open-meteo-weather"));

        // No tools from open-meteo-weather should be in active tools
        @SuppressWarnings("unchecked")
        java.util.List<String> activeTools = (java.util.List<String>) discoveryEvent.metadata().get("tools");
        assertFalse(activeTools.contains("get_current_weather"));
    }

    @Test
    void testProcessTurnWithDisabledMcpTool() {
        String sessionId = "mcp-test-session-disabled-tool";
        AgentSession session = mcpAgentEngine.getOrCreateSession(sessionId);
        session.setMcpToolEnabled("get_current_weather", false);

        ChatMessageDto result = mcpAgentEngine.processTurn(sessionId, "What is the current weather in Berlin?");
        assertNotNull(result);

        var discoveryEvent = session.getStepEvents().stream()
                .filter(e -> e.type().name().equals("MCP_DISCOVERY"))
                .findFirst()
                .orElseThrow();

        @SuppressWarnings("unchecked")
        java.util.List<String> disabledTools = (java.util.List<String>) discoveryEvent.metadata().get("disabledTools");
        assertNotNull(disabledTools);
        assertTrue(disabledTools.contains("get_current_weather"));
    }

    @Test
    void testExecuteToolCallBlockedWhenDisabled() {
        String sessionId = "mcp-test-blocked";
        AgentSession session = mcpAgentEngine.getOrCreateSession(sessionId);
        session.setMcpToolEnabled("calculate", false);

        var result = mcpClientManager.executeToolCall(sessionId, "calculate", "{}", session);
        assertFalse(result.isSuccess());
        assertTrue(result.errorMessage().contains("disabled"));

        session.setMcpToolEnabled("calculate", true);
        session.setMcpServerEnabled("local-mcp-server", false);

        var resultServer = mcpClientManager.executeToolCall(sessionId, "calculate", "{}", session);
        assertFalse(resultServer.isSuccess());
        assertTrue(resultServer.errorMessage().contains("disabled"));
    }

}
