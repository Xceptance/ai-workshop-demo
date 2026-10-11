package com.workshop.agent.mcp.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.workshop.agent.mcp.model.McpProtocolMessage;
import com.workshop.agent.mcp.model.McpServerConfig;
import com.workshop.agent.mcp.model.McpWireRecord;
import com.workshop.agent.mcp.server.LocalMcpServer;
import com.workshop.agent.mcp.server.OpenMeteoWeatherMcpServer;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * MCP Client Manager & Wire Interceptor.
 *
 * Coordinates connections to local and remote Model Context Protocol (MCP) servers,
 * dynamically discovers tools via `tools/list`, translates MCP JSON Schemas into LLM ToolSpecifications,
 * dispatches `tools/call` over the wire, and records every JSON-RPC interaction for Glass-Box auditing.
 */
@Service
public class McpClientManager {

    private static final Logger log = LoggerFactory.getLogger(McpClientManager.class);

    public record McpToolExecutionResult(
            String toolName,
            String serverName,
            String transport,
            String requestJson,
            String responseJson,
            String resultText,
            long latencyMs,
            boolean isSuccess,
            String errorMessage
    ) {}

    private final LocalMcpServer localMcpServer;
    private final OpenMeteoWeatherMcpServer openMeteoWeatherServer;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    private final AtomicLong messageIdSequence = new AtomicLong(1);

    // Mappings: toolName -> target server ID
    private final Map<String, String> toolToServerMap = new ConcurrentHashMap<>();
    // Mappings: exposed toolName -> remote server wire toolName (for disambiguated tool aliases)
    private final Map<String, String> toolToOriginalNameMap = new ConcurrentHashMap<>();
    // Discovered ToolSpecifications
    private final List<ToolSpecification> discoveredSpecifications = new CopyOnWriteArrayList<>();
    // Discovered Tool raw definitions for inspection
    private final Map<String, Map<String, Object>> toolRawDefinitions = new ConcurrentHashMap<>();

    // Per-session wire traffic audit logs
    private final Map<String, List<McpWireRecord>> sessionTraffic = new ConcurrentHashMap<>();
    // Global circular buffer of recent wire events for the live drawer
    private final List<McpWireRecord> recentGlobalTraffic = new CopyOnWriteArrayList<>();

    // Remote WWO Endpoint
    private final String remoteWwoUrl = "https://mcp.worldweatheronline.com/mcp";
    // Standalone Subproject Energy Market Server Endpoints
    private final String energyServerUrl = "http://localhost:8085/mcp";
    private final String energyBmadServerUrl = "http://localhost:8086/mcp";
    private volatile String energySessionId = null;
    private volatile String energyBmadSessionId = null;

    public McpClientManager(
            LocalMcpServer localMcpServer,
            OpenMeteoWeatherMcpServer openMeteoWeatherServer,
            ObjectMapper objectMapper
    ) {
        this.localMcpServer = localMcpServer;
        this.openMeteoWeatherServer = openMeteoWeatherServer;
        this.objectMapper = objectMapper.copy().enable(SerializationFeature.INDENT_OUTPUT);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();

        // Perform initial discovery on startup
        refreshAllServers("system-init");
    }

    public synchronized void refreshAllServers(String sessionId) {
        refreshAllServers(sessionId, Collections.emptySet());
    }

    public synchronized void refreshAllServers(String sessionId, Set<String> disabledServers) {
        toolToServerMap.clear();
        toolToOriginalNameMap.clear();
        discoveredSpecifications.clear();
        toolRawDefinitions.clear();

        Set<String> disabled = (disabledServers != null) ? disabledServers : Collections.emptySet();

        // 1. Discover from Local MCP Server
        if (!disabled.contains("local-mcp-server")) {
            discoverLocalServer(sessionId);
        }

        // 2. Discover from Open-Meteo Weather MCP Server
        if (!disabled.contains("open-meteo-weather")) {
            discoverOpenMeteoServer(sessionId);
        }

        // 3. Discover from Standalone Energy Market MCP Server (Port 8085, if running)
        if (!disabled.contains("energy-market-server")) {
            discoverEnergyMarketServer(sessionId);
        }

        // 4. Discover from Standalone Energy Market MCP Server - BMad (Port 8086, if running)
        if (!disabled.contains("energy-market-server-bmad")) {
            discoverEnergyMarketBmadServer(sessionId);
        }

        log.info("[McpClientManager] Total discovered MCP tools: {} across servers: {}",
                discoveredSpecifications.size(), toolToServerMap.values().stream().distinct().toList());
    }

    public List<ToolSpecification> getDiscoveredToolSpecifications() {
        return new ArrayList<>(discoveredSpecifications);
    }

    public Map<String, Map<String, Object>> getToolRawDefinitions() {
        return new LinkedHashMap<>(toolRawDefinitions);
    }

    public List<McpWireRecord> getSessionTraffic(String sessionId) {
        return sessionTraffic.getOrDefault(sessionId, List.of());
    }

    public List<McpWireRecord> getRecentGlobalTraffic() {
        return new ArrayList<>(recentGlobalTraffic);
    }

    public List<McpServerConfig> getServerConfigs() {
        return getServerConfigs(Collections.emptySet());
    }

    public List<McpServerConfig> getServerConfigs(Set<String> disabledServers) {
        Set<String> disabled = (disabledServers != null) ? disabledServers : Collections.emptySet();
        List<McpServerConfig> list = new ArrayList<>();

        // 1. Local Server
        boolean localEnabled = !disabled.contains("local-mcp-server");
        long localToolCount = toolToServerMap.values().stream().filter(s -> s.equals("local-mcp-server")).count();
        List<String> localTools = toolToServerMap.entrySet().stream()
                .filter(e -> e.getValue().equals("local-mcp-server"))
                .map(Map.Entry::getKey)
                .toList();
        list.add(new McpServerConfig(
                "local-mcp-server",
                "Local Workshop Tools",
                "In-JVM Java MCP Server exposing Math, System, Files, and Search tools",
                "LOOPBACK / STDIO",
                "com.workshop.agent.mcp.server.LocalMcpServer",
                localEnabled ? "CONNECTED" : "DISABLED",
                (int) localToolCount,
                localTools,
                2L,
                localEnabled ? "Ready for local function execution" : "Server disabled by session setting",
                localEnabled
        ));

        // 2. Open-Meteo Weather Server
        boolean meteoEnabled = !disabled.contains("open-meteo-weather");
        long meteoToolCount = toolToServerMap.values().stream().filter(s -> s.equals("open-meteo-weather")).count();
        List<String> meteoTools = toolToServerMap.entrySet().stream()
                .filter(e -> e.getValue().equals("open-meteo-weather"))
                .map(Map.Entry::getKey)
                .toList();
        list.add(new McpServerConfig(
                "open-meteo-weather",
                "Open-Meteo Weather (Free & Open)",
                "Global real-time conditions & forecasts with NO API key needed",
                "STREAMABLE_HTTP (REST Bridge)",
                "https://api.open-meteo.com",
                meteoEnabled ? "CONNECTED" : "DISABLED",
                (int) meteoToolCount,
                meteoTools,
                120L,
                meteoEnabled ? "100% Free Open Weather MCP Service active" : "Server disabled by session setting",
                meteoEnabled
        ));

        // 3. Remote World Weather Online (Cloud Demo)
        boolean wwoEnabled = !disabled.contains("world-weather-online");
        list.add(new McpServerConfig(
                "world-weather-online",
                "World Weather Online (Cloud Remote)",
                "External commercial MCP endpoint on Railway",
                "STREAMABLE_HTTP (POST)",
                remoteWwoUrl,
                wwoEnabled ? "DIAGNOSTIC (404 Offline)" : "DISABLED",
                0,
                List.of(),
                0L,
                wwoEnabled ? "Remote Railway host returns 404. Live fallback routes to Open-Meteo." : "Server disabled by session setting",
                wwoEnabled
        ));

        // 4. Standalone Energy Market Server (Port 8085)
        boolean energyEnabled = !disabled.contains("energy-market-server");
        long energyToolCount = toolToServerMap.values().stream().filter(s -> s.equals("energy-market-server")).count();
        List<String> energyTools = toolToServerMap.entrySet().stream()
                .filter(e -> e.getValue().equals("energy-market-server"))
                .map(Map.Entry::getKey)
                .toList();
        list.add(new McpServerConfig(
                "energy-market-server",
                "Energy Market Server (Port 8085)",
                "Spot electricity prices, historical data, renewable forecast & battery arbitrage",
                "STREAMABLE_HTTP (Port 8085)",
                energyServerUrl,
                !energyEnabled ? "DISABLED" : (energyToolCount > 0 ? "CONNECTED" : "STANDALONE_READY"),
                (int) energyToolCount,
                energyTools,
                energyToolCount > 0 ? 15L : 0L,
                !energyEnabled ? "Server disabled by session setting" : (energyToolCount > 0
                        ? "Active on port 8085 (no auth)"
                        : "Standalone subproject in servers/energy-market-server (start with: mvn spring-boot:run)"),
                energyEnabled
        ));

        // 5. Standalone Energy Market Server - BMad (Port 8086)
        boolean energyBmadEnabled = !disabled.contains("energy-market-server-bmad");
        long energyBmadToolCount = toolToServerMap.values().stream().filter(s -> s.equals("energy-market-server-bmad")).count();
        List<String> energyBmadTools = toolToServerMap.entrySet().stream()
                .filter(e -> e.getValue().equals("energy-market-server-bmad"))
                .map(Map.Entry::getKey)
                .toList();
        list.add(new McpServerConfig(
                "energy-market-server-bmad",
                "Energy Market Server - BMad (Port 8086)",
                "Spring AI 2 WebMVC subproject exposing energy spot, forecast, and dispatch tools",
                "STREAMABLE_HTTP (Port 8086)",
                energyBmadServerUrl,
                !energyBmadEnabled ? "DISABLED" : (energyBmadToolCount > 0 ? "CONNECTED" : "STANDALONE_READY"),
                (int) energyBmadToolCount,
                energyBmadTools,
                energyBmadToolCount > 0 ? 15L : 0L,
                !energyBmadEnabled ? "Server disabled by session setting" : (energyBmadToolCount > 0
                        ? "Active on port 8086 (no auth)"
                        : "Standalone subproject in servers/energy-market-server-bmad (start with: mvn spring-boot:run)"),
                energyBmadEnabled
        ));

        return list;
    }

    private void discoverLocalServer(String sessionId) {
        String serverId = "local-mcp-server";
        long reqId = messageIdSequence.getAndIncrement();

        // 1. Initialize Handshake
        McpProtocolMessage initReq = McpProtocolMessage.initializeRequest(reqId, "ai-mini-agent", "1.0.0");
        long startInit = System.currentTimeMillis();
        McpProtocolMessage initResp = localMcpServer.handleRequest(initReq);
        long durInit = System.currentTimeMillis() - startInit;

        recordWireTraffic(sessionId, serverId, "LOOPBACK", "OUTBOUND_REQUEST", "initialize", reqId, toJson(initReq), 0, true, null);
        recordWireTraffic(sessionId, serverId, "LOOPBACK", "INBOUND_RESPONSE", "initialize", reqId, toJson(initResp), durInit, true, null);

        // 2. notifications/initialized
        McpProtocolMessage notify = McpProtocolMessage.initializedNotification();
        localMcpServer.handleRequest(notify);
        recordWireTraffic(sessionId, serverId, "LOOPBACK", "NOTIFICATION", "notifications/initialized", null, toJson(notify), 0, true, null);

        // 3. tools/list
        long listId = messageIdSequence.getAndIncrement();
        McpProtocolMessage listReq = McpProtocolMessage.toolsListRequest(listId);
        long startList = System.currentTimeMillis();
        McpProtocolMessage listResp = localMcpServer.handleRequest(listReq);
        long durList = System.currentTimeMillis() - startList;

        recordWireTraffic(sessionId, serverId, "LOOPBACK", "OUTBOUND_REQUEST", "tools/list", listId, toJson(listReq), 0, true, null);
        recordWireTraffic(sessionId, serverId, "LOOPBACK", "INBOUND_RESPONSE", "tools/list", listId, toJson(listResp), durList, true, null);

        registerDiscoveredTools(serverId, listResp);
    }

    private void discoverOpenMeteoServer(String sessionId) {
        String serverId = "open-meteo-weather";
        long reqId = messageIdSequence.getAndIncrement();

        // 1. Initialize Handshake
        McpProtocolMessage initReq = McpProtocolMessage.initializeRequest(reqId, "ai-mini-agent", "1.0.0");
        long startInit = System.currentTimeMillis();
        McpProtocolMessage initResp = openMeteoWeatherServer.handleRequest(initReq);
        long durInit = System.currentTimeMillis() - startInit;

        recordWireTraffic(sessionId, serverId, "STREAMABLE_HTTP", "OUTBOUND_REQUEST", "initialize", reqId, toJson(initReq), 0, true, null);
        recordWireTraffic(sessionId, serverId, "STREAMABLE_HTTP", "INBOUND_RESPONSE", "initialize", reqId, toJson(initResp), durInit, true, null);

        // 2. tools/list
        long listId = messageIdSequence.getAndIncrement();
        McpProtocolMessage listReq = McpProtocolMessage.toolsListRequest(listId);
        long startList = System.currentTimeMillis();
        McpProtocolMessage listResp = openMeteoWeatherServer.handleRequest(listReq);
        long durList = System.currentTimeMillis() - startList;

        recordWireTraffic(sessionId, serverId, "STREAMABLE_HTTP", "OUTBOUND_REQUEST", "tools/list", listId, toJson(listReq), 0, true, null);
        recordWireTraffic(sessionId, serverId, "STREAMABLE_HTTP", "INBOUND_RESPONSE", "tools/list", listId, toJson(listResp), durList, true, null);

        registerDiscoveredTools(serverId, listResp);
    }

    private void discoverEnergyMarketServer(String sessionId) {
        String serverId = "energy-market-server";
        try {
            long reqId = messageIdSequence.getAndIncrement();
            McpProtocolMessage initReq = McpProtocolMessage.initializeRequest(reqId, "ai-mini-agent", "1.0.0");
            long startInit = System.currentTimeMillis();
            McpProtocolMessage initResp = sendHttpJsonRpc(energyServerUrl, initReq);
            long durInit = System.currentTimeMillis() - startInit;

            recordWireTraffic(sessionId, serverId, "STREAMABLE_HTTP", "OUTBOUND_REQUEST", "initialize", reqId, toJson(initReq), 0, true, null);
            recordWireTraffic(sessionId, serverId, "STREAMABLE_HTTP", "INBOUND_RESPONSE", "initialize", reqId, toJson(initResp), durInit, true, null);

            long listId = messageIdSequence.getAndIncrement();
            McpProtocolMessage listReq = McpProtocolMessage.toolsListRequest(listId);
            long startList = System.currentTimeMillis();
            McpProtocolMessage listResp = sendHttpJsonRpc(energyServerUrl, listReq);
            long durList = System.currentTimeMillis() - startList;

            recordWireTraffic(sessionId, serverId, "STREAMABLE_HTTP", "OUTBOUND_REQUEST", "tools/list", listId, toJson(listReq), 0, true, null);
            recordWireTraffic(sessionId, serverId, "STREAMABLE_HTTP", "INBOUND_RESPONSE", "tools/list", listId, toJson(listResp), durList, true, null);

            registerDiscoveredTools(serverId, listResp);
            log.info("[McpClientManager] Successfully connected to Energy Market MCP Server at {}", energyServerUrl);
        } catch (Exception e) {
            log.debug("[McpClientManager] Energy Market MCP server at {} not reachable: {}", energyServerUrl, e.getMessage());
        }
    }

    private void discoverEnergyMarketBmadServer(String sessionId) {
        String serverId = "energy-market-server-bmad";
        this.energyBmadSessionId = null;
        try {
            long reqId = messageIdSequence.getAndIncrement();
            McpProtocolMessage initReq = McpProtocolMessage.initializeRequest(reqId, "ai-mini-agent", "1.0.0");
            long startInit = System.currentTimeMillis();
            McpProtocolMessage initResp = sendHttpJsonRpc(energyBmadServerUrl, initReq, null, sId -> this.energyBmadSessionId = sId);
            long durInit = System.currentTimeMillis() - startInit;

            recordWireTraffic(sessionId, serverId, "STREAMABLE_HTTP", "OUTBOUND_REQUEST", "initialize", reqId, toJson(initReq), 0, true, null);
            recordWireTraffic(sessionId, serverId, "STREAMABLE_HTTP", "INBOUND_RESPONSE", "initialize", reqId, toJson(initResp), durInit, true, null);

            // Optional: send notifications/initialized
            McpProtocolMessage notify = McpProtocolMessage.initializedNotification();
            try {
                sendHttpJsonRpc(energyBmadServerUrl, notify, energyBmadSessionId, sId -> this.energyBmadSessionId = sId);
            } catch (Exception ignored) {}

            long listId = messageIdSequence.getAndIncrement();
            McpProtocolMessage listReq = McpProtocolMessage.toolsListRequest(listId);
            long startList = System.currentTimeMillis();
            McpProtocolMessage listResp = sendHttpJsonRpc(energyBmadServerUrl, listReq, energyBmadSessionId, sId -> this.energyBmadSessionId = sId);
            long durList = System.currentTimeMillis() - startList;

            recordWireTraffic(sessionId, serverId, "STREAMABLE_HTTP", "OUTBOUND_REQUEST", "tools/list", listId, toJson(listReq), 0, true, null);
            recordWireTraffic(sessionId, serverId, "STREAMABLE_HTTP", "INBOUND_RESPONSE", "tools/list", listId, toJson(listResp), durList, true, null);

            registerDiscoveredTools(serverId, listResp);
            log.info("[McpClientManager] Successfully connected to Energy Market MCP Server (BMad) at {}", energyBmadServerUrl);
        } catch (Exception e) {
            log.debug("[McpClientManager] Energy Market MCP server (BMad) at {} not reachable: {}", energyBmadServerUrl, e.getMessage());
        }
    }

    private McpProtocolMessage sendHttpJsonRpc(String url, McpProtocolMessage message) throws Exception {
        return sendHttpJsonRpc(url, message, energySessionId, sId -> this.energySessionId = sId);
    }

    private McpProtocolMessage sendHttpJsonRpc(String url, McpProtocolMessage message, String currentSessionId, java.util.function.Consumer<String> sessionConsumer) throws Exception {
        String reqBody = toJson(message);
        var reqBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream, application/json")
                .timeout(Duration.ofSeconds(2))
                .POST(HttpRequest.BodyPublishers.ofString(reqBody));

        if (currentSessionId != null) {
            reqBuilder.header("Mcp-Session-Id", currentSessionId);
        }

        HttpRequest request = reqBuilder.build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= 400) {
            if (sessionConsumer != null) sessionConsumer.accept(null);
            throw new RuntimeException("HTTP " + response.statusCode() + ": " + response.body());
        }

        String newSessionId = response.headers().firstValue("Mcp-Session-Id").orElse(null);
        if (newSessionId != null && sessionConsumer != null) {
            sessionConsumer.accept(newSessionId);
        }

        if (response.body() == null || response.body().isBlank()) {
            return null;
        }

        String jsonBody = extractJsonFromSseOrPlain(response.body());
        return objectMapper.readValue(jsonBody, McpProtocolMessage.class);
    }

    private String extractJsonFromSseOrPlain(String body) {
        if (body == null || body.isBlank()) {
            return "{}";
        }
        String trimmed = body.trim();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            return trimmed;
        }
        for (String line : trimmed.split("\\r?\\n")) {
            String l = line.trim();
            if (l.startsWith("data:")) {
                return l.substring("data:".length()).trim();
            }
        }
        return trimmed;
    }

    @SuppressWarnings("unchecked")
    private void registerDiscoveredTools(String serverId, McpProtocolMessage listResponse) {
        if (listResponse == null || listResponse.result() == null) return;

        Map<String, Object> result = (Map<String, Object>) listResponse.result();
        Object toolsObj = result.get("tools");
        if (!(toolsObj instanceof List<?> toolsList)) return;

        for (Object item : toolsList) {
            if (!(item instanceof Map<?, ?> rawMap)) continue;
            @SuppressWarnings("unchecked")
            Map<String, Object> toolMap = (Map<String, Object>) rawMap;
            String rawName = (String) toolMap.get("name");
            String description = toolMap.get("description") != null ? toolMap.get("description").toString() : "";
            Map<String, Object> inputSchema = (Map<String, Object>) toolMap.get("inputSchema");

            String effectiveName = rawName;
            if (toolToServerMap.containsKey(rawName)) {
                String prefix = serverId.contains("bmad") ? "bmad_" : (serverId.replace("-", "_") + "_");
                effectiveName = prefix + rawName;
                log.info("[McpClientManager] Tool [{}] already registered from [{}], aliasing for [{}] as [{}]",
                        rawName, toolToServerMap.get(rawName), serverId, effectiveName);
            }

            toolToServerMap.put(effectiveName, serverId);
            toolToOriginalNameMap.put(effectiveName, rawName);
            toolRawDefinitions.put(effectiveName, (Map<String, Object>) toolMap);

            JsonObjectSchema parameterSchema = convertJsonSchema(inputSchema);
            ToolSpecification spec = ToolSpecification.builder()
                    .name(effectiveName)
                    .description(description)
                    .parameters(parameterSchema)
                    .build();

            discoveredSpecifications.add(spec);
            log.info("[McpClientManager] Registered MCP tool: [{}] from server [{}]", effectiveName, serverId);
        }
    }

    @SuppressWarnings("unchecked")
    private JsonObjectSchema convertJsonSchema(Map<String, Object> schemaMap) {
        if (schemaMap == null) return JsonObjectSchema.builder().build();

        JsonObjectSchema.Builder builder = JsonObjectSchema.builder();
        String desc = (String) schemaMap.get("description");
        if (desc != null) builder.description(desc);

        Map<String, Object> props = (Map<String, Object>) schemaMap.get("properties");
        if (props != null) {
            props.forEach((propName, propDefObj) -> {
                if (propDefObj instanceof Map<?, ?> propDef) {
                    builder.addProperty(propName, convertPropertySchema((Map<String, Object>) propDef));
                }
            });
        }

        List<String> required = (List<String>) schemaMap.get("required");
        if (required != null) {
            builder.required(required);
        }

        return builder.build();
    }

    @SuppressWarnings("unchecked")
    private JsonSchemaElement convertPropertySchema(Map<String, Object> propDef) {
        String type = (String) propDef.getOrDefault("type", "string");
        String desc = (String) propDef.get("description");

        return switch (type.toLowerCase()) {
            case "integer" -> JsonIntegerSchema.builder().description(desc).build();
            case "number" -> JsonNumberSchema.builder().description(desc).build();
            case "boolean" -> JsonBooleanSchema.builder().description(desc).build();
            case "array" -> {
                JsonArraySchema.Builder b = JsonArraySchema.builder().description(desc);
                Object itemsObj = propDef.get("items");
                if (itemsObj instanceof Map<?, ?> itemsMap) {
                    b.items(convertPropertySchema((Map<String, Object>) itemsMap));
                } else {
                    b.items(JsonStringSchema.builder().build());
                }
                yield b.build();
            }
            default -> JsonStringSchema.builder().description(desc).build();
        };
    }

    public McpToolExecutionResult executeToolCall(String sessionId, String toolName, String argumentsJson) {
        return executeToolCall(sessionId, toolName, argumentsJson, null);
    }

    public McpToolExecutionResult executeToolCall(String sessionId, String toolName, String argumentsJson, com.workshop.agent.model.AgentSession session) {
        String targetServer = toolToServerMap.getOrDefault(toolName, "local-mcp-server");
        String transport = (targetServer.equals("open-meteo-weather") || targetServer.equals("energy-market-server") || targetServer.equals("energy-market-server-bmad")) ? "STREAMABLE_HTTP" : "LOOPBACK";

        if (session != null) {
            if (!session.isMcpToolEnabled(toolName)) {
                String error = "MCP tool '" + toolName + "' is currently disabled for this session.";
                log.warn("[MCP-EXEC-BLOCKED] {}", error);
                return new McpToolExecutionResult(toolName, targetServer, transport, argumentsJson, "{}", "ERROR: " + error, 0, false, error);
            }
            if (!session.isMcpServerEnabled(targetServer)) {
                String error = "MCP server '" + targetServer + "' is currently disabled for this session.";
                log.warn("[MCP-EXEC-BLOCKED] {}", error);
                return new McpToolExecutionResult(toolName, targetServer, transport, argumentsJson, "{}", "ERROR: " + error, 0, false, error);
            }
        }

        long reqId = messageIdSequence.getAndIncrement();
        Object parsedArgs;
        try {
            parsedArgs = objectMapper.readValue(argumentsJson, Map.class);
        } catch (Exception e) {
            try {
                parsedArgs = objectMapper.readTree(argumentsJson);
            } catch (Exception e2) {
                parsedArgs = argumentsJson;
            }
        }

        String wireToolName = toolToOriginalNameMap.getOrDefault(toolName, toolName);
        McpProtocolMessage rpcRequest = McpProtocolMessage.toolsCallRequest(reqId, wireToolName, parsedArgs);
        String requestJson = toJson(rpcRequest);

        long startTime = System.currentTimeMillis();
        recordWireTraffic(sessionId, targetServer, transport, "OUTBOUND_REQUEST", "tools/call", reqId, requestJson, 0, true, null);

        McpProtocolMessage rpcResponse;
        try {
            if ("open-meteo-weather".equals(targetServer)) {
                rpcResponse = openMeteoWeatherServer.handleRequest(rpcRequest);
            } else if ("energy-market-server".equals(targetServer)) {
                rpcResponse = sendHttpJsonRpc(energyServerUrl, rpcRequest, energySessionId, sId -> this.energySessionId = sId);
            } else if ("energy-market-server-bmad".equals(targetServer)) {
                rpcResponse = sendHttpJsonRpc(energyBmadServerUrl, rpcRequest, energyBmadSessionId, sId -> this.energyBmadSessionId = sId);
            } else {
                rpcResponse = localMcpServer.handleRequest(rpcRequest);
            }
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            rpcResponse = McpProtocolMessage.error(reqId, -32603, "Internal MCP error: " + e.getMessage());
            String errRespJson = toJson(rpcResponse);
            recordWireTraffic(sessionId, targetServer, transport, "INBOUND_RESPONSE", "tools/call", reqId, errRespJson, duration, false, e.getMessage());
            return new McpToolExecutionResult(toolName, targetServer, transport, requestJson, errRespJson, "ERROR: " + e.getMessage(), duration, false, e.getMessage());
        }

        long duration = System.currentTimeMillis() - startTime;
        String responseJson = toJson(rpcResponse);
        boolean isSuccess = rpcResponse.error() == null;
        String errorMsg = rpcResponse.error() != null ? rpcResponse.error().message() : null;

        recordWireTraffic(sessionId, targetServer, transport, "INBOUND_RESPONSE", "tools/call", reqId, responseJson, duration, isSuccess, errorMsg);

        // Extract text from result.content[0].text
        String resultText = extractTextContent(rpcResponse);

        return new McpToolExecutionResult(
                toolName,
                targetServer,
                transport,
                requestJson,
                responseJson,
                resultText,
                duration,
                isSuccess,
                errorMsg
        );
    }

    @SuppressWarnings("unchecked")
    private String extractTextContent(McpProtocolMessage resp) {
        if (resp == null) return "null";
        if (resp.error() != null) {
            return "MCP ERROR [" + resp.error().code() + "]: " + resp.error().message();
        }

        Object resObj = resp.result();
        if (resObj instanceof Map<?, ?> m) {
            Object contentObj = m.get("content");
            if (contentObj instanceof List<?> cList && !cList.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (Object item : cList) {
                    if (item instanceof Map<?, ?> itemMap) {
                        String text = (String) itemMap.get("text");
                        if (text != null) sb.append(text).append("\n");
                    }
                }
                return sb.toString().trim();
            }
            return toJson(resObj);
        }
        return String.valueOf(resObj);
    }

    private void recordWireTraffic(
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
        McpWireRecord record = McpWireRecord.of(
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

        sessionTraffic.computeIfAbsent(sessionId, k -> new CopyOnWriteArrayList<>()).add(record);

        // Add to global buffer (keep latest 50)
        recentGlobalTraffic.add(0, record);
        while (recentGlobalTraffic.size() > 50) {
            recentGlobalTraffic.remove(recentGlobalTraffic.size() - 1);
        }

        log.debug("[MCP-WIRE] {} -> {} [{}] ({} ms)", direction, serverName, method, latencyMs);
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return String.valueOf(obj);
        }
    }
}
