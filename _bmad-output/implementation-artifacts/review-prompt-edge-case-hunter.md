# Edge Case Hunter Review

**Goal:** You are a pure path tracer. Never comment on whether code is good or bad; only list missing handling.
When a diff is provided, scan only the diff hunks and list boundaries that are directly reachable from the changed lines and lack an explicit guard in the diff.
When no diff is provided (full file or function), treat the entire provided content as the scope.
Ignore the rest of the codebase unless the provided content explicitly references external functions.
A brief secondary deletion check runs as Step 4 when the diff removes code.
A claims check runs as Step 5.

**Inputs:**
- **content** — Content to review, or a path to read it from: diff, full file, or function
- **also_consider** (optional) — Areas to keep in mind during review alongside normal edge-case analysis
- **claims_file** — Path to the spec this change was built from. Do NOT read it before Step 5: the path tracing in Steps 2–3 must finish before the claims are seen.

**MANDATORY: Execute steps in the Execution section IN EXACT ORDER. DO NOT skip steps or change the sequence. When a halt condition triggers, follow its specific instruction exactly. Each action within a step is a REQUIRED action to complete that step.**

**Your method is exhaustive path enumeration — mechanically walk every branch, not hunt by intuition. Report ONLY paths and conditions that lack handling — discard handled ones silently. Do NOT editorialize or add filler. Do not assign severity labels, rankings, or priority levels.**


## EXECUTION

### Step 1: Receive Content

- Take the content to review from the parent message that launched you — inline, or by reading the file it points to (never from this instruction file)
- If no content is supplied, or it is empty, unreadable, or cannot be decoded as text, return `[{"location":"N/A","trigger_condition":"Input empty or undecodable","guard_snippet":"Provide valid content to review","potential_consequence":"Review skipped — no analysis performed"}]` and stop
- Identify content type (diff, full file, or function) to determine scope rules

### Step 2: Exhaustive Path Analysis

**Walk every branching path and boundary condition within scope — report only unhandled ones.**

- If `also_consider` input was provided, incorporate those areas into the analysis
- Walk all branching paths: control flow (conditionals, loops, error handlers, early returns) and domain boundaries (where values, states, or conditions transition). Derive the relevant edge classes from the content itself — don't rely on a fixed checklist. Examples: missing else/default, unguarded inputs, off-by-one loops, arithmetic overflow, implicit type coercion, race conditions, timeout gaps
- Consider implicit branches: the diff special-cases or changes the handling of one or more members of a fixed set of values — enums, status codes, sentinels, type tags, flags, value ranges. The rest of the set is implicit branches (e.g. the diff changes the `RED` and `YELLOW` cases of a `RED`/`YELLOW`/`GREEN` enum; `GREEN` is the implicit branch)
- Consider handle lifetime: when the changed code re-checks, re-fetches, or re-validates something it already held — a handle, index, id, pointer — the re-check exists because an intervening call can invalidate it. Identify that call, what it does to the thing held, and what the changed code silently skips when the re-check fails
- For each call site the diff adds or changes — in test files as well as production code — read the callee's declaration and check the call against it: argument count, order, types, and defaults. Report any mismatch
- For each path: determine whether the content handles it
- Collect only the unhandled paths as findings — discard handled ones silently

### Step 3: Validate Completeness

- Revisit every edge class from Step 2 — e.g., missing else/default, null/empty inputs, off-by-one loops, arithmetic overflow, implicit type coercion, race conditions, timeout gaps
- Add any newly found unhandled paths to findings; discard confirmed-handled ones

### Step 4: Deletion Check

If the diff removed or replaced meaningful code (ignore pure renames and whitespace): load `references/deletion-check.md` and follow it.

### Step 5: Claims Check

Load `references/claims-check.md` and follow it.

### Step 6: Present Findings

Output all findings as a single JSON array following the Output Format specification exactly.


## OUTPUT FORMAT

Return ONLY a valid JSON array of objects. Each edge-case finding contains exactly these four fields:

```json
[{
  "location": "file:start-end (or file:line when single line, or file:hunk when exact line unavailable)",
  "trigger_condition": "one-line description (max 15 words)",
  "guard_snippet": "minimal code sketch that closes the gap (single-line escaped string, no raw newlines or unescaped quotes)",
  "potential_consequence": "what could actually go wrong (max 15 words)"
}]
```

No extra text, no explanations, no markdown wrapping. An empty array `[]` is valid when nothing is found. Deletion findings from Step 4 and claim findings from Step 5, if any, go in the same array with the extra fields defined in `references/deletion-check.md` and `references/claims-check.md`.


## HALT CONDITIONS

- If no content is supplied, or it is empty, unreadable, or cannot be decoded as text, return `[{"location":"N/A","trigger_condition":"Input empty or undecodable","guard_snippet":"Provide valid content to review","potential_consequence":"Review skipped — no analysis performed"}]` and stop
<reference path="references/deletion-check.md">
# Deletion Check

Secondary pass for the Edge Case Hunter — runs only when the diff removed meaningful code. Subordinate to the edge-case pass; findings are usually few or none.

For each chunk of removed or replaced code (ignore pure renames and whitespace), ask: did it carry behavior or a contract that the change neither re-established nor intentionally retired? Add a finding for any resulting regression, orphaned reference, or newly-dead code. Skip anything already covered by your edge-case findings.

Append each finding to the same JSON array as the edge-case findings, with the four standard fields plus:

- `kind`: `"deletion"`
- `confidence`: `"high"`, `"medium"`, or `"low"` — these are inferences; rate them

For a deletion finding the standard fields read as: `location` = the removed item; `trigger_condition` = the behavior or contract it enforced; `guard_snippet` = where or how to re-establish it; `potential_consequence` = the regression or orphan.

Add nothing if nothing qualifies.
</reference>
<reference path="references/claims-check.md">
# Claims Check

Final pass for the Edge Case Hunter. Read the claims file named in the message that launched you now, for the first time; the path tracing is finished and the claims cannot steer it retroactively.

It is the spec the change was built from. Read only its `## Intent` and `## Tasks & Acceptance` sections — the claims live there; ignore the rest of the file. The spec is the change's own account of itself: testimony, not evidence — a claim repeated in a code comment is still the same claim, not confirmation. Extract each checkable claim — what the change does, what it preserves, ordering, arithmetic, and parity with existing code ("exactly as X does") — then try to falsify each one against the code you have already traced. Where your trace is not enough to decide, read the code that decides it: the compared-to function, the actual callee, the state the claim assumes.

Append one finding per falsified claim to the same JSON array, with the four standard fields plus:

- `kind`: `"claim"`
- `confidence`: `"high"`, `"medium"`, or `"low"`

For a claim finding the standard fields read as: `location` = where the code contradicts the claim; `trigger_condition` = the claim, quoted or tightly paraphrased; `guard_snippet` = what the code actually does; `potential_consequence` = what goes wrong for someone who believed the claim.

Verified claims produce nothing. Add nothing if nothing is falsified.
</reference>

## CONTENT SOURCE

"Review content:" in the message that launched you gives the content itself or a path to read it from. Read the file when it is a path; either way that is the content under review, and this instruction file never is.


claims_file (leave unread until your instructions call for it):
```markdown
---
title: 'Dedicated Energy Market MCP Server'
type: 'feature'
created: '2026-10-10'
status: 'in-review'
baseline_commit: 'cffe359151cb70bf483a5e05d46542b208b973b8'
route: 'dispatch'
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** AI agents optimizing automated energy trading, battery dispatch, and grid balancing require real-time and historical European electricity spot market visibility, renewable forecasts, and dispatch optimization, which currently do not exist in the workshop MCP ecosystem as an independent, zero-friction service.

**Approach:** Build a dedicated, standalone subproject (`servers/energy-market-server-bmad/`) on JDK 25, Spring Boot 4.1.0, and Spring AI 2 WebMVC exposing purely unauthenticated MCP protocol tools and resources on port 8086, and integrate it into `McpClientManager`.

## Boundaries & Constraints

**Always:**
- Keep `servers/energy-market-server-bmad/` an independently buildable Maven project (`mvn clean package` succeeds inside `servers/energy-market-server-bmad/` without root dependencies).
- Bind to port `8086` by default with zero-friction unauthenticated access on the MCP endpoint (`POST /mcp`).
- Maintain deterministic date-seeded European electricity market pricing and renewable generation simulations in `EnergyMarketEngine`.
- Use Spring AI 2 `@Tool` annotations for MCP tool definitions (`energy_get_spot_prices`, `energy_get_historical_prices`, `energy_get_renewable_forecast`, `energy_evaluate_dispatch`).
- Ensure all root workshop tests continue to pass without regression.

**Never:**
- Never include human REST controllers or non-MCP web endpoints (focus purely on the MCP server protocol).
- Never require API keys, bearer tokens, or SSL certificates to access the MCP server in demo mode.
- Never tightly couple the root POM to the subproject build lifecycle (maintain independent compilation).
- Never depend on live external third-party paid exchange APIs.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Query Spot Prices | `zone: "DE-LU"` | Hourly Day-Ahead & Intraday prices (EUR/MWh), average price, peak hours, negative-price count | Return error message listing supported bidding zones for invalid zone |
| Query Historical Prices (MCP) | `zone: "DE-LU"`, `startDate: "2026-10-01"`, `endDate: "2026-10-08"` | Daily average prices, min/max spreads, total negative price hours, and trend analysis | Validation error if `endDate` is before `startDate` or span > 90 days |
| Query Renewable Forecast | `zone: "DE-LU"`, `date: "2026-10-11"` | Hourly solar PV, onshore wind, offshore wind generation vs total grid load | Return validation error if zone invalid |
| Evaluate Battery Dispatch | `capacityMwh: 10.0`, `maxPowerMw: 2.5`, `roundTripEfficiency: 0.88`, `date: "2026-10-11"` | Recommended charge hours (low/negative price), discharge hours (peak price), efficiency losses, net profit EUR | Return error if capacity or power is <= 0 or efficiency not between 0 and 1 |
| Read MCP Resource | `energy://market/prices/today` | HTTP 200 OK JSON text with current market prices and balance snapshot | JSON-RPC error -32602 for unknown resource URI |

</frozen-after-approval>

## Code Map

- `servers/energy-market-server-bmad/pom.xml` -- Standalone Maven POM with JDK 25, Spring Boot 4.1.0, and Spring AI 2 WebMVC starter
- `servers/energy-market-server-bmad/src/main/resources/application.yml` -- Server configuration with `server.port: 8086` and Spring AI MCP server settings
- `servers/energy-market-server-bmad/src/main/java/com/workshop/energy/EnergyMarketServerApplication.java` -- Spring Boot main application entry point
- `servers/energy-market-server-bmad/src/main/java/com/workshop/energy/domain/EnergyMarketEngine.java` -- Deterministic multi-day European spot price generator, renewable forecast calculator, and battery dispatch optimizer
- `servers/energy-market-server-bmad/src/main/java/com/workshop/energy/domain/MarketModels.java` -- Immutable records for spot prices, historical daily metrics, renewable forecasts, and dispatch schedules
- `servers/energy-market-server-bmad/src/main/java/com/workshop/energy/mcp/EnergyMarketTools.java` -- Spring AI `@Tool` methods for spot prices, historical prices, renewable forecasts, and dispatch optimization
- `servers/energy-market-server-bmad/src/main/java/com/workshop/energy/mcp/EnergyMarketResources.java` -- MCP resource provider for `energy://market/prices/today`, `energy://market/prices/history/{zone}`, and `energy://grid/balance/de-lu`
- `servers/energy-market-server-bmad/src/test/java/com/workshop/energy/EnergyMarketServerIntegrationTest.java` -- Integration tests validating engine, MCP tools, and JSON-RPC protocol handling over HTTP `POST /mcp`
- `src/main/java/com/workshop/agent/mcp/client/McpClientManager.java` -- Workshop client manager updated with remote HTTP MCP registration and execution support for `http://localhost:8086/mcp`

## Tasks & Acceptance

**Execution:**
- [x] `servers/energy-market-server-bmad/pom.xml` -- Create standalone POM -- Define JDK 25, Spring Boot 4.1.0, and Spring AI 2.0.1 dependencies
- [x] `servers/energy-market-server-bmad/src/main/resources/application.yml` -- Configure server -- Set port 8086, app name, and MCP server endpoints
- [x] `servers/energy-market-server-bmad/src/main/java/com/workshop/energy/EnergyMarketServerApplication.java` -- Create application class -- Standard `@SpringBootApplication` bootstrapper
- [x] `servers/energy-market-server-bmad/src/main/java/com/workshop/energy/domain/MarketModels.java` -- Define domain records -- Clean data contracts for spot, historical, forecast, and dispatch results
- [x] `servers/energy-market-server-bmad/src/main/java/com/workshop/energy/domain/EnergyMarketEngine.java` -- Implement deterministic simulation engine -- Provide realistic European electricity price curves and dispatch solver
- [x] `servers/energy-market-server-bmad/src/main/java/com/workshop/energy/mcp/EnergyMarketTools.java` -- Implement Spring AI `@Tool` methods -- Expose the 4 energy market tools over MCP
- [x] `servers/energy-market-server-bmad/src/main/java/com/workshop/energy/mcp/EnergyMarketResources.java` -- Implement MCP resource registrations -- Expose `energy://` resources
- [x] `servers/energy-market-server-bmad/src/test/java/com/workshop/energy/EnergyMarketServerIntegrationTest.java` -- Implement integration tests -- Validate engine, tool execution, and MCP JSON-RPC protocol handling
- [x] `src/main/java/com/workshop/agent/mcp/client/McpClientManager.java` -- Register energy market server -- Allow workshop client to connect to `http://localhost:8086/mcp` and log wire traffic

**Acceptance Criteria:**
- Given the subproject `servers/energy-market-server-bmad/`, when running `mvn clean test` inside that directory, then all unit and integration tests compile and pass on JDK 25.
- Given the running Energy Market server on port 8086, when an MCP client sends a JSON-RPC request to `POST /mcp` without authentication, then the server processes and returns a valid JSON-RPC 2.0 response.
- Given the running Energy Market server, when an MCP client requests `tools/list`, then `energy_get_spot_prices`, `energy_get_historical_prices`, `energy_get_renewable_forecast`, and `energy_evaluate_dispatch` are returned with valid schemas.
- Given `McpClientManager` in the main workshop application, when running root tests with `mvn test`, all existing tests pass without regressions.

## Implementation Notes

- Scaffolded standalone subproject in `servers/energy-market-server-bmad/` configured with Java 25, Spring Boot 4.1.0, and Spring AI 2.0.1 WebMVC starter.
- Excluded human REST controller as directed by user intent; server exposes pure MCP protocol endpoints and resources.
- Configured default port 8086 in `application.yml` with open demo access (no auth).
- Implemented `EnergyMarketEngine` with deterministic date-seeded sinusoidal spot prices, renewable generation breakdown, and battery dispatch arbitrage optimization.
- Defined tools with `@McpTool` and `@Tool` annotations, and resources with `@McpResource`.
- Updated `McpClientManager` in root project with HTTP JSON-RPC streamable transport, port 8086 endpoint registration, and `Mcp-Session-Id` header propagation.
- Verified test suites: 12 tests in subproject passed; 36 tests in root project passed with zero regressions.

## Spec Change Log

## Review Triage Log

## Design Notes

The `EnergyMarketEngine` generates authentic European electricity prices using a deterministic sinusoidal model seeded by date and zone. Peak hours (08:00-11:00 and 17:00-20:00) exhibit higher prices, while solar noon (12:00-15:00) dips, occasionally yielding negative prices when renewable forecasts exceed load.
The `energy_evaluate_dispatch` solver analyzes hourly price spreads against round-trip efficiency ($\eta$) to identify profitable charging ($P_{charge} < P_{discharge} \times \eta$) and generates optimal battery schedules.
The server runs strictly as an MCP service, without human REST controllers, keeping the attack surface and code footprint minimal and focused.

## Verification

**Commands:**
- `mvn -f servers/energy-market-server-bmad/pom.xml clean test` -- expected: All subproject tests compile and pass
- `mvn test` -- expected: All root workshop tests compile and pass without regressions

**Manual checks (if no CLI):**
- Verify `servers/energy-market-server-bmad/` contains standalone `pom.xml` and can be built independently.

```

Review content:
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

Do not invoke any skill, and do not spawn subagents of your own — you are the reviewer. If the instruction file is unreadable, report that exact failure and stop. Return your findings as text in your final message; do not route them through any findings-reporting tool the host may offer.
