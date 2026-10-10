# Spec Delta

## Purpose

Provides AI agents with real-time access to European electricity spot market pricing, historical price analysis, renewable generation forecasts, battery/asset dispatch optimization tools, and market status resources over the Model Context Protocol (JSON-RPC 2.0 / HTTP) and REST via an independent Spring AI 2 server running without authentication on a configurable port.

## ADDED Requirements

### Requirement: Standalone HTTP Transport on Configurable Port Without Authentication
The server SHALL run as an independent HTTP service on a configurable port (default 8085) and accept JSON-RPC 2.0 requests at `POST /mcp` without requiring authentication headers or API keys.

#### Scenario: Unauthenticated JSON-RPC request
- **WHEN** client sends an HTTP POST request to `http://localhost:8085/mcp` with a valid JSON-RPC payload and no authorization headers
- **THEN** server processes the request and responds with HTTP 200 OK containing the JSON-RPC response

#### Scenario: Custom port binding
- **WHEN** server is started with command-line argument `--server.port=9090` or environment variable `PORT=9090`
- **THEN** server binds to and accepts requests on port 9090

### Requirement: MCP Server Protocol Initialization
The server SHALL implement standard Model Context Protocol (MCP) JSON-RPC 2.0 lifecycle methods including `initialize`, returning server metadata and supported protocol capabilities.

#### Scenario: Server initialization
- **WHEN** client sends a JSON-RPC request with method `initialize`
- **THEN** server returns a valid JSON-RPC 2.0 response containing server metadata and supported protocol capabilities

### Requirement: Discovery of Energy Market Tools
The server SHALL expose available energy market tools via the standard MCP `tools/list` method, describing each tool's name, description, and JSON Schema input parameters.

#### Scenario: Listing available tools
- **WHEN** client sends a JSON-RPC request with method `tools/list`
- **THEN** server returns declarations for `energy_get_spot_prices`, `energy_get_historical_prices`, `energy_get_renewable_forecast`, and `energy_evaluate_dispatch` with parameter schemas

### Requirement: Querying Spot Electricity Prices
The server SHALL support the `energy_get_spot_prices` tool to query Day-Ahead and Intraday electricity prices in EUR/MWh for European bidding zones, reporting hourly price series, daily averages, and negative pricing occurrences.

#### Scenario: Retrieve spot prices for valid bidding zone
- **WHEN** client calls `energy_get_spot_prices` with bidding zone `DE-LU`
- **THEN** server returns a text content block containing hourly price points in EUR/MWh, average price, peak hours, and negative price flags

#### Scenario: Unsupported bidding zone error
- **WHEN** client calls `energy_get_spot_prices` with an invalid or unsupported zone code
- **THEN** server returns an error message listing supported bidding zones

### Requirement: Querying Historical Spot Electricity Prices
The server SHALL support retrieving historical electricity spot prices across a date range via the `energy_get_historical_prices` tool and direct HTTP `GET /api/prices/historical`, validating date ordering and providing daily summaries and negative price counts.

#### Scenario: Retrieve historical prices across valid date range
- **WHEN** client calls `energy_get_historical_prices` for zone `DE-LU` from `2026-10-01` to `2026-10-08`
- **THEN** server returns daily average prices, min/max bounds, and negative price hour counts

#### Scenario: Direct REST historical endpoint query
- **WHEN** client sends HTTP `GET /api/prices/historical?zone=DE-LU&start=2026-10-01&end=2026-10-08`
- **THEN** server returns HTTP 200 OK with JSON payload containing time series data points

#### Scenario: Invalid date range error
- **WHEN** client requests a date range where `endDate` precedes `startDate`
- **THEN** server returns a validation error indicating invalid date bounds

### Requirement: Querying Renewable Generation Forecasts
The server SHALL support the `energy_get_renewable_forecast` tool to provide forecasted wind and solar photovoltaic generation volumes alongside anticipated grid load for a specified date and bidding zone.

#### Scenario: Retrieve renewable forecast
- **WHEN** client calls `energy_get_renewable_forecast` with a valid zone and date
- **THEN** server returns hourly generation forecasts in MW broken down by solar, wind onshore, and wind offshore compared with system load

### Requirement: Asset Dispatch and Flexibility Optimization
The server SHALL support the `energy_evaluate_dispatch` tool to calculate optimal charging and discharging or load-shifting schedules for flexible assets (such as battery energy storage systems or industrial loads) based on price spreads.

#### Scenario: Battery storage arbitrage dispatch
- **WHEN** client calls `energy_evaluate_dispatch` specifying battery capacity in MWh, maximum power in MW, round-trip efficiency, and target date
- **THEN** server calculates optimal charge windows (low/negative prices), discharge windows (peak prices), and expected net trading profit

#### Scenario: Invalid asset parameters
- **WHEN** client calls `energy_evaluate_dispatch` with non-positive capacity or power ratings
- **THEN** server returns a validation error explaining the required positive numeric constraints

### Requirement: Exposing Market Status Resources
The server SHALL expose read-only MCP resources via `resources/list` and `resources/read`, providing real-time JSON snapshots of market price benchmarks, historical overviews, and balancing conditions.

#### Scenario: Read today market price resource
- **WHEN** client requests `resources/read` with URI `energy://market/prices/today`
- **THEN** server returns the raw JSON payload with MIME type `application/json` containing today's hourly pricing curve

#### Scenario: Read historical market price resource
- **WHEN** client requests `resources/read` with URI `energy://market/prices/history/de-lu`
- **THEN** server returns the raw JSON payload with MIME type `application/json` containing multi-day price summary

#### Scenario: Unknown resource URI
- **WHEN** client requests `resources/read` with an unrecognized URI
- **THEN** server returns a JSON-RPC error with code -32602 indicating unknown resource

### Requirement: JSON-RPC Error Handling
The server SHALL return standard JSON-RPC 2.0 error codes for malformed requests (-32600), unknown methods (-32601), and invalid parameter types (-32602).

#### Scenario: Unknown method dispatch
- **WHEN** client sends a request with an unhandled method name
- **THEN** server returns a JSON-RPC error response with code -32601 and message "Method not found"
