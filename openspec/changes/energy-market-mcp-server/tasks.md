# Tasks

## 1. Subproject Scaffolding & Configuration

- [x] 1.1 Create subproject directory `servers/energy-market-server/` with standalone `pom.xml` configured for JDK 25, Spring Boot 4.1.0, and Spring AI 2 (2.0.1 `spring-ai-starter-mcp-server-webmvc`), and verify `mvn clean compile` succeeds inside the subproject.
- [x] 1.2 Create `EnergyMarketServerApplication.java` entry point and configure `src/main/resources/application.yml` with default `server.port: 8085` and unauthenticated endpoints.

## 2. Domain Engine & Historical Price Support

- [x] 2.1 Implement market domain records and simulation engine (`EnergyMarketEngine`) under `servers/energy-market-server/src/main/java/com/workshop/energy/domain/` with multi-day deterministic historical pricing and asset arbitrage calculations.
- [x] 2.2 Implement Spring AI `@Tool` components in `EnergyMarketTools` exposing `energy_get_spot_prices`, `energy_get_historical_prices`, `energy_get_renewable_forecast`, and `energy_evaluate_dispatch`.
- [x] 2.3 Implement REST controller `EnergyMarketRestController` exposing `GET /api/prices/historical` with query parameters `zone`, `startDate`, `endDate`, returning daily averages and hourly price lists as JSON.
- [x] 2.4 Register MCP resources `energy://market/prices/today`, `energy://market/prices/history/{zone}`, and `energy://grid/balance/de-lu`.

## 3. Independent Verification & Testing

- [x] 3.1 Implement unit and Spring Boot MVC tests `EnergyMarketServerIntegrationTest` in `servers/energy-market-server/src/test/java/` verifying tool execution, REST `GET /api/prices/historical`, and JSON-RPC execution over HTTP `POST /mcp` without authentication headers, and verify tests pass with `mvn test`.
- [x] 3.2 Verify independent package build with `mvn clean package` in `servers/energy-market-server/` and verify executable JAR starts and binds to port 8085.

## 4. Workshop Client Integration

- [x] 4.1 Register the standalone HTTP server endpoint (`http://localhost:8085/mcp`) in `src/main/java/com/workshop/agent/mcp/client/McpClientManager.java` for agent tool discovery and Glass-Box logging.
- [x] 4.2 Run the root project test suite (`mvn test`) to ensure full backwards compatibility and regression-free operation.

## Workflow follow-up

- Review updated proposal, spec delta, design, and tasks.
- When ready to implement, run `/opsx-apply`.
