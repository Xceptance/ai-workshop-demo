package com.workshop.energy.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workshop.energy.domain.*;
import com.workshop.energy.mcp.EnergyMarketTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;

@RestController
public class EnergyMarketMcpHttpController {

    private static final Logger log = LoggerFactory.getLogger(EnergyMarketMcpHttpController.class);

    private final EnergyMarketTools tools;
    private final EnergyMarketEngine engine;
    private final ObjectMapper objectMapper;

    public EnergyMarketMcpHttpController(EnergyMarketTools tools, EnergyMarketEngine engine, ObjectMapper objectMapper) {
        this.tools = tools;
        this.engine = engine;
        this.objectMapper = objectMapper;
    }

    @PostMapping(value = "/mcp", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> handleMcpJsonRpc(@RequestBody String requestBody) {
        if (requestBody == null || requestBody.isBlank()) {
            return ResponseEntity.ok(createError(null, -32600, "Invalid Request: empty body"));
        }

        JsonNode request;
        try {
            request = objectMapper.readTree(requestBody);
        } catch (Exception e) {
            return ResponseEntity.ok(createError(null, -32600, "Invalid JSON: " + e.getMessage()));
        }

        if (!request.isObject()) {
            return ResponseEntity.ok(createError(null, -32600, "Invalid Request: expected JSON object"));
        }

        JsonNode idNode = request.get("id");
        Object id = (idNode != null && !idNode.isNull()) ? (idNode.isNumber() ? idNode.numberValue() : idNode.asText()) : null;

        JsonNode methodNode = request.get("method");
        if (methodNode == null || !methodNode.isTextual()) {
            return ResponseEntity.ok(createError(id, -32600, "Invalid Request: missing or non-string method"));
        }
        String method = methodNode.asText();
        JsonNode params = request.get("params");

        log.debug("MCP HTTP Request: method={}, id={}", method, id);

        return switch (method) {
            case "initialize" -> ResponseEntity.ok(handleInitialize(id));
            case "tools/list" -> ResponseEntity.ok(handleToolsList(id));
            case "tools/call" -> ResponseEntity.ok(handleToolsCall(id, params));
            case "resources/list" -> ResponseEntity.ok(handleResourcesList(id));
            case "resources/read" -> ResponseEntity.ok(handleResourcesRead(id, params));
            case "notifications/initialized", "ping" -> ResponseEntity.ok(Map.of("jsonrpc", "2.0", "id", id != null ? id : 1, "result", Map.of()));
            default -> ResponseEntity.ok(createError(id, -32601, "Method not found: " + method));
        };
    }

    private Map<String, Object> handleInitialize(Object id) {
        Map<String, Object> serverInfo = Map.of(
                "name", "energy-market-server",
                "version", "1.0.0"
        );
        Map<String, Object> capabilities = Map.of(
                "tools", Map.of("listChanged", false),
                "resources", Map.of("listChanged", false, "subscribe", false)
        );
        return Map.of(
                "jsonrpc", "2.0",
                "id", id != null ? id : 1,
                "result", Map.of(
                        "protocolVersion", "2024-11-05",
                        "serverInfo", serverInfo,
                        "capabilities", capabilities
                )
        );
    }

    private Map<String, Object> handleToolsList(Object id) {
        List<Map<String, Object>> toolList = List.of(
                createToolDef("energy_get_spot_prices",
                        "Retrieve Day-Ahead and Intraday electricity spot prices in EUR/MWh for a European bidding zone (DE-LU, FR, NL, AT), including averages, extremes, and negative pricing occurrences.",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "biddingZone", Map.of("type", "string", "description", "Bidding zone code: DE-LU, FR, NL, AT"),
                                        "date", Map.of("type", "string", "description", "Optional target date (YYYY-MM-DD)")
                                ),
                                "required", List.of("biddingZone")
                        )),
                createToolDef("energy_get_historical_prices",
                        "Query historical electricity spot prices across a date range (YYYY-MM-DD to YYYY-MM-DD) for a European bidding zone, reporting daily averages and negative price hours.",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "biddingZone", Map.of("type", "string", "description", "Bidding zone code (e.g. DE-LU, FR, NL, AT)"),
                                        "startDate", Map.of("type", "string", "description", "Start date (YYYY-MM-DD)"),
                                        "endDate", Map.of("type", "string", "description", "End date (YYYY-MM-DD)")
                                ),
                                "required", List.of("biddingZone", "startDate", "endDate")
                        )),
                createToolDef("energy_get_renewable_forecast",
                        "Retrieve forecasted renewable generation (wind onshore/offshore, solar PV) and grid load in MW for a specified date and European bidding zone.",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "biddingZone", Map.of("type", "string", "description", "Bidding zone code (e.g. DE-LU, FR, NL, AT)"),
                                        "date", Map.of("type", "string", "description", "Target forecast date (YYYY-MM-DD)")
                                ),
                                "required", List.of("biddingZone")
                        )),
                createToolDef("energy_evaluate_dispatch",
                        "Calculate optimal battery storage or flexible asset charge/discharge schedules to maximize trading margin and grid balancing based on spot price spreads.",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "capacityMWh", Map.of("type", "number", "description", "Battery storage capacity in MWh"),
                                        "maxPowerMw", Map.of("type", "number", "description", "Maximum power charge/discharge rating in MW"),
                                        "roundTripEfficiency", Map.of("type", "number", "description", "Round-trip efficiency (e.g. 0.88), default 0.88"),
                                        "date", Map.of("type", "string", "description", "Target date (YYYY-MM-DD)"),
                                        "biddingZone", Map.of("type", "string", "description", "Bidding zone code (e.g. DE-LU)")
                                ),
                                "required", List.of("capacityMWh", "maxPowerMw")
                        ))
        );

        return Map.of(
                "jsonrpc", "2.0",
                "id", id != null ? id : 1,
                "result", Map.of("tools", toolList)
        );
    }

    private Map<String, Object> createToolDef(String name, String desc, Map<String, Object> schema) {
        return Map.of(
                "name", name,
                "description", desc,
                "inputSchema", schema
        );
    }

    private Map<String, Object> handleToolsCall(Object id, JsonNode params) {
        if (params == null || !params.has("name")) {
            return createError(id, -32602, "Invalid params: missing tool name");
        }
        String name = params.get("name").asText();
        JsonNode args = params.get("arguments");

        try {
            String resultText = switch (name) {
                case "energy_get_spot_prices" -> {
                    String zone = args != null && args.has("biddingZone") ? args.get("biddingZone").asText() : "DE-LU";
                    String date = args != null && args.has("date") ? args.get("date").asText() : null;
                    yield tools.getSpotPrices(zone, date);
                }
                case "energy_get_historical_prices" -> {
                    if (args == null || !args.has("startDate") || !args.has("endDate")) {
                        yield "Error: missing required startDate or endDate";
                    }
                    String zone = args.has("biddingZone") ? args.get("biddingZone").asText() : "DE-LU";
                    String start = args.get("startDate").asText();
                    String end = args.get("endDate").asText();
                    yield tools.getHistoricalPrices(zone, start, end);
                }
                case "energy_get_renewable_forecast" -> {
                    String zone = args != null && args.has("biddingZone") ? args.get("biddingZone").asText() : "DE-LU";
                    String date = args != null && args.has("date") ? args.get("date").asText() : null;
                    yield tools.getRenewableForecast(zone, date);
                }
                case "energy_evaluate_dispatch" -> {
                    if (args == null || !args.has("capacityMWh") || !args.has("maxPowerMw")) {
                        yield "Error: missing required capacityMWh or maxPowerMw";
                    }
                    double cap = args.get("capacityMWh").asDouble();
                    double pwr = args.get("maxPowerMw").asDouble();
                    Double eff = args.has("roundTripEfficiency") ? args.get("roundTripEfficiency").asDouble() : null;
                    String date = args.has("date") ? args.get("date").asText() : null;
                    String zone = args.has("biddingZone") ? args.get("biddingZone").asText() : "DE-LU";
                    yield tools.evaluateDispatch(cap, pwr, eff, date, zone);
                }
                default -> throw new IllegalArgumentException("Unknown tool: " + name);
            };

            return Map.of(
                    "jsonrpc", "2.0",
                    "id", id != null ? id : 1,
                    "result", Map.of(
                            "content", List.of(Map.of("type", "text", "text", resultText)),
                            "isError", resultText.startsWith("Error:")
                    )
            );
        } catch (IllegalArgumentException e) {
            return createError(id, -32602, e.getMessage());
        }
    }

    private Map<String, Object> handleResourcesList(Object id) {
        List<Map<String, Object>> resources = List.of(
                Map.of(
                        "uri", "energy://market/prices/today",
                        "name", "Today's Spot Electricity Prices",
                        "description", "Current day hourly spot prices and market summary for DE-LU",
                        "mimeType", "application/json"
                ),
                Map.of(
                        "uri", "energy://market/prices/history/de-lu",
                        "name", "Historical Spot Electricity Prices (DE-LU)",
                        "description", "Recent 7-day spot prices and daily averages for DE-LU",
                        "mimeType", "application/json"
                ),
                Map.of(
                        "uri", "energy://grid/balance/de-lu",
                        "name", "Live Grid Balance & Renewable Status",
                        "description", "Real-time renewable generation share and grid balance snapshot",
                        "mimeType", "application/json"
                )
        );

        return Map.of(
                "jsonrpc", "2.0",
                "id", id != null ? id : 1,
                "result", Map.of("resources", resources)
        );
    }

    private Map<String, Object> handleResourcesRead(Object id, JsonNode params) {
        if (params == null || !params.has("uri")) {
            return createError(id, -32602, "Invalid params: missing uri");
        }
        String uri = params.get("uri").asText();

        try {
            String jsonText;
            if ("energy://market/prices/today".equalsIgnoreCase(uri)) {
                DailyPriceSummary summary = engine.getDailyPriceSummary(BiddingZone.DE_LU, LocalDate.now());
                jsonText = objectMapper.writeValueAsString(summary);
            } else if ("energy://market/prices/history/de-lu".equalsIgnoreCase(uri)) {
                LocalDate end = LocalDate.now();
                LocalDate start = end.minusDays(6);
                List<DailyPriceSummary> history = engine.getHistoricalPrices(BiddingZone.DE_LU, start, end);
                jsonText = objectMapper.writeValueAsString(history);
            } else if ("energy://grid/balance/de-lu".equalsIgnoreCase(uri)) {
                List<RenewableForecast> forecast = engine.getRenewableForecast(BiddingZone.DE_LU, LocalDate.now());
                jsonText = objectMapper.writeValueAsString(forecast);
            } else {
                return createError(id, -32602, "Resource not found: " + uri);
            }

            return Map.of(
                    "jsonrpc", "2.0",
                    "id", id != null ? id : 1,
                    "result", Map.of(
                            "contents", List.of(Map.of(
                                    "uri", uri,
                                    "mimeType", "application/json",
                                    "text", jsonText
                            ))
                    )
            );
        } catch (Exception e) {
            return createError(id, -32602, "Failed to read resource: " + e.getMessage());
        }
    }

    private Map<String, Object> createError(Object id, int code, String message) {
        return Map.of(
                "jsonrpc", "2.0",
                "id", id != null ? id : 1,
                "error", Map.of(
                        "code", code,
                        "message", message
                )
        );
    }
}
