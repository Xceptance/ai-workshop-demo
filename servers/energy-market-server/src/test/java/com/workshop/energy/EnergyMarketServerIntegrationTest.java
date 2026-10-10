package com.workshop.energy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = EnergyMarketServerApplication.class)
public class EnergyMarketServerIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    @DisplayName("Health endpoint returns UP without auth")
    void testHealthEndpoint() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.service").value("energy-market-server"));
    }

    @Test
    @DisplayName("REST historical prices endpoint returns structured JSON time-series")
    void testRestHistoricalPrices() throws Exception {
        mockMvc.perform(get("/api/prices/historical")
                        .param("zone", "DE-LU")
                        .param("startDate", "2026-10-01")
                        .param("endDate", "2026-10-05"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.biddingZone").value("DE-LU"))
                .andExpect(jsonPath("$.totalDays").value(5))
                .andExpect(jsonPath("$.days[0].hourlyPrices.length()").value(24));
    }

    @Test
    @DisplayName("REST historical prices endpoint validates end date >= start date")
    void testRestHistoricalPricesValidation() throws Exception {
        mockMvc.perform(get("/api/prices/historical")
                        .param("zone", "DE-LU")
                        .param("startDate", "2026-10-10")
                        .param("endDate", "2026-10-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    @DisplayName("MCP POST /mcp handles initialize method")
    void testMcpInitialize() throws Exception {
        String payload = """
                {
                    "jsonrpc": "2.0",
                    "id": 1,
                    "method": "initialize",
                    "params": {}
                }
                """;

        MvcResult result = mockMvc.perform(post("/mcp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(response.get("jsonrpc").asText()).isEqualTo("2.0");
        assertThat(response.get("id").asInt()).isEqualTo(1);
        assertThat(response.path("result").path("serverInfo").path("name").asText()).isEqualTo("energy-market-server");
    }

    @Test
    @DisplayName("MCP POST /mcp handles tools/list and returns all 4 energy tools")
    void testMcpToolsList() throws Exception {
        String payload = """
                {
                    "jsonrpc": "2.0",
                    "id": 2,
                    "method": "tools/list",
                    "params": {}
                }
                """;

        MvcResult result = mockMvc.perform(post("/mcp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode tools = response.path("result").path("tools");
        assertThat(tools.isArray()).isTrue();
        assertThat(tools.size()).isEqualTo(4);

        assertThat(tools.toString()).contains("energy_get_spot_prices");
        assertThat(tools.toString()).contains("energy_get_historical_prices");
        assertThat(tools.toString()).contains("energy_get_renewable_forecast");
        assertThat(tools.toString()).contains("energy_evaluate_dispatch");
    }

    @Test
    @DisplayName("MCP POST /mcp executes energy_get_spot_prices tool")
    void testMcpCallSpotPrices() throws Exception {
        String payload = """
                {
                    "jsonrpc": "2.0",
                    "id": 3,
                    "method": "tools/call",
                    "params": {
                        "name": "energy_get_spot_prices",
                        "arguments": {
                            "biddingZone": "DE-LU",
                            "date": "2026-10-10"
                        }
                    }
                }
                """;

        MvcResult result = mockMvc.perform(post("/mcp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        String text = response.path("result").path("content").get(0).path("text").asText();
        assertThat(text).contains("Spot Electricity Prices for Germany / Luxembourg");
        assertThat(text).contains("EUR/MWh");
        assertThat(text).contains("Hour 00:00:");
    }

    @Test
    @DisplayName("MCP POST /mcp executes energy_get_historical_prices tool")
    void testMcpCallHistoricalPrices() throws Exception {
        String payload = """
                {
                    "jsonrpc": "2.0",
                    "id": 4,
                    "method": "tools/call",
                    "params": {
                        "name": "energy_get_historical_prices",
                        "arguments": {
                            "biddingZone": "DE-LU",
                            "startDate": "2026-10-01",
                            "endDate": "2026-10-07"
                        }
                    }
                }
                """;

        MvcResult result = mockMvc.perform(post("/mcp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        String text = response.path("result").path("content").get(0).path("text").asText();
        assertThat(text).contains("Historical Spot Prices for Germany / Luxembourg");
        assertThat(text).contains("7 days");
        assertThat(text).contains("Overall Average:");
    }

    @Test
    @DisplayName("MCP POST /mcp executes energy_evaluate_dispatch tool")
    void testMcpCallEvaluateDispatch() throws Exception {
        String payload = """
                {
                    "jsonrpc": "2.0",
                    "id": 5,
                    "method": "tools/call",
                    "params": {
                        "name": "energy_evaluate_dispatch",
                        "arguments": {
                            "capacityMWh": 10.0,
                            "maxPowerMw": 5.0,
                            "roundTripEfficiency": 0.88,
                            "date": "2026-10-10",
                            "biddingZone": "DE-LU"
                        }
                    }
                }
                """;

        MvcResult result = mockMvc.perform(post("/mcp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        String text = response.path("result").path("content").get(0).path("text").asText();
        JsonNode schedule = objectMapper.readTree(text);
        assertThat(schedule.path("assetType").asText()).isEqualTo("BATTERY_STORAGE");
        assertThat(schedule.path("capacityMWh").asDouble()).isEqualTo(10.0);
        assertThat(schedule.path("actions").isArray()).isTrue();
        assertThat(schedule.path("actions").size()).isEqualTo(24);
    }

    @Test
    @DisplayName("MCP POST /mcp handles resources/list and resources/read")
    void testMcpResources() throws Exception {
        String listPayload = """
                {
                    "jsonrpc": "2.0",
                    "id": 6,
                    "method": "resources/list",
                    "params": {}
                }
                """;

        MvcResult listResult = mockMvc.perform(post("/mcp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(listPayload))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode listResponse = objectMapper.readTree(listResult.getResponse().getContentAsString());
        assertThat(listResponse.path("result").path("resources").size()).isGreaterThanOrEqualTo(3);

        String readPayload = """
                {
                    "jsonrpc": "2.0",
                    "id": 7,
                    "method": "resources/read",
                    "params": {
                        "uri": "energy://market/prices/today"
                    }
                }
                """;

        MvcResult readResult = mockMvc.perform(post("/mcp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(readPayload))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode readResponse = objectMapper.readTree(readResult.getResponse().getContentAsString());
        String jsonContent = readResponse.path("result").path("contents").get(0).path("text").asText();
        JsonNode summary = objectMapper.readTree(jsonContent);
        assertThat(summary.has("hourlyPrices")).isTrue();
    }

    @Test
    @DisplayName("MCP POST /mcp returns error -32601 on unknown method")
    void testUnknownMethod() throws Exception {
        String payload = """
                {
                    "jsonrpc": "2.0",
                    "id": 99,
                    "method": "unknown/method",
                    "params": {}
                }
                """;

        MvcResult result = mockMvc.perform(post("/mcp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(response.path("error").path("code").asInt()).isEqualTo(-32601);
    }
}
