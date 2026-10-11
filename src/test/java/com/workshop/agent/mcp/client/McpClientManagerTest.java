package com.workshop.agent.mcp.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workshop.agent.mcp.model.McpProtocolMessage;
import com.workshop.agent.mcp.model.McpServerConfig;
import com.workshop.agent.mcp.server.LocalMcpServer;
import com.workshop.agent.mcp.server.OpenMeteoWeatherMcpServer;
import dev.langchain4j.agent.tool.ToolSpecification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class McpClientManagerTest {

    private McpClientManager clientManager;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        LocalMcpServer localMcpServer = new LocalMcpServer(objectMapper);
        OpenMeteoWeatherMcpServer weatherServer = new OpenMeteoWeatherMcpServer(objectMapper);
        clientManager = new McpClientManager(localMcpServer, weatherServer, objectMapper);
    }

    @Test
    @DisplayName("Local and OpenMeteo servers are discovered on startup")
    void testInitialDiscovery() {
        List<McpServerConfig> configs = clientManager.getServerConfigs();
        assertThat(configs).hasSize(5);

        McpServerConfig localConfig = configs.stream()
                .filter(c -> c.id().equals("local-mcp-server"))
                .findFirst().orElseThrow();
        assertThat(localConfig.status()).isEqualTo("CONNECTED");
        assertThat(localConfig.toolCount()).isGreaterThan(0);

        McpServerConfig weatherConfig = configs.stream()
                .filter(c -> c.id().equals("open-meteo-weather"))
                .findFirst().orElseThrow();
        assertThat(weatherConfig.status()).isEqualTo("CONNECTED");
        assertThat(weatherConfig.toolCount()).isGreaterThan(0);

        // Both standalone subprojects are represented in server configs
        assertThat(configs.stream().map(McpServerConfig::id))
                .contains("energy-market-server", "energy-market-server-bmad");
    }

    @Test
    @DisplayName("Disabling servers in session filters them out during refresh")
    void testDisableServer() {
        clientManager.refreshAllServers("test-session", Set.of("local-mcp-server", "open-meteo-weather"));
        List<McpServerConfig> configs = clientManager.getServerConfigs(Set.of("local-mcp-server", "open-meteo-weather"));

        McpServerConfig localConfig = configs.stream()
                .filter(c -> c.id().equals("local-mcp-server"))
                .findFirst().orElseThrow();
        assertThat(localConfig.status()).isEqualTo("DISABLED");
        assertThat(localConfig.isEnabled()).isFalse();

        McpServerConfig weatherConfig = configs.stream()
                .filter(c -> c.id().equals("open-meteo-weather"))
                .findFirst().orElseThrow();
        assertThat(weatherConfig.status()).isEqualTo("DISABLED");
        assertThat(weatherConfig.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("Local MCP tools execute successfully through McpClientManager")
    void testLocalToolExecution() {
        var result = clientManager.executeToolCall("test-session", "calculate", "{\"expression\": \"25 + 17\"}");
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.resultText()).contains("42");
    }

    @Test
    @DisplayName("When 8085 and 8086 servers are running, both are registered and can be called")
    void testStandaloneServersDiscoveryAndExecution() {
        List<McpServerConfig> configs = clientManager.getServerConfigs();
        McpServerConfig server8085 = configs.stream()
                .filter(c -> c.id().equals("energy-market-server"))
                .findFirst().orElseThrow();
        McpServerConfig server8086 = configs.stream()
                .filter(c -> c.id().equals("energy-market-server-bmad"))
                .findFirst().orElseThrow();

        // If running, both should be CONNECTED with tools
        if ("CONNECTED".equals(server8085.status())) {
            assertThat(server8085.toolCount()).isGreaterThanOrEqualTo(4);
            assertThat(server8085.toolNames()).contains("energy_get_spot_prices");
        }
        if ("CONNECTED".equals(server8086.status())) {
            assertThat(server8086.toolCount()).isGreaterThanOrEqualTo(4);
            assertThat(server8086.toolNames()).contains("bmad-energy_get_spot_prices");

            // Calling the tool delegates over HTTP with SSE unwrapping
            var result = clientManager.executeToolCall("test-session", "bmad-energy_get_spot_prices", "{\"zone\": \"DE-LU\"}");
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.resultText()).contains("averageDayAheadPrice");
        }
    }
}
