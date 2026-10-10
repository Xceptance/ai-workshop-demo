# Design

## Context

The workshop runtime demonstrates agent-to-tool and agent-to-MCP communication. To showcase real-world microservice decoupled patterns, the Energy Market MCP server is organized as an **independent subproject** in `servers/energy-market-server/`.

It uses the modern Java and Spring ecosystem:
- **JDK 25** (utilizing modern Java 25 runtime and language features)
- **Maven** build system with an independent `pom.xml`
- **Spring Boot 4.1**
- **Spring AI 2** (`spring-ai-bom` 2.0.1 and `spring-ai-starter-mcp-server-webmvc`)
- Default custom port `8085` with zero authentication for immediate, friction-free demo experimentation.
- Dual exposure: MCP protocol tools/resources for AI agents, and a direct REST API for web browsers, dashboards, and curl.

See `proposal.md` for background and energy trading domain motivation.

## Goals / Non-Goals

**Goals:**
- Provide a completely standalone subproject (`servers/energy-market-server/`) that can be built (`mvn clean package`) and launched (`mvn spring-boot:run`) on its own.
- Leverage Spring AI 2 MCP server capabilities (`spring-ai-starter-mcp-server-webmvc`) with `@Tool` registrations.
- Bind by default to port `8085` (configurable via `server.port` / `PORT`).
- Expose an open, unauthenticated HTTP JSON-RPC endpoint at `POST /mcp` (and optional SSE transport).
- Expose direct REST endpoint `GET /api/prices/historical` returning structured JSON time-series and aggregate statistics.
- Expose four domain tools:
  - `energy_get_spot_prices`: Hourly Day-Ahead and Intraday prices (EUR/MWh), averages, peak hours, and negative price flags.
  - `energy_get_historical_prices`: Query multi-day historical price series, daily averages, price spreads, and negative price counts.
  - `energy_get_renewable_forecast`: Hourly solar, wind onshore, and wind offshore feed-in forecasts compared to electrical grid load.
  - `energy_evaluate_dispatch`: Battery storage and flexible asset dispatch solver calculating charge/discharge cycles, efficiency losses, and trading profit.
- Expose MCP resources (`energy://market/prices/today`, `energy://market/prices/history/{zone}`, and `energy://grid/balance/de-lu`).
- Wire HTTP discovery and dispatch into the main workshop's `McpClientManager` at `http://localhost:8085/mcp`.

**Non-Goals:**
- Requiring authentication tokens, API keys, or SSL certificates for the demo setup.
- Coupling build lifecycles between the root workshop project and this subproject (they remain independently buildable).
- Live external exchange connections requiring proprietary subscriptions.

## Decisions

### Decision 1: Dedicated Subproject with Independent Maven POM
- **Choice**: Place the server in `servers/energy-market-server/` with its own self-contained `pom.xml`, declaring `spring-boot-starter-parent` 4.1.0, Java version 25, and `spring-ai-bom` 2.0.1.
- **Rationale**: Enables participants to open, compile, run, and test the server in isolation without needing the parent workshop application running.

### Decision 2: Spring AI 2 MCP Server Starter (WebMVC) + Spring MVC REST
- **Choice**: Use `org.springframework.ai:spring-ai-starter-mcp-server-webmvc` for MCP endpoints, complemented by a lightweight `@RestController` for direct HTTP inspection.
- **Rationale**: Agents interact via MCP JSON-RPC, while developers and browser UIs can immediately query `GET /api/prices/historical` without an MCP client.

### Decision 3: Deterministic Historical Time Series Generation
- **Choice**: Implement a deterministic date-seeded European market pricing generator in `EnergyMarketEngine`. Any date range produces consistent, realistic hourly prices reflecting seasonal variance, solar peak dips, and wind surges.
- **Rationale**: Guarantees zero latency and offline reliability with realistic statistics (average EUR/MWh, negative price hours) across past weeks or months.

### Decision 4: Custom Port (8085) & No Authentication
- **Choice**: Set `server.port: 8085` in `application.yml` and omit security filters.
- **Rationale**: Port 8080 is typically used by the main workshop app; port 8085 avoids port conflicts. Omitting authentication guarantees that any external agent (Claude Code, Cursor, Antigravity, or Python scripts) can connect without header configuration.

## Risks / Trade-offs

- **[Large historical query payloads]** → Enforce max date range (e.g. 90 days); provide both daily aggregated summaries and hourly breakdown options.
- **[Invalid Date Formats]** → Validate ISO-8601 `YYYY-MM-DD` strings upfront; return 400 Bad Request in REST and code `-32602` in MCP.
- **[Compilation with JDK 25]** → Configured explicitly in `pom.xml` with source/target release 25 matching the system JDK.
