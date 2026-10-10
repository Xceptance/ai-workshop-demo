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
    // Standalone Subproject Energy Market Server Endpoint
    private final String energyServerUrl = "http://localhost:8085/mcp";

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

    /**
     * Discovers tools from all active MCP servers and translates them into LangChain4j specifications.
     */
    public synchronized void refreshAllServers(String sessionId) {
        toolToServerMap.clear();
        discoveredSpecifications.clear();
        toolRawDefinitions.clear();

        // 1. Discover from Local MCP Server
        discoverLocalServer(sessionId);

        // 2. Discover from Open-Meteo Weather MCP Server
        discoverOpenMeteoServer(sessionId);

        // 3. Discover from Standalone Energy Market MCP Server (if running)
        discoverEnergyMarketServer(sessionId);

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
        List<McpServerConfig> list = new ArrayList<>();

        // 1. Local Server
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
                "CONNECTED",
                (int) localToolCount,
                localTools,
                2L,
                "Ready for local function execution"
        ));

        // 2. Open-Meteo Weather Server
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
                "CONNECTED",
                (int) meteoToolCount,
                meteoTools,
                120L,
                "100% Free Open Weather MCP Service active"
        ));

        // 3. Remote World Weather Online (Cloud Demo)
        list.add(new McpServerConfig(
                "world-weather-online",
                "World Weather Online (Cloud Remote)",
                "External commercial MCP endpoint on Railway",
                "STREAMABLE_HTTP (POST)",
                remoteWwoUrl,
                "DIAGNOSTIC (404 Offline)",
                0,
                List.of(),
                0L,
                "Remote Railway host returns 404. Live fallback routes to Open-Meteo."
        ));

        // 4. Standalone Energy Market Server (Spring AI 2 Subproject)
        long energyToolCount = toolToServerMap.values().stream().filter(s -> s.equals("energy-market-server")).count();
        List<String> energyTools = toolToServerMap.entrySet().stream()
                .filter(e -> e.getValue().equals("energy-market-server"))
                .map(Map.Entry::getKey)
                .toList();
        list.add(new McpServerConfig(
                "energy-market-server",
                "Energy Market Server (Spring AI 2 / JDK 25)",
                "Spot electricity prices, historical data, renewable forecast & battery arbitrage",
                "STREAMABLE_HTTP (Port 8085)",
                energyServerUrl,
                energyToolCount > 0 ? "CONNECTED" : "STANDALONE_READY",
                (int) energyToolCount,
                energyTools,
                energyToolCount > 0 ? 15L : 0L,
                energyToolCount > 0
                        ? "Active on port 8085 (no auth)"
                        : "Standalone subproject in servers/energy-market-server (start with: mvn spring-boot:run)"
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

    private McpProtocolMessage sendHttpJsonRpc(String url, McpProtocolMessage message) throws Exception {
        String reqBody = toJson(message);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(2))
                .POST(HttpRequest.BodyPublishers.ofString(reqBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return objectMapper.readValue(response.body(), McpProtocolMessage.class);
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
            String name = (String) toolMap.get("name");
            String description = toolMap.get("description") != null ? toolMap.get("description").toString() : "";
            Map<String, Object> inputSchema = (Map<String, Object>) toolMap.get("inputSchema");

            toolToServerMap.put(name, serverId);
            toolRawDefinitions.put(name, (Map<String, Object>) toolMap);

            JsonObjectSchema parameterSchema = convertJsonSchema(inputSchema);
            ToolSpecification spec = ToolSpecification.builder()
                    .name(name)
                    .description(description)
                    .parameters(parameterSchema)
                    .build();

            discoveredSpecifications.add(spec);
            log.info("[McpClientManager] Registered MCP tool: [{}] from server [{}]", name, serverId);
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

    /**
     * Executes a tool call over the Model Context Protocol.
     * Records the raw JSON-RPC request and response payloads with exact wire latency.
     */
    public McpToolExecutionResult executeToolCall(String sessionId, String toolName, String argumentsJson) {
        String targetServer = toolToServerMap.getOrDefault(toolName, "local-mcp-server");
        String transport = targetServer.equals("open-meteo-weather") ? "STREAMABLE_HTTP" : "LOOPBACK";

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

        McpProtocolMessage rpcRequest = McpProtocolMessage.toolsCallRequest(reqId, toolName, parsedArgs);
        String requestJson = toJson(rpcRequest);

        long startTime = System.currentTimeMillis();
        recordWireTraffic(sessionId, targetServer, transport, "OUTBOUND_REQUEST", "tools/call", reqId, requestJson, 0, true, null);

        McpProtocolMessage rpcResponse;
        try {
            if ("open-meteo-weather".equals(targetServer)) {
                rpcResponse = openMeteoWeatherServer.handleRequest(rpcRequest);
            } else if ("energy-market-server".equals(targetServer)) {
                rpcResponse = sendHttpJsonRpc(energyServerUrl, rpcRequest);
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
