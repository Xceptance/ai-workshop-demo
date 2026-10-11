# Spec Delta

## Purpose

Enables fine-grained, session-isolated activation and deactivation of individual Java reflection tools, MCP servers, and discovered MCP tools, controlling model visibility, execution dispatch, and observability.

## ADDED Requirements

### Requirement: Session-isolated tool and server enablement states
The system SHALL maintain activation states (enabled or disabled) for each registered Java reflection tool, configured MCP server, and discovered MCP tool scoped to each conversation session, defaulting all tools and servers to enabled when a session is initialized.

#### Scenario: Default activation upon session initialization
- **WHEN** a new agent session is created
- **THEN** all registered Java tools, configured MCP servers, and their discovered tools are marked as enabled by default

#### Scenario: Session isolation for tool toggles
- **WHEN** a user disables a tool or MCP server in session A
- **THEN** the tool or MCP server remains enabled in session B and in future newly created sessions

### Requirement: Reflection tool filtering in classic mode
In Classic mode, the system SHALL filter tool specifications during context assembly to include only enabled Java reflection tools in the model request parameters, and SHALL reject execution requests targeting any disabled tool with an explanatory error.

#### Scenario: Disabled tool excluded from LLM context
- **WHEN** the `calculate` tool is marked as disabled in the active session
- **THEN** the model chat request parameters omit the `calculate` tool specification from the tool list

#### Scenario: Execution of disabled tool is blocked
- **WHEN** an incoming tool execution request targets a disabled tool
- **THEN** the engine halts execution of that tool and returns a failure result indicating the tool is currently disabled for this session

### Requirement: MCP server level enablement and discovery gating
The system SHALL support enabling and disabling entire MCP servers. When an MCP server is disabled, the system SHALL bypass initialization handshakes and dynamic tool discovery for that server, and SHALL NOT expose any of its tools to the LLM or wire dispatcher.

#### Scenario: Disabled MCP server produces no tools
- **WHEN** an MCP server such as `open-meteo-weather` is set to disabled
- **THEN** the engine skips dynamic discovery for that server and none of its tools appear in the discovered tool list or LLM prompt

#### Scenario: Re-enabling MCP server restores tools
- **WHEN** a previously disabled MCP server is toggled back to enabled
- **THEN** subsequent discovery cycles poll the server, restoring its tools to the session's active toolset

### Requirement: Individual MCP tool filtering in MCP mode
In MCP mode, the system SHALL support disabling specific tools from an enabled MCP server. Even when the parent MCP server is enabled, disabled individual tools SHALL be excluded from the LLM tool specifications list and rejected if invoked.

#### Scenario: Single MCP tool disabled while parent server remains active
- **WHEN** the `get_weather` tool is disabled but the `open-meteo-weather` server is enabled
- **THEN** `get_weather` is omitted from the tool specifications passed to the LLM while other tools from that server remain available

### Requirement: Observability in step events and audit transcripts
The system SHALL capture the enablement status of all tools and MCP servers in the metadata and JSON audit payloads of `CONTEXT_ASSEMBLY` and `MCP_DISCOVERY` execution step events.

#### Scenario: Context assembly event records disabled tools
- **WHEN** an agent turn begins with one or more tools disabled
- **THEN** the `CONTEXT_ASSEMBLY` step event payload contains an `activeTools` list and a `disabledTools` list reflecting current session configuration

#### Scenario: MCP discovery event records server states
- **WHEN** an MCP turn completes discovery
- **THEN** the `MCP_DISCOVERY` step event payload contains the enablement status of each configured MCP server

### Requirement: Interactive UI controls for tools and servers
The user interface SHALL render toggle switches for each Java tool inside the Tools modal, and for each MCP server and MCP tool inside the MCP Hub modal, updating active count badges in the header bar.

#### Scenario: Toggling Java tool in UI
- **WHEN** a user clicks the toggle switch for a Java tool in the Tools modal
- **THEN** the tool state updates immediately without requiring a full page refresh and the header badge reflects the updated active count

#### Scenario: Toggling MCP server in UI
- **WHEN** a user clicks the toggle switch for an MCP server in the MCP Hub modal
- **THEN** the server state updates and all corresponding tools in the Discovered Tools tab reflect the updated parent status

### Requirement: API endpoints for toggling tools and MCP servers
The system SHALL provide REST endpoints to query and toggle the enablement state of individual Java tools, MCP servers, and MCP tools for a specific session ID.

#### Scenario: Toggle Java tool via REST API
- **WHEN** a client sends a POST request to `/api/session/{sessionId}/tools/toggle` with tool name and desired state
- **THEN** the API returns HTTP 200 with the updated tool state and list of active tools for that session

#### Scenario: Toggle MCP server via REST API
- **WHEN** a client sends a POST request to `/api/session/{sessionId}/mcp/servers/toggle` with server name and desired state
- **THEN** the API returns HTTP 200 with the updated server state and list of active servers for that session
