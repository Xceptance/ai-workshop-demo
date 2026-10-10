---
title: 'Dedicated Energy Market MCP Server'
type: 'feature'
created: '2026-10-10'
status: 'done'
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

| Finding ID | Layer | Location | Verdict | Route | Evidence / Rationale |
|---|---|---|---|---|---|
| BH-1 | blind-hunter | `McpClientManager.java:308` | `low` | `patch` | Stale session ID retained across HTTP errors >= 400. Patched to clear `energySessionId = null` on error response. |
| BH-2 | blind-hunter | `EnergyMarketTools.java:38` | `false` | - | Tool methods catch all `Exception` and return safe formatted error strings. |
| BH-3 | blind-hunter | `EnergyMarketEngine.java:160` | `false` | - | Historical queries with reversed dates or span > 90 days are guarded and verified in `EngineTests`. |
| BH-4 | blind-hunter | `EnergyMarketEngine.java:210` | `false` | - | Battery dispatch capacity/power <= 0 or efficiency outside (0, 1] are guarded with validation messages. |
| BH-5 | blind-hunter | `McpClientManager.java:73` | `low` | `defer` | Session ID stored in volatile field; sufficient for single-user workshop demo client. |
| EH-1 | edge-case-hunter | `EnergyMarketEngine.java:110` | `false` | - | Zone codes are normalized via `toUpperCase().trim()` and unsupported zones return descriptive errors. |
| EH-2 | edge-case-hunter | `EnergyMarketEngine.java:230` | `false` | - | Negative electricity prices correctly trigger charging recommendation when efficiency spread is positive. |
| VG-1 | verification-gap | `servers/energy-market-server-bmad/...` | `false` | - | 12 tests across engine, tools, resources, and Streamable HTTP JSON-RPC endpoint compile and pass. |
| VG-2 | verification-gap | `McpClientManager.java` | `false` | - | Root project MCP client integrates remote HTTP server and preserves zero-regression across all root tests. |

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
