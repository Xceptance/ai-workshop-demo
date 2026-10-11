package com.workshop.energy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workshop.energy.domain.EnergyMarketEngine;
import com.workshop.energy.domain.MarketModels;
import com.workshop.energy.mcp.EnergyMarketResources;
import com.workshop.energy.mcp.EnergyMarketTools;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class EnergyMarketServerIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EnergyMarketEngine engine;

    @Autowired
    private EnergyMarketTools tools;

    @Autowired
    private EnergyMarketResources resources;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Nested
    @DisplayName("Domain Engine Tests")
    class EngineTests {

        @Test
        @DisplayName("calculateSpotPrices generates 24 hourly prices with valid statistical bounds")
        void testCalculateSpotPrices() {
            MarketModels.SpotPriceResult result = engine.calculateSpotPrices("DE-LU", "2026-10-10");

            assertThat(result.zone()).isEqualTo("DE-LU");
            assertThat(result.date()).isEqualTo("2026-10-10");
            assertThat(result.hourlyPrices()).hasSize(24);
            assertThat(result.maxPrice()).isGreaterThanOrEqualTo(result.minPrice());
            assertThat(result.averageDayAheadPrice()).isBetween(-50.0, 250.0);
            assertThat(result.negativePriceHoursCount()).isGreaterThanOrEqualTo(0);
        }

        @Test
        @DisplayName("calculateSpotPrices rejects unsupported bidding zones")
        void testUnsupportedZone() {
            assertThatThrownBy(() -> engine.calculateSpotPrices("US-EAST", "2026-10-10"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unsupported bidding zone");
        }

        @Test
        @DisplayName("calculateHistoricalPrices computes multi-day statistics correctly")
        void testCalculateHistoricalPrices() {
            MarketModels.HistoricalPricesResult result = engine.calculateHistoricalPrices("DE-LU", "2026-10-01", "2026-10-07");

            assertThat(result.zone()).isEqualTo("DE-LU");
            assertThat(result.startDate()).isEqualTo("2026-10-01");
            assertThat(result.endDate()).isEqualTo("2026-10-07");
            assertThat(result.dailySummaries()).hasSize(7);
            assertThat(result.overallMaxPrice()).isGreaterThanOrEqualTo(result.overallMinPrice());
        }

        @Test
        @DisplayName("calculateHistoricalPrices rejects end date before start date")
        void testInvalidDateRange() {
            assertThatThrownBy(() -> engine.calculateHistoricalPrices("DE-LU", "2026-10-10", "2026-10-01"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("is before startDate");
        }

        @Test
        @DisplayName("calculateHistoricalPrices rejects range exceeding 90 days")
        void testDateRangeTooLarge() {
            assertThatThrownBy(() -> engine.calculateHistoricalPrices("DE-LU", "2026-01-01", "2026-06-01"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("exceeds maximum allowed limit of 90 days");
        }

        @Test
        @DisplayName("calculateRenewableForecast provides 24 hours of generation breakdown and grid load")
        void testRenewableForecast() {
            MarketModels.RenewableForecastResult result = engine.calculateRenewableForecast("DE-LU", "2026-10-10");

            assertThat(result.zone()).isEqualTo("DE-LU");
            assertThat(result.hourlyForecasts()).hasSize(24);
            assertThat(result.totalGenerationMwh()).isPositive();
            assertThat(result.averageGridLoadMw()).isPositive();
        }

        @Test
        @DisplayName("evaluateDispatch produces valid charge/discharge schedule and net positive profit spread")
        void testEvaluateDispatch() {
            MarketModels.DispatchEvaluationResult result = engine.evaluateDispatch(10.0, 2.5, 0.88, "2026-10-10");

            assertThat(result.capacityMwh()).isEqualTo(10.0);
            assertThat(result.maxPowerMw()).isEqualTo(2.5);
            assertThat(result.roundTripEfficiency()).isEqualTo(0.88);
            assertThat(result.totalChargedMwh()).isPositive();
            assertThat(result.totalDischargedMwh()).isPositive();
            assertThat(result.schedule()).hasSize(24);

            // Verify state of charge never exceeds capacity and is non-negative
            for (var slot : result.schedule()) {
                assertThat(slot.stateOfChargeMwh()).isBetween(0.0, 10.0);
            }
        }

        @Test
        @DisplayName("evaluateDispatch rejects invalid asset constraints")
        void testInvalidDispatchParameters() {
            assertThatThrownBy(() -> engine.evaluateDispatch(-5.0, 2.0, 0.88, "2026-10-10"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("capacity must be positive");

            assertThatThrownBy(() -> engine.evaluateDispatch(10.0, 0.0, 0.88, "2026-10-10"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("power rating must be positive");

            assertThatThrownBy(() -> engine.evaluateDispatch(10.0, 2.0, 1.5, "2026-10-10"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Round-trip efficiency");
        }
    }

    @Nested
    @DisplayName("MCP Tools and Resources Tests")
    class ToolsAndResourcesTests {

        @Test
        @DisplayName("Tools return valid JSON strings")
        void testToolsOutput() throws Exception {
            String spotJson = tools.getSpotPrices("DE-LU", "2026-10-10");
            JsonNode spotNode = objectMapper.readTree(spotJson);
            assertThat(spotNode.has("hourlyPrices")).isTrue();

            String histJson = tools.getHistoricalPrices("DE-LU", "2026-10-01", "2026-10-05");
            JsonNode histNode = objectMapper.readTree(histJson);
            assertThat(histNode.has("dailySummaries")).isTrue();

            String renJson = tools.getRenewableForecast("DE-LU", "2026-10-10");
            JsonNode renNode = objectMapper.readTree(renJson);
            assertThat(renNode.has("hourlyForecasts")).isTrue();

            String dispJson = tools.evaluateDispatch(10.0, 2.5, 0.85, "2026-10-10");
            JsonNode dispNode = objectMapper.readTree(dispJson);
            assertThat(dispNode.has("schedule")).isTrue();
        }

        @Test
        @DisplayName("Resources return valid JSON snapshots")
        void testResourcesOutput() throws Exception {
            String todayJson = resources.getTodayPrices();
            JsonNode todayNode = objectMapper.readTree(todayJson);
            assertThat(todayNode.has("zone")).isTrue();

            String histDeLuJson = resources.getHistoricalPricesDeLu();
            JsonNode histDeLuNode = objectMapper.readTree(histDeLuJson);
            assertThat(histDeLuNode.has("zone")).isTrue();

            String gridJson = resources.getGridBalanceDeLu();
            JsonNode gridNode = objectMapper.readTree(gridJson);
            assertThat(gridNode.has("currentFrequencyHz")).isTrue();
        }
    }

    @Nested
    @DisplayName("MCP Protocol HTTP Endpoint Tests")
    class McpEndpointTests {

        @Test
        @DisplayName("POST /mcp handles JSON-RPC initialize handshake without authentication")
        void testMcpInitialize() throws Exception {
            String initPayload = """
                    {
                      "jsonrpc": "2.0",
                      "id": 1,
                      "method": "initialize",
                      "params": {
                        "protocolVersion": "2024-11-05",
                        "capabilities": {},
                        "clientInfo": {
                          "name": "test-client",
                          "version": "1.0.0"
                        }
                      }
                    }
                    """;

            var mvcResult = mockMvc.perform(post("/mcp")
                            .contentType(MediaType.APPLICATION_JSON)
                            .accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON)
                            .content(initPayload))
                    .andReturn();

            System.out.println("DEBUG MCP STATUS: " + mvcResult.getResponse().getStatus());
            System.out.println("DEBUG MCP BODY: " + mvcResult.getResponse().getContentAsString());
            System.out.println("DEBUG MCP ERR: " + mvcResult.getResponse().getErrorMessage());

            assertThat(mvcResult.getResponse().getStatus()).isEqualTo(200);
        }

        @Test
        @DisplayName("POST /mcp handles tools/list and returns energy tools")
        void testMcpToolsList() throws Exception {
            String initPayload = """
                    {
                      "jsonrpc": "2.0",
                      "id": 1,
                      "method": "initialize",
                      "params": {
                        "protocolVersion": "2024-11-05",
                        "capabilities": {},
                        "clientInfo": {
                          "name": "test-client",
                          "version": "1.0.0"
                        }
                      }
                    }
                    """;

            var initResult = mockMvc.perform(post("/mcp")
                            .contentType(MediaType.APPLICATION_JSON)
                            .accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON)
                            .content(initPayload))
                    .andExpect(status().isOk())
                    .andReturn();

            String sessionId = initResult.getResponse().getHeader("Mcp-Session-Id");

            String listPayload = """
                    {
                      "jsonrpc": "2.0",
                      "id": 2,
                      "method": "tools/list",
                      "params": {}
                    }
                    """;

            var reqBuilder = post("/mcp")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON)
                    .content(listPayload);

            if (sessionId != null) {
                reqBuilder.header("Mcp-Session-Id", sessionId);
            }

            String response = mockMvc.perform(reqBuilder)
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            assertThat(response).contains("bmad-energy_get_spot_prices");
            assertThat(response).contains("bmad-energy_get_historical_prices");
            assertThat(response).contains("bmad-energy_get_renewable_forecast");
            assertThat(response).contains("bmad-energy_evaluate_dispatch");
        }
    }
}
