# Tasks

## 1. Domain Model & Session State

- [x] 1.1 Add tool and MCP server enablement tracking fields and methods to `AgentSession` (`isToolEnabled`, `setToolEnabled`, `isMcpServerEnabled`, `setMcpServerEnabled`, `isMcpToolEnabled`, `setMcpToolEnabled`, `getDisabledTools`, `getDisabledMcpServers`, `getDisabledMcpTools`), and verify by creating unit tests in `AgentSessionTest`.

## 2. Core Execution Engine Filtering & Dispatch Guards

- [x] 2.1 Update `TransparentAgentEngine` to filter `toolRegistry.getAllSpecifications()` against the session's enabled tools list during context assembly, build pure text `ChatRequest` when no tools are active, and guard tool execution dispatch; verify via unit tests in `TransparentAgentEngineTest`.
- [x] 2.2 Update `McpClientManager` and `McpAgentEngine` to bypass discovery and wire dispatch for disabled MCP servers, filter out disabled MCP tools, and verify behavior with tests in `McpAgentEngineTest` and `LocalMcpServerTest`.
- [x] 2.3 Enhance `AgentStepEvent` payloads and metadata for `CONTEXT_ASSEMBLY` and `MCP_DISCOVERY` steps to record both active and disabled tools/servers for glass-box auditing; verify event payloads in engine test assertions.


## 3. Web & REST Controller Endpoints

- [x] 3.1 Expose REST API endpoints in `AgentApiController` or `AgentWebController` (`/api/session/{sessionId}/tools/toggle`, `/api/session/{sessionId}/mcp/servers/toggle`, and `/api/session/{sessionId}/mcp/tools/toggle`) returning updated toggle states and active counts; verify with Spring MVC tests in `AgentApiControllerTest` or `AgentWebControllerTest`.
- [x] 3.2 Update `AgentWebController.populateModel` to pass tool enablement states, server statuses, and active count summaries to Thymeleaf model attributes; verify attributes in `AgentWebControllerTest`.


## 4. UI Templates & Frontend Interactivity

- [x] 4.1 Update `tools-drawer.html` (`#toolsModal`) to add Bootstrap switch toggles for each registered tool, reflecting its current enabled/disabled state; verify template rendering.
- [x] 4.2 Update `mcp-drawer.html` (`#mcpHubModal`) to add switch toggles for each server in the "Connected Servers" tab and for each tool in the "Discovered Tools" tab; verify template rendering.
- [x] 4.3 Update `header.html` badges to display active vs total counts for both reflection tools (e.g. `Tools (3/4)`) and MCP Hub (e.g. `MCP Hub (2/3 servers, 7/9 tools)`); verify badge markup.
- [x] 4.4 Add JavaScript async toggle event listeners in `agent-app.js` to dispatch state changes via `fetch()` and update header badge counts live without page reload; verify interactive toggle behavior.

## 5. End-to-End Verification & Documentation

- [x] 5.1 Run full Maven build and test suite (`mvn clean test`) to ensure all existing and new unit/integration tests pass with 100% success.
