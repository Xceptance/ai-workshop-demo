# Design

## Context

The workshop platform supports two execution paradigms:
1. **Classic Mode**: `TransparentAgentEngine` queries `ToolRegistry.getAllSpecifications()` and passes all Java reflection tools (`CalculatorTool`, `SystemInfoTool`, `FileInspectionTool`, `WebSearchMockTool`) to the model in `ChatRequest`.
2. **MCP Mode**: `McpAgentEngine` coordinates with `McpClientManager`, discovering tools from configured servers (`local-mcp-server`, `open-meteo-weather`, `energy-market-server`, etc.) via JSON-RPC `tools/list`.

Currently, all tools and servers are always active. See `proposal.md` for motivation and `specs/tool-and-mcp-management/spec.md` for behavioral requirements.

## Goals / Non-Goals

**Goals:**
- Provide per-session isolation for enabled/disabled states so concurrent sessions do not interfere with each other.
- Support both coarse-grained (entire MCP server) and fine-grained (individual Java or MCP tool) toggling.
- Prevent disabled tools from appearing in LLM prompt schemas, reducing token usage and focusing agent reasoning.
- Block execution attempts against disabled tools as defense-in-depth.
- Provide responsive, in-place UI toggles in `#toolsModal` and `#mcpHubModal` with live badge counter updates.
- Record toggle status in glass-box audit payloads (`CONTEXT_ASSEMBLY`, `MCP_DISCOVERY`).

**Non-Goals:**
- External persistent storage (database) for tool toggle states across JVM restarts.
- Dynamic addition/deletion of new tool source code or MCP endpoints at runtime (this feature toggles existing configured tools/servers).

## Decisions

### 1. State Encapsulation on `AgentSession`
- **Choice**: Store `disabledTools`, `disabledMcpServers`, and `disabledMcpTools` as thread-safe sets (`Set<String>`) within `AgentSession`.
- **Rationale**: Keeps workshop participants isolated; one student turning off the calculator does not disable it for another student's session.
- **Alternatives Considered**:
  - *Global flags in `ToolRegistry` and `McpClientManager`*: Rejected because it would cause cross-session side effects and race conditions in multi-user workshops.

### 2. Two-Tier MCP Gating (Server vs Tool)
- **Choice**: Implement hierarchical gating in `McpClientManager`:
  - When an MCP server is disabled for a session, `refreshAllServers` skips network/process calls to that server entirely.
  - When an MCP server is enabled, its discovered tools are retrieved, but individual tools can still be disabled.
- **Rationale**: Allows simulating total service outages (disabling a server) as well as selective capability testing (disabling one tool out of many).
- **Alternatives Considered**:
  - *Tool-only filtering*: Still makes network calls to offline servers, causing unnecessary latency timeouts.

### 3. Execution Pipeline Integration & Defense-in-Depth
- **Choice**:
  - **Context Filter**: Filter `ToolSpecification` lists before constructing `ChatRequestParameters`. If all tools are disabled, build `ChatRequest` without tool parameters.
  - **Dispatch Guard**: In both `TransparentAgentEngine` (via `ToolRegistry` check) and `McpAgentEngine` (via `McpClientManager` check), if a tool execution request arrives for a disabled tool, reject it with `ToolExecutionResult` indicating the tool is disabled.
- **Rationale**: Eliminates token overhead while guaranteeing disabled tools cannot be executed.

### 4. Interactive Frontend via Async API
- **Choice**: Add REST endpoints:
  - `POST /api/session/{sessionId}/tools/toggle` (payload: `{ "toolName": "...", "enabled": boolean }`)
  - `POST /api/session/{sessionId}/mcp/servers/toggle` (payload: `{ "serverId": "...", "enabled": boolean }`)
  - `POST /api/session/{sessionId}/mcp/tools/toggle` (payload: `{ "toolName": "...", "enabled": boolean }`)
  Connected to Bootstrap switch inputs in `tools-drawer.html` and `mcp-drawer.html`. Small JavaScript handlers in `agent-app.js` handle change events and update header badge counts.
- **Rationale**: Clean, instant user experience with no page reloads, preserving modal scroll position and SSE streams.

### 5. Glass-Box Observability Audit Payloads
- **Choice**: In `TransparentAgentEngine` (`CONTEXT_ASSEMBLY` step) and `McpAgentEngine` (`MCP_DISCOVERY` step), include `activeTools`, `disabledTools`, and `serverStatus` in event metadata and JSON payload.
- **Rationale**: Workshop participants can inspect the raw JSON payload in the UI modal to verify how disabling a tool altered the context sent to Gemini.

## Risks / Trade-offs

- **[Risk: Model hallucinates tool call when all tools are disabled]** → *Mitigation*: When active tool count is 0, completely omit `ChatRequestParameters.toolSpecifications`, placing the LLM in pure text mode.
- **[Risk: Toggling MCP server invalidates cached tool specifications]** → *Mitigation*: Dynamically filter discovered tools per session based on current server and tool toggle sets during turn processing.
- **[Risk: Stale UI counts if modal toggled while turn is running]** → *Mitigation*: Toggle API returns updated summary counts; UI immediately syncs the header badges.
