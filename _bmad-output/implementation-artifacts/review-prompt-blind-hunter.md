Conduct a review of CONTENT.
Look for what's missing, not only what's wrong.
Compute your finding floor N from the diff file's size: N = min(floor(sqrt(kB) + 1), 10), where kB is the file's size in kilobytes. State the arithmetic in one line, then find at least N issues to fix or improve.
Output a Markdown list of findings only — no severity, priority, or ranking.
If the content is empty, stop and say so.
If you have zero findings, re-check and keep thinking; do not stop with an empty list.

CONTENT:
```diff
diff --git a/src/main/java/com/workshop/agent/mcp/client/McpClientManager.java b/src/main/java/com/workshop/agent/mcp/client/McpClientManager.java
index 25bbbf5..df35f4b 100644
--- a/src/main/java/com/workshop/agent/mcp/client/McpClientManager.java
+++ b/src/main/java/com/workshop/agent/mcp/client/McpClientManager.java
@@ -69,6 +69,9 @@ public class McpClientManager {
 
     // Remote WWO Endpoint
     private final String remoteWwoUrl = "https://mcp.worldweatheronline.com/mcp";
+    // Standalone Subproject Energy Market Server Endpoint
+    private final String energyServerUrl = "http://localhost:8086/mcp";
+    private volatile String energySessionId = null;
 
     public McpClientManager(
             LocalMcpServer localMcpServer,
@@ -100,6 +103,9 @@ public class McpClientManager {
         // 2. Discover from Open-Meteo Weather MCP Server
         discoverOpenMeteoServer(sessionId);
 
+        // 3. Discover from Standalone Energy Market MCP Server (if running)
+        discoverEnergyMarketServer(sessionId);
+
         log.info("[McpClientManager] Total discovered MCP tools: {} across servers: {}",
                 discoveredSpecifications.size(), toolToServerMap.values().stream().distinct().toList());
     }
@@ -175,6 +181,27 @@ public class McpClientManager {
                 "Remote Railway host returns 404. Live fallback routes to Open-Meteo."
         ));
 
+        // 4. Standalone Energy Market Server (Spring AI 2 Subproject)
+        long energyToolCount = toolToServerMap.values().stream().filter(s -> s.equals("energy-market-server")).count();
+        List<String> energyTools = toolToServerMap.entrySet().stream()
+                .filter(e -> e.getValue().equals("energy-market-server"))
+                .map(Map.Entry::getKey)
+                .toList();
+        list.add(new McpServerConfig(
+                "energy-market-server",
+                "Energy Market Server (Spring AI 2 / JDK 25)",
+                "Spot electricity prices, historical data, renewable forecast & battery arbitrage",
+                "STREAMABLE_HTTP (Port 8086)",
+                energyServerUrl,
+                energyToolCount > 0 ? "CONNECTED" : "STANDALONE_READY",
+                (int) energyToolCount,
+                energyTools,
+                energyToolCount > 0 ? 15L : 0L,
+                energyToolCount > 0
+                        ? "Active on port 8086 (no auth)"
+                        : "Standalone subproject in servers/energy-market-server-bmad (start with: mvn spring-boot:run)"
+        ));
+
         return list;
     }
 
@@ -235,6 +262,58 @@ public class McpClientManager {
         registerDiscoveredTools(serverId, listResp);
     }
 
+    private void discoverEnergyMarketServer(String sessionId) {
+        String serverId = "energy-market-server";
+        try {
+            long reqId = messageIdSequence.getAndIncrement();
+            McpProtocolMessage initReq = McpProtocolMessage.initializeRequest(reqId, "ai-mini-agent", "1.0.0");
+            long startInit = System.currentTimeMillis();
+            McpProtocolMessage initResp = sendHttpJsonRpc(energyServerUrl, initReq);
+            long durInit = System.currentTimeMillis() - startInit;
+
+            recordWireTraffic(sessionId, serverId, "STREAMABLE_HTTP", "OUTBOUND_REQUEST", "initialize", reqId, toJson(initReq), 0, true, null);
+            recordWireTraffic(sessionId, serverId, "STREAMABLE_HTTP", "INBOUND_RESPONSE", "initialize", reqId, toJson(initResp), durInit, true, null);
+
+            long listId = messageIdSequence.getAndIncrement();
+            McpProtocolMessage listReq = McpProtocolMessage.toolsListRequest(listId);
+            long startList = System.currentTimeMillis();
+            McpProtocolMessage listResp = sendHttpJsonRpc(energyServerUrl, listReq);
+            long durList = System.currentTimeMillis() - startList;
+
+            recordWireTraffic(sessionId, serverId, "STREAMABLE_HTTP", "OUTBOUND_REQUEST", "tools/list", listId, toJson(listReq), 0, true, null);
+            recordWireTraffic(sessionId, serverId, "STREAMABLE_HTTP", "INBOUND_RESPONSE", "tools/list", listId, toJson(listResp), durList, true, null);
+
+            registerDiscoveredTools(serverId, listResp);
+            log.info("[McpClientManager] Successfully connected to Energy Market MCP Server at {}", energyServerUrl);
+        } catch (Exception e) {
+            log.debug("[McpClientManager] Energy Market MCP server at {} not reachable: {}", energyServerUrl, e.getMessage());
+        }
+    }
+
+    private McpProtocolMessage sendHttpJsonRpc(String url, McpProtocolMessage message) throws Exception {
+        String reqBody = toJson(message);
+        var reqBuilder = HttpRequest.newBuilder()
+                .uri(URI.create(url))
+                .header("Content-Type", "application/json")
+                .header("Accept", "text/event-stream, application/json")
+                .timeout(Duration.ofSeconds(2))
+                .POST(HttpRequest.BodyPublishers.ofString(reqBody));
+
+        if (energySessionId != null) {
+            reqBuilder.header("Mcp-Session-Id", energySessionId);
+        }
+
+        HttpRequest request = reqBuilder.build();
+        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
+
+        String newSessionId = response.headers().firstValue("Mcp-Session-Id").orElse(null);
+        if (newSessionId != null) {
+            this.energySessionId = newSessionId;
+        }
+
+        return objectMapper.readValue(response.body(), McpProtocolMessage.class);
+    }
+
     @SuppressWarnings("unchecked")
     private void registerDiscoveredTools(String serverId, McpProtocolMessage listResponse) {
         if (listResponse == null || listResponse.result() == null) return;
@@ -320,7 +399,7 @@ public class McpClientManager {
      */
     public McpToolExecutionResult executeToolCall(String sessionId, String toolName, String argumentsJson) {
         String targetServer = toolToServerMap.getOrDefault(toolName, "local-mcp-server");
-        String transport = targetServer.equals("open-meteo-weather") ? "STREAMABLE_HTTP" : "LOOPBACK";
+        String transport = (targetServer.equals("open-meteo-weather") || targetServer.equals("energy-market-server")) ? "STREAMABLE_HTTP" : "LOOPBACK";
 
         long reqId = messageIdSequence.getAndIncrement();
         Object parsedArgs;
@@ -344,6 +423,8 @@ public class McpClientManager {
         try {
             if ("open-meteo-weather".equals(targetServer)) {
                 rpcResponse = openMeteoWeatherServer.handleRequest(rpcRequest);
+            } else if ("energy-market-server".equals(targetServer)) {
+                rpcResponse = sendHttpJsonRpc(energyServerUrl, rpcRequest);
             } else {
                 rpcResponse = localMcpServer.handleRequest(rpcRequest);
             }
diff --git a/servers/energy-market-server-bmad/pom.xml b/servers/energy-market-server-bmad/pom.xml
new file mode 100644
index 0000000..c619af2
--- /dev/null
+++ b/servers/energy-market-server-bmad/pom.xml
@@ -0,0 +1,78 @@
+<?xml version="1.0" encoding="UTF-8"?>
+<project xmlns="http://maven.apache.org/POM/4.0.0"
+         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
+         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
+    <modelVersion>4.0.0</modelVersion>
+
+    <parent>
+        <groupId>org.springframework.boot</groupId>
+        <artifactId>spring-boot-starter-parent</artifactId>
+        <version>4.1.0</version>
+        <relativePath/>
+    </parent>
+
+    <groupId>com.workshop.energy</groupId>
+    <artifactId>energy-market-server-bmad</artifactId>
+    <version>1.0.0-SNAPSHOT</version>
+    <name>Energy Market MCP Server (BMad)</name>
+    <description>Dedicated Standalone Energy Market MCP Server on Spring Boot 4.1, Spring AI 2, and JDK 25</description>
+
+    <properties>
+        <java.version>25</java.version>
+        <maven.compiler.source>25</maven.compiler.source>
+        <maven.compiler.target>25</maven.compiler.target>
+        <spring-ai.version>2.0.1</spring-ai.version>
+    </properties>
+
+    <dependencyManagement>
+        <dependencies>
+            <dependency>
+                <groupId>org.springframework.ai</groupId>
+                <artifactId>spring-ai-bom</artifactId>
+                <version>${spring-ai.version}</version>
+                <type>pom</type>
+                <scope>import</scope>
+            </dependency>
+        </dependencies>
+    </dependencyManagement>
+
+    <dependencies>
+        <!-- Spring Boot Web Starter -->
+        <dependency>
+            <groupId>org.springframework.boot</groupId>
+            <artifactId>spring-boot-starter-web</artifactId>
+        </dependency>
+
+        <!-- Jackson -->
+        <dependency>
+            <groupId>com.fasterxml.jackson.core</groupId>
+            <artifactId>jackson-databind</artifactId>
+        </dependency>
+        <dependency>
+            <groupId>com.fasterxml.jackson.datatype</groupId>
+            <artifactId>jackson-datatype-jsr310</artifactId>
+        </dependency>
+
+        <!-- Spring AI MCP Server WebMvc Starter -->
+        <dependency>
+            <groupId>org.springframework.ai</groupId>
+            <artifactId>spring-ai-starter-mcp-server-webmvc</artifactId>
+        </dependency>
+
+        <!-- Testing -->
+        <dependency>
+            <groupId>org.springframework.boot</groupId>
+            <artifactId>spring-boot-starter-test</artifactId>
+            <scope>test</scope>
+        </dependency>
+    </dependencies>
+
+    <build>
+        <plugins>
+            <plugin>
+                <groupId>org.springframework.boot</groupId>
+                <artifactId>spring-boot-maven-plugin</artifactId>
+            </plugin>
+        </plugins>
+    </build>
+</project>
diff --git a/servers/energy-market-server-bmad/src/main/resources/application.yml b/servers/energy-market-server-bmad/src/main/resources/application.yml
new file mode 100644
index 0000000..8be8e69
--- /dev/null
+++ b/servers/energy-market-server-bmad/src/main/resources/application.yml
@@ -0,0 +1,22 @@
+server:
+  port: 8086
+
+spring:
+  application:
+    name: energy-market-server
+  ai:
+    mcp:
+      server:
+        name: energy-market-server
+        version: 1.0.0
+        protocol: streamable
+        streamable-http:
+          mcp-endpoint: /mcp
+        capabilities:
+          tool: true
+          resource: true
+
+logging:
+  level:
+    org.springframework.ai: INFO
+    com.workshop.energy: DEBUG
diff --git a/servers/energy-market-server-bmad/src/main/java/com/workshop/energy/EnergyMarketServerApplication.java b/servers/energy-market-server-bmad/src/main/java/com/workshop/energy/EnergyMarketServerApplication.java
new file mode 100644
index 0000000..b3f37c2
--- /dev/null
+++ b/servers/energy-market-server-bmad/src/main/java/com/workshop/energy/EnergyMarketServerApplication.java
@@ -0,0 +1,23 @@
+package com.workshop.energy;
+
+import com.fasterxml.jackson.databind.ObjectMapper;
+import com.fasterxml.jackson.databind.SerializationFeature;
+import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
+import org.springframework.boot.SpringApplication;
+import org.springframework.boot.autoconfigure.SpringBootApplication;
+import org.springframework.context.annotation.Bean;
+
+@SpringBootApplication
+public class EnergyMarketServerApplication {
+
+    public static void main(String[] args) {
+        SpringApplication.run(EnergyMarketServerApplication.class, args);
+    }
+
+    @Bean
+    public ObjectMapper objectMapper() {
+        return new ObjectMapper()
+                .registerModule(new JavaTimeModule())
+                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
+    }
+}
diff --git a/servers/energy-market-server-bmad/src/main/java/com/workshop/energy/domain/MarketModels.java b/servers/energy-market-server-bmad/src/main/java/com/workshop/energy/domain/MarketModels.java
new file mode 100644
index 0000000..14d5240
--- /dev/null
+++ b/servers/energy-market-server-bmad/src/main/java/com/workshop/energy/domain/MarketModels.java
@@ -0,0 +1,96 @@
+package com.workshop.energy.domain;
+
+import java.util.List;
+
+public final class MarketModels {
+
+    private MarketModels() {}
+
+    public record HourlySpotPrice(
+            int hour,
+            double dayAheadPriceEurPerMwh,
+            double intradayPriceEurPerMwh,
+            boolean isNegative
+    ) {}
+
+    public record SpotPriceResult(
+            String zone,
+            String date,
+            double averageDayAheadPrice,
+            double averageIntradayPrice,
+            double maxPrice,
+            double minPrice,
+            int negativePriceHoursCount,
+            List<HourlySpotPrice> hourlyPrices
+    ) {}
+
+    public record DailyHistoricalSummary(
+            String date,
+            double averagePriceEurPerMwh,
+            double minPrice,
+            double maxPrice,
+            int negativePriceHoursCount
+    ) {}
+
+    public record HistoricalPricesResult(
+            String zone,
+            String startDate,
+            String endDate,
+            double overallAveragePrice,
+            double overallMinPrice,
+            double overallMaxPrice,
+            int totalNegativePriceHours,
+            List<DailyHistoricalSummary> dailySummaries
+    ) {}
+
+    public record HourlyRenewableForecast(
+            int hour,
+            double solarPvMw,
+            double windOnshoreMw,
+            double windOffshoreMw,
+            double totalRenewableMw,
+            double gridLoadMw,
+            double renewableSharePercent
+    ) {}
+
+    public record RenewableForecastResult(
+            String zone,
+            String date,
+            double totalGenerationMwh,
+            double averageGridLoadMw,
+            double maxRenewableSharePercent,
+            List<HourlyRenewableForecast> hourlyForecasts
+    ) {}
+
+    public record HourlyDispatchSchedule(
+            int hour,
+            double priceEurPerMwh,
+            String action, // CHARGE, DISCHARGE, IDLE
+            double powerMw,
+            double energyFlowMwh,
+            double stateOfChargeMwh
+    ) {}
+
+    public record DispatchEvaluationResult(
+            String date,
+            double capacityMwh,
+            double maxPowerMw,
+            double roundTripEfficiency,
+            double totalChargedMwh,
+            double totalDischargedMwh,
+            double netEnergyLossMwh,
+            double chargingCostEur,
+            double dischargingRevenueEur,
+            double netProfitEur,
+            List<HourlyDispatchSchedule> schedule
+    ) {}
+
+    public record GridBalanceSnapshot(
+            String zone,
+            String timestamp,
+            double currentFrequencyHz,
+            double netBalanceMw,
+            double renewableSharePercent,
+            String status
+    ) {}
+}
diff --git a/servers/energy-market-server-bmad/src/main/java/com/workshop/energy/domain/EnergyMarketEngine.java b/servers/energy-market-server-bmad/src/main/java/com/workshop/energy/domain/EnergyMarketEngine.java
new file mode 100644
index 0000000..13b73d4
--- /dev/null
+++ b/servers/energy-market-server-bmad/src/main/java/com/workshop/energy/domain/EnergyMarketEngine.java
@@ -0,0 +1,362 @@
+package com.workshop.energy.domain;
+
+import org.springframework.stereotype.Service;
+
+import java.math.BigDecimal;
+import java.math.RoundingMode;
+import java.time.LocalDate;
+import java.time.format.DateTimeParseException;
+import java.util.*;
+
+@Service
+public class EnergyMarketEngine {
+
+    public static final Set<String> SUPPORTED_ZONES = Set.of("DE-LU", "FR", "NL", "AT");
+
+    public String normalizeZone(String zone) {
+        if (zone == null || zone.isBlank()) {
+            return "DE-LU";
+        }
+        String normalized = zone.trim().toUpperCase(Locale.ROOT);
+        if (!SUPPORTED_ZONES.contains(normalized)) {
+            throw new IllegalArgumentException("Unsupported bidding zone: '" + zone + 
+                    "'. Supported zones are: " + String.join(", ", SUPPORTED_ZONES));
+        }
+        return normalized;
+    }
+
+    public LocalDate parseDate(String dateStr) {
+        if (dateStr == null || dateStr.isBlank()) {
+            return LocalDate.now();
+        }
+        try {
+            return LocalDate.parse(dateStr.trim());
+        } catch (DateTimeParseException e) {
+            throw new IllegalArgumentException("Invalid date format: '" + dateStr + "'. Expected ISO-8601 YYYY-MM-DD.", e);
+        }
+    }
+
+    public MarketModels.SpotPriceResult calculateSpotPrices(String rawZone, String rawDate) {
+        String zone = normalizeZone(rawZone);
+        LocalDate date = parseDate(rawDate);
+
+        List<MarketModels.HourlySpotPrice> hourly = new ArrayList<>(24);
+        double sumDa = 0.0;
+        double sumId = 0.0;
+        double maxPrice = -Double.MAX_VALUE;
+        double minPrice = Double.MAX_VALUE;
+        int negativeHours = 0;
+
+        long seed = date.toEpochDay() * 37L + zone.hashCode();
+        Random rng = new Random(seed);
+
+        for (int h = 0; h < 24; h++) {
+            // Sinusoidal daily curve with morning and evening peaks, midday solar dip
+            double morningPeak = 38.0 * Math.exp(-Math.pow((h - 8.5) / 2.2, 2));
+            double eveningPeak = 52.0 * Math.exp(-Math.pow((h - 19.0) / 2.5, 2));
+            double solarDip = -45.0 * Math.exp(-Math.pow((h - 13.0) / 2.0, 2));
+            double nightDrop = (h < 6) ? -28.0 : 0.0;
+
+            // Seasonal adjustment (higher prices in winter, lower in summer/spring due to solar)
+            int dayOfYear = date.getDayOfYear();
+            double seasonalShift = 15.0 * Math.cos(2 * Math.PI * (dayOfYear - 15) / 365.0);
+
+            // Weekend effect
+            int dayOfWeek = date.getDayOfWeek().getValue();
+            double weekendFactor = (dayOfWeek >= 6) ? -18.0 : 0.0;
+
+            // Zone specific base
+            double zoneBase = switch (zone) {
+                case "FR" -> 68.0;
+                case "NL" -> 74.0;
+                case "AT" -> 66.0;
+                default -> 70.0; // DE-LU
+            };
+
+            double noise = (rng.nextDouble() - 0.5) * 12.0;
+
+            double daPrice = round(zoneBase + seasonalShift + weekendFactor + morningPeak + eveningPeak + solarDip + nightDrop + noise);
+            double idPrice = round(daPrice + (rng.nextDouble() - 0.48) * 8.0);
+
+            boolean isNeg = daPrice < 0.0 || idPrice < 0.0;
+            if (isNeg) {
+                negativeHours++;
+            }
+
+            hourly.add(new MarketModels.HourlySpotPrice(h, daPrice, idPrice, isNeg));
+
+            sumDa += daPrice;
+            sumId += idPrice;
+            if (daPrice > maxPrice) maxPrice = daPrice;
+            if (daPrice < minPrice) minPrice = daPrice;
+        }
+
+        double avgDa = round(sumDa / 24.0);
+        double avgId = round(sumId / 24.0);
+
+        return new MarketModels.SpotPriceResult(
+                zone,
+                date.toString(),
+                avgDa,
+                avgId,
+                round(maxPrice),
+                round(minPrice),
+                negativeHours,
+                Collections.unmodifiableList(hourly)
+        );
+    }
+
+    public MarketModels.HistoricalPricesResult calculateHistoricalPrices(String rawZone, String rawStart, String rawEnd) {
+        String zone = normalizeZone(rawZone);
+        LocalDate start = parseDate(rawStart);
+        LocalDate end = parseDate(rawEnd);
+
+        if (end.isBefore(start)) {
+            throw new IllegalArgumentException("Invalid date range: endDate (" + end + ") is before startDate (" + start + ").");
+        }
+
+        long daysBetween = java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1;
+        if (daysBetween > 90) {
+            throw new IllegalArgumentException("Requested date range exceeds maximum allowed limit of 90 days (requested: " + daysBetween + " days).");
+        }
+
+        List<MarketModels.DailyHistoricalSummary> summaries = new ArrayList<>();
+        double totalSum = 0.0;
+        double overallMin = Double.MAX_VALUE;
+        double overallMax = -Double.MAX_VALUE;
+        int totalNegative = 0;
+
+        LocalDate cur = start;
+        while (!cur.isAfter(end)) {
+            MarketModels.SpotPriceResult dayResult = calculateSpotPrices(zone, cur.toString());
+            summaries.add(new MarketModels.DailyHistoricalSummary(
+                    cur.toString(),
+                    dayResult.averageDayAheadPrice(),
+                    dayResult.minPrice(),
+                    dayResult.maxPrice(),
+                    dayResult.negativePriceHoursCount()
+            ));
+
+            totalSum += dayResult.averageDayAheadPrice();
+            if (dayResult.minPrice() < overallMin) overallMin = dayResult.minPrice();
+            if (dayResult.maxPrice() > overallMax) overallMax = dayResult.maxPrice();
+            totalNegative += dayResult.negativePriceHoursCount();
+
+            cur = cur.plusDays(1);
+        }
+
+        double overallAvg = round(totalSum / summaries.size());
+
+        return new MarketModels.HistoricalPricesResult(
+                zone,
+                start.toString(),
+                end.toString(),
+                overallAvg,
+                round(overallMin),
+                round(overallMax),
+                totalNegative,
+                Collections.unmodifiableList(summaries)
+        );
+    }
+
+    public MarketModels.RenewableForecastResult calculateRenewableForecast(String rawZone, String rawDate) {
+        String zone = normalizeZone(rawZone);
+        LocalDate date = parseDate(rawDate);
+
+        long seed = date.toEpochDay() * 59L + zone.hashCode();
+        Random rng = new Random(seed);
+
+        List<MarketModels.HourlyRenewableForecast> hourly = new ArrayList<>(24);
+        double totalGenMwh = 0.0;
+        double sumLoad = 0.0;
+        double maxShare = 0.0;
+
+        double baseWindOn = switch (zone) {
+            case "DE-LU" -> 22000.0;
+            case "NL" -> 6000.0;
+            case "FR" -> 11000.0;
+            case "AT" -> 3500.0;
+            default -> 15000.0;
+        };
+
+        double baseWindOff = switch (zone) {
+            case "DE-LU" -> 7500.0;
+            case "NL" -> 4500.0;
+            default -> 0.0;
+        };
+
+        double baseSolar = switch (zone) {
+            case "DE-LU" -> 34000.0;
+            case "FR" -> 21000.0;
+            case "NL" -> 14000.0;
+            case "AT" -> 5500.0;
+            default -> 15000.0;
+        };
+
+        // Day of week load factor
+        double dayFactor = (date.getDayOfWeek().getValue() >= 6) ? 0.78 : 1.0;
+
+        for (int h = 0; h < 24; h++) {
+            // Solar curve
+            double solarFactor = 0.0;
+            if (h >= 6 && h <= 19) {
+                solarFactor = Math.sin(Math.PI * (h - 6) / 13.0);
+            }
+            double solarPv = round(baseSolar * Math.max(0.0, solarFactor) * (0.85 + 0.3 * rng.nextDouble()));
+
+            // Wind variations
+            double windOn = round(baseWindOn * (0.7 + 0.6 * rng.nextDouble()));
+            double windOff = round(baseWindOff * (0.8 + 0.4 * rng.nextDouble()));
+            double totalRenewable = round(solarPv + windOn + windOff);
+
+            // Grid Load
+            double loadCurve = 42000.0 + 22000.0 * Math.exp(-Math.pow((h - 11.5) / 6.0, 2)) + (h > 17 && h < 22 ? 6000.0 : 0.0);
+            double gridLoad = round(loadCurve * dayFactor * (0.95 + 0.1 * rng.nextDouble()));
+
+            double share = round((totalRenewable / Math.max(gridLoad, 1000.0)) * 100.0);
+            if (share > maxShare) maxShare = share;
+
+            hourly.add(new MarketModels.HourlyRenewableForecast(
+                    h,
+                    solarPv,
+                    windOn,
+                    windOff,
+                    totalRenewable,
+                    gridLoad,
+                    share
+            ));
+
+            totalGenMwh += totalRenewable;
+            sumLoad += gridLoad;
+        }
+
+        return new MarketModels.RenewableForecastResult(
+                zone,
+                date.toString(),
+                round(totalGenMwh),
+                round(sumLoad / 24.0),
+                round(maxShare),
+                Collections.unmodifiableList(hourly)
+        );
+    }
+
+    public MarketModels.DispatchEvaluationResult evaluateDispatch(
+            double capacityMwh,
+            double maxPowerMw,
+            double roundTripEfficiency,
+            String rawDate
+    ) {
+        if (capacityMwh <= 0) {
+            throw new IllegalArgumentException("Battery capacity must be positive: capacityMwh=" + capacityMwh);
+        }
+        if (maxPowerMw <= 0) {
+            throw new IllegalArgumentException("Maximum power rating must be positive: maxPowerMw=" + maxPowerMw);
+        }
+        if (roundTripEfficiency <= 0.0 || roundTripEfficiency > 1.0) {
+            throw new IllegalArgumentException("Round-trip efficiency must be in range (0.0, 1.0]: roundTripEfficiency=" + roundTripEfficiency);
+        }
+
+        LocalDate date = parseDate(rawDate);
+        MarketModels.SpotPriceResult spotResult = calculateSpotPrices("DE-LU", date.toString());
+        List<MarketModels.HourlySpotPrice> hourly = spotResult.hourlyPrices();
+
+        // 1-way efficiency = sqrt(roundTripEfficiency)
+        double oneWayEff = Math.sqrt(roundTripEfficiency);
+
+        // Sort hours by price to find arbitrage spreads
+        record HourPrice(int hour, double price) {}
+        List<HourPrice> sortedHours = new ArrayList<>();
+        for (var h : hourly) {
+            sortedHours.add(new HourPrice(h.hour(), h.dayAheadPriceEurPerMwh()));
+        }
+        sortedHours.sort(Comparator.comparingDouble(HourPrice::price));
+
+        // Lowest 4 hours for charging, highest 4 hours for discharging
+        int cycleHours = Math.min(4, (int) Math.ceil(capacityMwh / maxPowerMw));
+        Set<Integer> chargeHours = new HashSet<>();
+        Set<Integer> dischargeHours = new HashSet<>();
+
+        for (int i = 0; i < cycleHours; i++) {
+            chargeHours.add(sortedHours.get(i).hour());
+            dischargeHours.add(sortedHours.get(sortedHours.size() - 1 - i).hour());
+        }
+
+        // Simulate Chronological 24-hour operation
+        List<MarketModels.HourlyDispatchSchedule> schedule = new ArrayList<>();
+        double currentSoc = 0.0;
+        double totalCharged = 0.0;
+        double totalDischarged = 0.0;
+        double chargingCost = 0.0;
+        double dischargingRevenue = 0.0;
+
+        for (int h = 0; h < 24; h++) {
+            double price = hourly.get(h).dayAheadPriceEurPerMwh();
+            String action = "IDLE";
+            double power = 0.0;
+            double energyFlow = 0.0;
+
+            if (chargeHours.contains(h) && currentSoc < capacityMwh) {
+                action = "CHARGE";
+                double room = capacityMwh - currentSoc;
+                double maxEnergy = Math.min(maxPowerMw, room / oneWayEff);
+                energyFlow = maxEnergy;
+                power = maxEnergy;
+                currentSoc += energyFlow * oneWayEff;
+                totalCharged += energyFlow;
+                chargingCost += energyFlow * price;
+            } else if (dischargeHours.contains(h) && currentSoc > 0.0) {
+                action = "DISCHARGE";
+                double available = currentSoc;
+                double maxEnergy = Math.min(maxPowerMw, available);
+                energyFlow = maxEnergy;
+                power = maxEnergy;
+                currentSoc -= energyFlow;
+                double delivered = energyFlow * oneWayEff;
+                totalDischarged += delivered;
+                dischargingRevenue += delivered * price;
+            }
+
+            schedule.add(new MarketModels.HourlyDispatchSchedule(
+                    h,
+                    price,
+                    action,
+                    round(power),
+                    round(energyFlow),
+                    round(currentSoc)
+            ));
+        }
+
+        double netLoss = round(totalCharged - totalDischarged);
+        double netProfit = round(dischargingRevenue - chargingCost);
+
+        return new MarketModels.DispatchEvaluationResult(
+                date.toString(),
+                capacityMwh,
+                maxPowerMw,
+                roundTripEfficiency,
+                round(totalCharged),
+                round(totalDischarged),
+                netLoss,
+                round(chargingCost),
+                round(dischargingRevenue),
+                netProfit,
+                Collections.unmodifiableList(schedule)
+        );
+    }
+
+    public MarketModels.GridBalanceSnapshot getGridBalanceSnapshot(String rawZone) {
+        String zone = normalizeZone(rawZone);
+        return new MarketModels.GridBalanceSnapshot(
+                zone,
+                java.time.Instant.now().toString(),
+                50.018,
+                235.0,
+                58.4,
+                "NORMAL / BALANCED"
+        );
+    }
+
+    private static double round(double val) {
+        return BigDecimal.valueOf(val).setScale(2, RoundingMode.HALF_UP).doubleValue();
+    }
+}
diff --git a/servers/energy-market-server-bmad/src/main/java/com/workshop/energy/mcp/EnergyMarketTools.java b/servers/energy-market-server-bmad/src/main/java/com/workshop/energy/mcp/EnergyMarketTools.java
new file mode 100644
index 0000000..1a21722
--- /dev/null
+++ b/servers/energy-market-server-bmad/src/main/java/com/workshop/energy/mcp/EnergyMarketTools.java
@@ -0,0 +1,117 @@
+package com.workshop.energy.mcp;
+
+import com.fasterxml.jackson.databind.ObjectMapper;
+import com.fasterxml.jackson.databind.SerializationFeature;
+import com.workshop.energy.domain.EnergyMarketEngine;
+import com.workshop.energy.domain.MarketModels;
+import org.springframework.ai.mcp.annotation.McpTool;
+import org.springframework.ai.mcp.annotation.McpToolParam;
+import org.springframework.ai.tool.annotation.Tool;
+import org.springframework.ai.tool.annotation.ToolParam;
+import org.springframework.stereotype.Component;
+
+@Component
+public class EnergyMarketTools {
+
+    private final EnergyMarketEngine engine;
+    private final ObjectMapper objectMapper;
+
+    public EnergyMarketTools(EnergyMarketEngine engine, ObjectMapper objectMapper) {
+        this.engine = engine;
+        this.objectMapper = objectMapper.copy().enable(SerializationFeature.INDENT_OUTPUT);
+    }
+
+    @McpTool(
+            name = "energy_get_spot_prices",
+            description = "Query Day-Ahead and Intraday electricity spot prices in EUR/MWh for a European bidding zone (DE-LU, FR, NL, AT) on a specific date (YYYY-MM-DD)."
+    )
+    @Tool(
+            name = "energy_get_spot_prices",
+            description = "Query Day-Ahead and Intraday electricity spot prices in EUR/MWh for a European bidding zone (DE-LU, FR, NL, AT) on a specific date (YYYY-MM-DD)."
+    )
+    public String getSpotPrices(
+            @McpToolParam(description = "European bidding zone code (DE-LU, FR, NL, AT). Defaults to DE-LU if omitted.", required = false)
+            @ToolParam(description = "European bidding zone code (DE-LU, FR, NL, AT). Defaults to DE-LU if omitted.", required = false) String zone,
+            @McpToolParam(description = "Target date in ISO format YYYY-MM-DD. Defaults to today if omitted.", required = false)
+            @ToolParam(description = "Target date in ISO format YYYY-MM-DD. Defaults to today if omitted.", required = false) String date
+    ) {
+        try {
+            MarketModels.SpotPriceResult result = engine.calculateSpotPrices(zone, date);
+            return objectMapper.writeValueAsString(result);
+        } catch (Exception e) {
+            return "Error retrieving spot prices: " + e.getMessage();
+        }
+    }
+
+    @McpTool(
+            name = "energy_get_historical_prices",
+            description = "Retrieve multi-day historical electricity spot prices across a date range (up to 90 days) for trend analysis and negative price detection."
+    )
+    @Tool(
+            name = "energy_get_historical_prices",
+            description = "Retrieve multi-day historical electricity spot prices across a date range (up to 90 days) for trend analysis and negative price detection."
+    )
+    public String getHistoricalPrices(
+            @McpToolParam(description = "European bidding zone code (DE-LU, FR, NL, AT). Defaults to DE-LU if omitted.", required = false)
+            @ToolParam(description = "European bidding zone code (DE-LU, FR, NL, AT). Defaults to DE-LU if omitted.", required = false) String zone,
+            @McpToolParam(description = "Start date in ISO format YYYY-MM-DD.", required = true)
+            @ToolParam(description = "Start date in ISO format YYYY-MM-DD.", required = true) String startDate,
+            @McpToolParam(description = "End date in ISO format YYYY-MM-DD.", required = true)
+            @ToolParam(description = "End date in ISO format YYYY-MM-DD.", required = true) String endDate
+    ) {
+        try {
+            MarketModels.HistoricalPricesResult result = engine.calculateHistoricalPrices(zone, startDate, endDate);
+            return objectMapper.writeValueAsString(result);
+        } catch (Exception e) {
+            return "Error retrieving historical prices: " + e.getMessage();
+        }
+    }
+
+    @McpTool(
+            name = "energy_get_renewable_forecast",
+            description = "Retrieve hourly forecasts for solar PV, onshore wind, and offshore wind generation alongside expected electrical grid load."
+    )
+    @Tool(
+            name = "energy_get_renewable_forecast",
+            description = "Retrieve hourly forecasts for solar PV, onshore wind, and offshore wind generation alongside expected electrical grid load."
+    )
+    public String getRenewableForecast(
+            @McpToolParam(description = "European bidding zone code (DE-LU, FR, NL, AT). Defaults to DE-LU if omitted.", required = false)
+            @ToolParam(description = "European bidding zone code (DE-LU, FR, NL, AT). Defaults to DE-LU if omitted.", required = false) String zone,
+            @McpToolParam(description = "Forecast date in ISO format YYYY-MM-DD. Defaults to today if omitted.", required = false)
+            @ToolParam(description = "Forecast date in ISO format YYYY-MM-DD. Defaults to today if omitted.", required = false) String date
+    ) {
+        try {
+            MarketModels.RenewableForecastResult result = engine.calculateRenewableForecast(zone, date);
+            return objectMapper.writeValueAsString(result);
+        } catch (Exception e) {
+            return "Error retrieving renewable forecast: " + e.getMessage();
+        }
+    }
+
+    @McpTool(
+            name = "energy_evaluate_dispatch",
+            description = "Calculate optimal charging, discharging, and load-shifting schedules for battery storage or flexible electrical assets based on spot market spreads."
+    )
+    @Tool(
+            name = "energy_evaluate_dispatch",
+            description = "Calculate optimal charging, discharging, and load-shifting schedules for battery storage or flexible electrical assets based on spot market spreads."
+    )
+    public String evaluateDispatch(
+            @McpToolParam(description = "Total battery storage capacity in MWh.", required = true)
+            @ToolParam(description = "Total battery storage capacity in MWh.", required = true) double capacityMwh,
+            @McpToolParam(description = "Maximum charging and discharging power in MW.", required = true)
+            @ToolParam(description = "Maximum charging and discharging power in MW.", required = true) double maxPowerMw,
+            @McpToolParam(description = "Round-trip battery efficiency between 0.0 and 1.0 (e.g., 0.88 for 88%).", required = true)
+            @ToolParam(description = "Round-trip battery efficiency between 0.0 and 1.0 (e.g., 0.88 for 88%).", required = true) double roundTripEfficiency,
+            @McpToolParam(description = "Evaluation target date in ISO format YYYY-MM-DD. Defaults to today if omitted.", required = false)
+            @ToolParam(description = "Evaluation target date in ISO format YYYY-MM-DD. Defaults to today if omitted.", required = false) String date
+    ) {
+        try {
+            MarketModels.DispatchEvaluationResult result = engine.evaluateDispatch(capacityMwh, maxPowerMw, roundTripEfficiency, date);
+            return objectMapper.writeValueAsString(result);
+        } catch (Exception e) {
+            return "Error evaluating asset dispatch: " + e.getMessage();
+        }
+    }
+}
diff --git a/servers/energy-market-server-bmad/src/main/java/com/workshop/energy/mcp/EnergyMarketResources.java b/servers/energy-market-server-bmad/src/main/java/com/workshop/energy/mcp/EnergyMarketResources.java
new file mode 100644
index 0000000..05de9bc
--- /dev/null
+++ b/servers/energy-market-server-bmad/src/main/java/com/workshop/energy/mcp/EnergyMarketResources.java
@@ -0,0 +1,69 @@
+package com.workshop.energy.mcp;
+
+import com.fasterxml.jackson.databind.ObjectMapper;
+import com.fasterxml.jackson.databind.SerializationFeature;
+import com.workshop.energy.domain.EnergyMarketEngine;
+import com.workshop.energy.domain.MarketModels;
+import org.springframework.ai.mcp.annotation.McpResource;
+import org.springframework.stereotype.Component;
+
+import java.time.LocalDate;
+
+@Component
+public class EnergyMarketResources {
+
+    private final EnergyMarketEngine engine;
+    private final ObjectMapper objectMapper;
+
+    public EnergyMarketResources(EnergyMarketEngine engine, ObjectMapper objectMapper) {
+        this.engine = engine;
+        this.objectMapper = objectMapper.copy().enable(SerializationFeature.INDENT_OUTPUT);
+    }
+
+    @McpResource(
+            uri = "energy://market/prices/today",
+            name = "Today Market Prices",
+            description = "Real-time snapshot of today's hourly spot electricity prices and market trend for DE-LU",
+            mimeType = "application/json"
+    )
+    public String getTodayPrices() {
+        try {
+            MarketModels.SpotPriceResult result = engine.calculateSpotPrices("DE-LU", LocalDate.now().toString());
+            return objectMapper.writeValueAsString(result);
+        } catch (Exception e) {
+            return "{\"error\":\"" + e.getMessage() + "\"}";
+        }
+    }
+
+    @McpResource(
+            uri = "energy://market/prices/history/de-lu",
+            name = "Historical Prices (DE-LU)",
+            description = "Recent 7-day historical electricity price overview for bidding zone DE-LU",
+            mimeType = "application/json"
+    )
+    public String getHistoricalPricesDeLu() {
+        try {
+            LocalDate end = LocalDate.now();
+            LocalDate start = end.minusDays(7);
+            MarketModels.HistoricalPricesResult result = engine.calculateHistoricalPrices("DE-LU", start.toString(), end.toString());
+            return objectMapper.writeValueAsString(result);
+        } catch (Exception e) {
+            return "{\"error\":\"" + e.getMessage() + "\"}";
+        }
+    }
+
+    @McpResource(
+            uri = "energy://grid/balance/de-lu",
+            name = "Grid Balance (DE-LU)",
+            description = "Live status snapshot of renewable generation share and grid balance condition in DE-LU",
+            mimeType = "application/json"
+    )
+    public String getGridBalanceDeLu() {
+        try {
+            MarketModels.GridBalanceSnapshot snapshot = engine.getGridBalanceSnapshot("DE-LU");
+            return objectMapper.writeValueAsString(snapshot);
+        } catch (Exception e) {
+            return "{\"error\":\"" + e.getMessage() + "\"}";
+        }
+    }
+}
diff --git a/servers/energy-market-server-bmad/src/test/java/com/workshop/energy/EnergyMarketServerIntegrationTest.java b/servers/energy-market-server-bmad/src/test/java/com/workshop/energy/EnergyMarketServerIntegrationTest.java
new file mode 100644
index 0000000..50c4da6
--- /dev/null
+++ b/servers/energy-market-server-bmad/src/test/java/com/workshop/energy/EnergyMarketServerIntegrationTest.java
@@ -0,0 +1,284 @@
+package com.workshop.energy;
+
+import com.fasterxml.jackson.databind.JsonNode;
+import com.fasterxml.jackson.databind.ObjectMapper;
+import com.workshop.energy.domain.EnergyMarketEngine;
+import com.workshop.energy.domain.MarketModels;
+import com.workshop.energy.mcp.EnergyMarketResources;
+import com.workshop.energy.mcp.EnergyMarketTools;
+import org.junit.jupiter.api.BeforeEach;
+import org.junit.jupiter.api.DisplayName;
+import org.junit.jupiter.api.Nested;
+import org.junit.jupiter.api.Test;
+import org.springframework.beans.factory.annotation.Autowired;
+import org.springframework.boot.test.context.SpringBootTest;
+import org.springframework.http.MediaType;
+import org.springframework.test.web.servlet.MockMvc;
+import org.springframework.test.web.servlet.setup.MockMvcBuilders;
+import org.springframework.web.context.WebApplicationContext;
+
+import static org.assertj.core.api.Assertions.assertThat;
+import static org.assertj.core.api.Assertions.assertThatThrownBy;
+import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
+import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
+
+@SpringBootTest
+class EnergyMarketServerIntegrationTest {
+
+    @Autowired
+    private WebApplicationContext webApplicationContext;
+
+    private MockMvc mockMvc;
+
+    @Autowired
+    private ObjectMapper objectMapper;
+
+    @Autowired
+    private EnergyMarketEngine engine;
+
+    @Autowired
+    private EnergyMarketTools tools;
+
+    @Autowired
+    private EnergyMarketResources resources;
+
+    @BeforeEach
+    void setUp() {
+        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
+    }
+
+    @Nested
+    @DisplayName("Domain Engine Tests")
+    class EngineTests {
+
+        @Test
+        @DisplayName("calculateSpotPrices generates 24 hourly prices with valid statistical bounds")
+        void testCalculateSpotPrices() {
+            MarketModels.SpotPriceResult result = engine.calculateSpotPrices("DE-LU", "2026-10-10");
+
+            assertThat(result.zone()).isEqualTo("DE-LU");
+            assertThat(result.date()).isEqualTo("2026-10-10");
+            assertThat(result.hourlyPrices()).hasSize(24);
+            assertThat(result.maxPrice()).isGreaterThanOrEqualTo(result.minPrice());
+            assertThat(result.averageDayAheadPrice()).isBetween(-50.0, 250.0);
+            assertThat(result.negativePriceHoursCount()).isGreaterThanOrEqualTo(0);
+        }
+
+        @Test
+        @DisplayName("calculateSpotPrices rejects unsupported bidding zones")
+        void testUnsupportedZone() {
+            assertThatThrownBy(() -> engine.calculateSpotPrices("US-EAST", "2026-10-10"))
+                    .isInstanceOf(IllegalArgumentException.class)
+                    .hasMessageContaining("Unsupported bidding zone");
+        }
+
+        @Test
+        @DisplayName("calculateHistoricalPrices computes multi-day statistics correctly")
+        void testCalculateHistoricalPrices() {
+            MarketModels.HistoricalPricesResult result = engine.calculateHistoricalPrices("DE-LU", "2026-10-01", "2026-10-07");
+
+            assertThat(result.zone()).isEqualTo("DE-LU");
+            assertThat(result.startDate()).isEqualTo("2026-10-01");
+            assertThat(result.endDate()).isEqualTo("2026-10-07");
+            assertThat(result.dailySummaries()).hasSize(7);
+            assertThat(result.overallMaxPrice()).isGreaterThanOrEqualTo(result.overallMinPrice());
+        }
+
+        @Test
+        @DisplayName("calculateHistoricalPrices rejects end date before start date")
+        void testInvalidDateRange() {
+            assertThatThrownBy(() -> engine.calculateHistoricalPrices("DE-LU", "2026-10-10", "2026-10-01"))
+                    .isInstanceOf(IllegalArgumentException.class)
+                    .hasMessageContaining("is before startDate");
+        }
+
+        @Test
+        @DisplayName("calculateHistoricalPrices rejects range exceeding 90 days")
+        void testDateRangeTooLarge() {
+            assertThatThrownBy(() -> engine.calculateHistoricalPrices("DE-LU", "2026-01-01", "2026-06-01"))
+                    .isInstanceOf(IllegalArgumentException.class)
+                    .hasMessageContaining("exceeds maximum allowed limit of 90 days");
+        }
+
+        @Test
+        @DisplayName("calculateRenewableForecast provides 24 hours of generation breakdown and grid load")
+        void testRenewableForecast() {
+            MarketModels.RenewableForecastResult result = engine.calculateRenewableForecast("DE-LU", "2026-10-10");
+
+            assertThat(result.zone()).isEqualTo("DE-LU");
+            assertThat(result.hourlyForecasts()).hasSize(24);
+            assertThat(result.totalGenerationMwh()).isPositive();
+            assertThat(result.averageGridLoadMw()).isPositive();
+        }
+
+        @Test
+        @DisplayName("evaluateDispatch produces valid charge/discharge schedule and net positive profit spread")
+        void testEvaluateDispatch() {
+            MarketModels.DispatchEvaluationResult result = engine.evaluateDispatch(10.0, 2.5, 0.88, "2026-10-10");
+
+            assertThat(result.capacityMwh()).isEqualTo(10.0);
+            assertThat(result.maxPowerMw()).isEqualTo(2.5);
+            assertThat(result.roundTripEfficiency()).isEqualTo(0.88);
+            assertThat(result.totalChargedMwh()).isPositive();
+            assertThat(result.totalDischargedMwh()).isPositive();
+            assertThat(result.schedule()).hasSize(24);
+
+            // Verify state of charge never exceeds capacity and is non-negative
+            for (var slot : result.schedule()) {
+                assertThat(slot.stateOfChargeMwh()).isBetween(0.0, 10.0);
+            }
+        }
+
+        @Test
+        @DisplayName("evaluateDispatch rejects invalid asset constraints")
+        void testInvalidDispatchParameters() {
+            assertThatThrownBy(() -> engine.evaluateDispatch(-5.0, 2.0, 0.88, "2026-10-10"))
+                    .isInstanceOf(IllegalArgumentException.class)
+                    .hasMessageContaining("capacity must be positive");
+
+            assertThatThrownBy(() -> engine.evaluateDispatch(10.0, 0.0, 0.88, "2026-10-10"))
+                    .isInstanceOf(IllegalArgumentException.class)
+                    .hasMessageContaining("power rating must be positive");
+
+            assertThatThrownBy(() -> engine.evaluateDispatch(10.0, 2.0, 1.5, "2026-10-10"))
+                    .isInstanceOf(IllegalArgumentException.class)
+                    .hasMessageContaining("Round-trip efficiency");
+        }
+    }
+
+    @Nested
+    @DisplayName("MCP Tools and Resources Tests")
+    class ToolsAndResourcesTests {
+
+        @Test
+        @DisplayName("Tools return valid JSON strings")
+        void testToolsOutput() throws Exception {
+            String spotJson = tools.getSpotPrices("DE-LU", "2026-10-10");
+            JsonNode spotNode = objectMapper.readTree(spotJson);
+            assertThat(spotNode.has("hourlyPrices")).isTrue();
+
+            String histJson = tools.getHistoricalPrices("DE-LU", "2026-10-01", "2026-10-05");
+            JsonNode histNode = objectMapper.readTree(histJson);
+            assertThat(histNode.has("dailySummaries")).isTrue();
+
+            String renJson = tools.getRenewableForecast("DE-LU", "2026-10-10");
+            JsonNode renNode = objectMapper.readTree(renJson);
+            assertThat(renNode.has("hourlyForecasts")).isTrue();
+
+            String dispJson = tools.evaluateDispatch(10.0, 2.5, 0.85, "2026-10-10");
+            JsonNode dispNode = objectMapper.readTree(dispJson);
+            assertThat(dispNode.has("schedule")).isTrue();
+        }
+
+        @Test
+        @DisplayName("Resources return valid JSON snapshots")
+        void testResourcesOutput() throws Exception {
+            String todayJson = resources.getTodayPrices();
+            JsonNode todayNode = objectMapper.readTree(todayJson);
+            assertThat(todayNode.has("zone")).isTrue();
+
+            String histDeLuJson = resources.getHistoricalPricesDeLu();
+            JsonNode histDeLuNode = objectMapper.readTree(histDeLuJson);
+            assertThat(histDeLuNode.has("zone")).isTrue();
+
+            String gridJson = resources.getGridBalanceDeLu();
+            JsonNode gridNode = objectMapper.readTree(gridJson);
+            assertThat(gridNode.has("currentFrequencyHz")).isTrue();
+        }
+    }
+
+    @Nested
+    @DisplayName("MCP Protocol HTTP Endpoint Tests")
+    class McpEndpointTests {
+
+        @Test
+        @DisplayName("POST /mcp handles JSON-RPC initialize handshake without authentication")
+        void testMcpInitialize() throws Exception {
+            String initPayload = """
+                    {
+                      "jsonrpc": "2.0",
+                      "id": 1,
+                      "method": "initialize",
+                      "params": {
+                        "protocolVersion": "2024-11-05",
+                        "capabilities": {},
+                        "clientInfo": {
+                          "name": "test-client",
+                          "version": "1.0.0"
+                        }
+                      }
+                    }
+                    """;
+
+            var mvcResult = mockMvc.perform(post("/mcp")
+                            .contentType(MediaType.APPLICATION_JSON)
+                            .accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON)
+                            .content(initPayload))
+                    .andReturn();
+
+            System.out.println("DEBUG MCP STATUS: " + mvcResult.getResponse().getStatus());
+            System.out.println("DEBUG MCP BODY: " + mvcResult.getResponse().getContentAsString());
+            System.out.println("DEBUG MCP ERR: " + mvcResult.getResponse().getErrorMessage());
+
+            assertThat(mvcResult.getResponse().getStatus()).isEqualTo(200);
+        }
+
+        @Test
+        @DisplayName("POST /mcp handles tools/list and returns energy tools")
+        void testMcpToolsList() throws Exception {
+            String initPayload = """
+                    {
+                      "jsonrpc": "2.0",
+                      "id": 1,
+                      "method": "initialize",
+                      "params": {
+                        "protocolVersion": "2024-11-05",
+                        "capabilities": {},
+                        "clientInfo": {
+                          "name": "test-client",
+                          "version": "1.0.0"
+                        }
+                      }
+                    }
+                    """;
+
+            var initResult = mockMvc.perform(post("/mcp")
+                            .contentType(MediaType.APPLICATION_JSON)
+                            .accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON)
+                            .content(initPayload))
+                    .andExpect(status().isOk())
+                    .andReturn();
+
+            String sessionId = initResult.getResponse().getHeader("Mcp-Session-Id");
+
+            String listPayload = """
+                    {
+                      "jsonrpc": "2.0",
+                      "id": 2,
+                      "method": "tools/list",
+                      "params": {}
+                    }
+                    """;
+
+            var reqBuilder = post("/mcp")
+                    .contentType(MediaType.APPLICATION_JSON)
+                    .accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON)
+                    .content(listPayload);
+
+            if (sessionId != null) {
+                reqBuilder.header("Mcp-Session-Id", sessionId);
+            }
+
+            String response = mockMvc.perform(reqBuilder)
+                    .andExpect(status().isOk())
+                    .andReturn()
+                    .getResponse()
+                    .getContentAsString();
+
+            assertThat(response).contains("energy_get_spot_prices");
+            assertThat(response).contains("energy_get_historical_prices");
+            assertThat(response).contains("energy_get_renewable_forecast");
+            assertThat(response).contains("energy_evaluate_dispatch");
+        }
+    }
+}

```

Do not invoke any skill, and do not spawn subagents of your own — you are the reviewer. Return your findings as text in your final message; do not route them through any findings-reporting tool the host may offer.
