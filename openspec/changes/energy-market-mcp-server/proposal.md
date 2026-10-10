# Proposal

## Why

In automated energy trading and smart grid optimization (inspired by modern renewable energy trading and grid management platforms), AI agents need real-time awareness of European electricity spot markets (Day-Ahead and Intraday prices), renewable energy generation forecasts (wind and solar feed-in), and power plant or battery storage dispatch opportunities.

Providing a dedicated, standalone Energy Market MCP server as an independent subproject allows workshop participants to inspect, build, test, and run an MCP server independently on a custom port without authentication for zero-friction demo usage. Adding historical price queries and a direct REST endpoint further allows agents and developers to analyze multi-day price trends, seasonality, and negative-price occurrences.

## What Changes

- **Standalone Subproject**: Create `servers/energy-market-server/` with its own `pom.xml`, built on **JDK 25**, **Maven**, **Spring Boot 4.1**, and **Spring AI 2** (`spring-ai-starter-mcp-server-webmvc`).
- **Independent Build & Lifecycle**: Can be built independently (`mvn clean package`) and launched (`mvn spring-boot:run` or `java -jar ...`) on a **custom port** (default `8085`, configurable via `server.port`).
- **Open Demo Concept (No Auth)**: Exposes unauthenticated HTTP endpoints (`POST /mcp` for JSON-RPC 2.0 / SSE, plus REST endpoints) requiring no API keys or token headers.
- **Historical Price Support**:
  - **MCP Tool `energy_get_historical_prices`**: Queries spot electricity prices across a specified date range (`startDate`, `endDate`), providing daily averages, price extremes, and counts of negative price hours.
  - **REST Endpoint `GET /api/prices/historical`**: Direct HTTP JSON endpoint for browser, curl, or frontend dashboard inspection without an MCP client.
- **Real-Time MCP Tools**:
  - `energy_get_spot_prices`: Retrieve Day-Ahead and Intraday spot electricity prices (EUR/MWh) for European bidding zones (such as DE-LU, FR, NL, AT), including price averages, peak hours, and negative price flags.
  - `energy_get_renewable_forecast`: Retrieve forecasted renewable generation (wind onshore, wind offshore, solar PV) and expected grid load for a specified date and bidding zone.
  - `energy_evaluate_dispatch`: Given an energy asset's parameters (e.g., battery storage capacity in MWh, charge/discharge efficiency, power rating in MW, or flexible consumer load) and market price forecasts, calculate optimal dispatch schedules (when to charge at low/negative prices and when to discharge at peak prices) to maximize trading margin and support grid balancing.
- **MCP Resources**:
  - `energy://market/prices/today`: Real-time JSON snapshot of today's hourly spot prices and market trend for the default bidding zone.
  - `energy://market/prices/history/{zone}`: Multi-day historical price overview for a bidding zone.
  - `energy://grid/balance/de-lu`: Live status snapshot of renewable generation share and grid balance condition.
- **Workshop Integration**: The main workshop app's `McpClientManager` connects to this server over HTTP at `http://localhost:8085/mcp`.

## Capabilities

### New Capabilities
- `energy-market-mcp`: Exposes European electricity spot market pricing, historical price analysis, renewable generation forecasts, battery and flexible asset dispatch optimization tools, and market status resources over Model Context Protocol (JSON-RPC 2.0 / HTTP) and REST via an independent Spring AI 2 subproject.

### Modified Capabilities

## Impact
- **New Subproject**:
  - `servers/energy-market-server/pom.xml` (JDK 25, Spring Boot 4.1, Spring AI 2)
  - `servers/energy-market-server/src/main/java/com/workshop/energy/EnergyMarketServerApplication.java`
  - `servers/energy-market-server/src/main/java/com/workshop/energy/service/EnergyMarketService.java`
  - `servers/energy-market-server/src/main/java/com/workshop/energy/mcp/EnergyMarketTools.java`
  - `servers/energy-market-server/src/main/java/com/workshop/energy/web/EnergyMarketRestController.java`
  - `servers/energy-market-server/src/main/resources/application.yml` (default `server.port: 8085`)
  - `servers/energy-market-server/src/test/java/com/workshop/energy/EnergyMarketServerApplicationTests.java`
- **Workshop Client Integration**:
  - `src/main/java/com/workshop/agent/mcp/client/McpClientManager.java` (wired to connect to `http://localhost:8085/mcp` over HTTP JSON-RPC)
- **External Dependencies & APIs**: None; uses Spring AI 2 starters with an authentic, built-in deterministic European electricity market simulation engine.
