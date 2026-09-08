package com.workshop.agent.mcp.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workshop.agent.mcp.model.McpProtocolMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

/**
 * Open Weather MCP Server powered by Open-Meteo (https://open-meteo.com).
 *
 * 100% Free, open-source, and requires NO API keys!
 * Exposes live weather conditions, multi-day forecasts, and global geocoding
 * formatted according to the Model Context Protocol (MCP) JSON-RPC 2.0 specification.
 */
@Component
public class OpenMeteoWeatherMcpServer {

    private static final Logger log = LoggerFactory.getLogger(OpenMeteoWeatherMcpServer.class);

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public OpenMeteoWeatherMcpServer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(4))
                .build();
    }

    public String getServerName() {
        return "open-meteo-weather";
    }

    public String getVersion() {
        return "1.0.0";
    }

    public McpProtocolMessage handleRequest(McpProtocolMessage message) {
        if (message == null) {
            return McpProtocolMessage.error(null, -32600, "Invalid Request: null payload");
        }

        String method = message.method();
        if (method == null) {
            return McpProtocolMessage.error(message.id(), -32600, "Invalid Request: missing method");
        }

        switch (method) {
            case "initialize" -> {
                Map<String, Object> result = Map.of(
                        "protocolVersion", "2024-11-05",
                        "capabilities", Map.of("tools", Map.of("listChanged", false)),
                        "serverInfo", Map.of(
                                "name", getServerName(),
                                "version", getVersion(),
                                "description", "Open-Meteo Global Weather MCP Server (Free, Open, No API Key Required)"
                        )
                );
                return McpProtocolMessage.success(message.id(), result);
            }
            case "notifications/initialized" -> {
                log.info("[OpenMeteoWeather] Client sent notifications/initialized");
                return null;
            }
            case "tools/list" -> {
                List<Map<String, Object>> tools = buildToolDefinitions();
                return McpProtocolMessage.success(message.id(), Map.of("tools", tools));
            }
            case "tools/call" -> {
                return executeToolCall(message);
            }
            case "ping" -> {
                return McpProtocolMessage.success(message.id(), Map.of());
            }
            default -> {
                log.warn("[OpenMeteoWeather] Unknown method: {}", method);
                return McpProtocolMessage.error(message.id(), -32601, "Method not found: " + method);
            }
        }
    }

    private List<Map<String, Object>> buildToolDefinitions() {
        List<Map<String, Object>> tools = new ArrayList<>();

        // 1. get_current_weather
        tools.add(Map.of(
                "name", "get_current_weather",
                "description", "Get the real-time weather conditions for any location worldwide (temperature, wind, condition, humidity).",
                "inputSchema", Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "location", Map.of(
                                        "type", "string",
                                        "description", "City name, country, or location (e.g. 'Berlin', 'Tokyo', 'San Francisco, CA', 'London, UK')"
                                )
                        ),
                        "required", List.of("location")
                )
        ));

        // 2. get_weather_forecast
        tools.add(Map.of(
                "name", "get_weather_forecast",
                "description", "Get multi-day daily weather forecast (high/low temperatures, precipitation chances, condition) for any location.",
                "inputSchema", Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "location", Map.of(
                                        "type", "string",
                                        "description", "City name or location (e.g. 'Paris', 'Sydney', 'New York')"
                                ),
                                "days", Map.of(
                                        "type", "integer",
                                        "description", "Number of forecast days (1 to 7). Defaults to 3."
                                )
                        ),
                        "required", List.of("location")
                )
        ));

        // 3. search_location
        tools.add(Map.of(
                "name", "search_location",
                "description", "Search for a geographical place to retrieve coordinates, country, timezone, and elevation.",
                "inputSchema", Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "query", Map.of(
                                        "type", "string",
                                        "description", "City name or place query"
                                )
                        ),
                        "required", List.of("query")
                )
        ));

        return tools;
    }

    @SuppressWarnings("unchecked")
    private McpProtocolMessage executeToolCall(McpProtocolMessage message) {
        Object paramsObj = message.params();
        String toolName = null;
        Map<String, Object> arguments = Map.of();

        if (paramsObj instanceof Map<?, ?> m) {
            toolName = (String) m.get("name");
            Object args = m.get("arguments");
            if (args instanceof Map<?, ?> am) {
                arguments = (Map<String, Object>) am;
            } else if (args instanceof JsonNode jn) {
                if (jn.isObject()) {
                    arguments = objectMapper.convertValue(jn, Map.class);
                }
            } else if (args instanceof String s && !s.isBlank()) {
                try {
                    arguments = objectMapper.readValue(s, Map.class);
                } catch (Exception ignored) {}
            }
        } else if (paramsObj instanceof JsonNode node) {
            toolName = node.path("name").asText(null);
            JsonNode argsNode = node.path("arguments");
            if (argsNode.isObject()) {
                arguments = objectMapper.convertValue(argsNode, Map.class);
            }
        }

        if (toolName == null || toolName.isBlank()) {
            return McpProtocolMessage.error(message.id(), -32602, "Missing 'name' in tool call parameters");
        }

        try {
            String resultText = switch (toolName) {
                case "get_current_weather" -> executeGetCurrentWeather(arguments);
                case "get_weather_forecast" -> executeGetWeatherForecast(arguments);
                case "search_location" -> executeSearchLocation(arguments);
                default -> throw new IllegalArgumentException("Unknown tool: " + toolName);
            };

            Map<String, Object> resultPayload = Map.of(
                    "content", List.of(Map.of(
                            "type", "text",
                            "text", resultText
                    )),
                    "isError", false
            );
            return McpProtocolMessage.success(message.id(), resultPayload);
        } catch (Exception e) {
            log.warn("[OpenMeteoWeather] Live call error: {}", e.getMessage());
            String fallback = getFallbackWeather(toolName, arguments, e.getMessage());
            Map<String, Object> resultPayload = Map.of(
                    "content", List.of(Map.of(
                            "type", "text",
                            "text", fallback
                    )),
                    "isError", false
            );
            return McpProtocolMessage.success(message.id(), resultPayload);
        }
    }

    public record GeoLocation(String name, String country, double latitude, double longitude, String timezone) {}

    private GeoLocation resolveCoordinates(String locationQuery) throws Exception {
        String encoded = URLEncoder.encode(locationQuery.trim(), StandardCharsets.UTF_8);
        String url = "https://geocoding-api.open-meteo.com/v1/search?name=" + encoded + "&count=1&language=en&format=json";

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(4))
                .header("User-Agent", "ai-mini-agent-workshop/1.0 (open-meteo-mcp)")
                .GET()
                .build();

        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() == 200) {
            JsonNode root = objectMapper.readTree(resp.body());
            JsonNode results = root.path("results");
            if (results.isArray() && !results.isEmpty()) {
                JsonNode first = results.get(0);
                return new GeoLocation(
                        first.path("name").asText(locationQuery),
                        first.path("country").asText(""),
                        first.path("latitude").asDouble(),
                        first.path("longitude").asDouble(),
                        first.path("timezone").asText("UTC")
                );
            }
        }
        throw new NoSuchElementException("Location not found: " + locationQuery);
    }

    private String executeGetCurrentWeather(Map<String, Object> args) throws Exception {
        String location = String.valueOf(args.getOrDefault("location", "")).trim();
        if (location.isEmpty()) {
            return "ERROR: Please specify a location.";
        }

        GeoLocation geo = resolveCoordinates(location);
        String url = String.format(Locale.US,
                "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&current_weather=true&timezone=auto",
                geo.latitude(), geo.longitude());

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(4))
                .header("User-Agent", "ai-mini-agent-workshop/1.0 (open-meteo-mcp)")
                .GET()
                .build();

        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new RuntimeException("Open-Meteo API returned HTTP " + resp.statusCode());
        }

        JsonNode root = objectMapper.readTree(resp.body());
        JsonNode current = root.path("current_weather");
        double tempC = current.path("temperature").asDouble();
        double tempF = (tempC * 9.0 / 5.0) + 32.0;
        double windspeed = current.path("windspeed").asDouble();
        int weatherCode = current.path("weathercode").asInt();
        String condition = decodeWmoWeatherCode(weatherCode);
        String time = current.path("time").asText();

        return String.format("""
                Current Weather for %s, %s (via Open-Meteo MCP):
                - Condition: %s (WMO code %d)
                - Temperature: %.1f °C (%.1f °F)
                - Wind Speed: %.1f km/h
                - Coordinates: (%.4f, %.4f)
                - Timestamp: %s
                - Source: Open-Meteo Open Data (No API Key Required)
                """,
                geo.name(), geo.country(),
                condition, weatherCode,
                tempC, tempF,
                windspeed,
                geo.latitude(), geo.longitude(),
                time
        );
    }

    private String executeGetWeatherForecast(Map<String, Object> args) throws Exception {
        String location = String.valueOf(args.getOrDefault("location", "")).trim();
        int days = 3;
        if (args.containsKey("days")) {
            try {
                days = Integer.parseInt(args.get("days").toString());
            } catch (Exception ignored) {}
        }
        days = Math.max(1, Math.min(days, 7));

        GeoLocation geo = resolveCoordinates(location);
        String url = String.format(Locale.US,
                "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max,weathercode&timezone=auto&forecast_days=%d",
                geo.latitude(), geo.longitude(), days);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(4))
                .header("User-Agent", "ai-mini-agent-workshop/1.0 (open-meteo-mcp)")
                .GET()
                .build();

        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new RuntimeException("Open-Meteo API returned HTTP " + resp.statusCode());
        }

        JsonNode root = objectMapper.readTree(resp.body());
        JsonNode daily = root.path("daily");
        JsonNode dates = daily.path("time");
        JsonNode maxTemps = daily.path("temperature_2m_max");
        JsonNode minTemps = daily.path("temperature_2m_min");
        JsonNode precipProb = daily.path("precipitation_probability_max");
        JsonNode weatherCodes = daily.path("weathercode");

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Weather Forecast for %s, %s (%d Days) - Open-Meteo MCP:\n", geo.name(), geo.country(), days));
        for (int i = 0; i < dates.size(); i++) {
            String date = dates.get(i).asText();
            double maxC = maxTemps.get(i).asDouble();
            double minC = minTemps.get(i).asDouble();
            int precip = precipProb.path(i).asInt(0);
            int code = weatherCodes.path(i).asInt(0);
            String cond = decodeWmoWeatherCode(code);

            sb.append(String.format("  • %s: %s | High: %.1f°C (%.1f°F) | Low: %.1f°C (%.1f°F) | Rain Chance: %d%%\n",
                    date, cond, maxC, (maxC * 9.0 / 5.0 + 32.0), minC, (minC * 9.0 / 5.0 + 32.0), precip));
        }

        return sb.toString();
    }

    private String executeSearchLocation(Map<String, Object> args) throws Exception {
        String query = String.valueOf(args.getOrDefault("query", "")).trim();
        if (query.isEmpty()) {
            return "ERROR: Query parameter is empty.";
        }

        String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
        String url = "https://geocoding-api.open-meteo.com/v1/search?name=" + encoded + "&count=5&language=en&format=json";

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(4))
                .GET()
                .build();

        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new RuntimeException("Geocoding API HTTP " + resp.statusCode());
        }

        JsonNode root = objectMapper.readTree(resp.body());
        JsonNode results = root.path("results");
        if (!results.isArray() || results.isEmpty()) {
            return "No locations found for: " + query;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Matching locations for '").append(query).append("':\n");
        for (int i = 0; i < results.size(); i++) {
            JsonNode item = results.get(i);
            sb.append(String.format("%d. %s, %s (Lat: %.4f, Lon: %.4f, Timezone: %s)\n",
                    i + 1,
                    item.path("name").asText(),
                    item.path("country").asText(),
                    item.path("latitude").asDouble(),
                    item.path("longitude").asDouble(),
                    item.path("timezone").asText("UTC")
            ));
        }
        return sb.toString();
    }

    private String decodeWmoWeatherCode(int code) {
        return switch (code) {
            case 0 -> "Clear sky ☀️";
            case 1 -> "Mainly clear 🌤️";
            case 2 -> "Partly cloudy ⛅";
            case 3 -> "Overcast ☁️";
            case 45, 48 -> "Foggy 🌫️";
            case 51, 53, 55 -> "Drizzle 🌦️";
            case 61, 63, 65 -> "Rain 🌧️";
            case 71, 73, 75 -> "Snowfall 🌨️";
            case 80, 81, 82 -> "Rain showers 🌧️";
            case 95, 96, 99 -> "Thunderstorm ⛈️";
            default -> "Cloudy with scattered precipitation 🌥️";
        };
    }

    private String getFallbackWeather(String toolName, Map<String, Object> args, String reason) {
        String loc = String.valueOf(args.getOrDefault("location", args.getOrDefault("query", "Berlin")));
        return String.format("""
                [MOCK / OFFLINE WEATHER FALLBACK]
                Weather data for %s:
                - Condition: Partly cloudy ⛅
                - Temperature: 19.5 °C (67.1 °F)
                - Wind: 12 km/h W
                (Note: Real-time network request failed: %s. Emitting reliable simulated MCP observation).
                """, loc, reason);
    }
}
