# Proposal

## Why

In an AI agent workshop and educational platform, attendees need to observe how an LLM's behavior changes when specific capabilities are present or absent (e.g., comparing answers when a calculator tool is available versus disabled, or when an external weather/energy MCP server is toggled off). Currently, all registered Java tools and all discovered MCP servers and tools are unconditionally passed to the LLM during every turn. 

Providing fine-grained toggling for individual tools (both in-process Java tools and discovered MCP tools) as well as whole MCP servers gives participants an interactive "sandbox" to test tool selection dynamics, fallback behaviors, and token budget impacts in real-time.

## What Changes

- **Per-Session Tool and Server State**: Store enabled/disabled states for tools and MCP servers on `AgentSession`, allowing session-isolated toggling with sensible defaults (all enabled initially).
- **Execution Engine Filtering (Classic Mode)**: Update `TransparentAgentEngine` to filter `toolRegistry.getAllSpecifications()` by the session's enabled tools list during context assembly and LLM chat request preparation. If a tool is disabled, its schema is omitted from LLM parameters and execution dispatch rejects any attempts to invoke it.
- **Execution Engine Filtering (MCP Mode)**: Update `McpAgentEngine` and `McpClientManager` to support enabling/disabling entire MCP servers (skipping discovery and communication when disabled) as well as toggling individual discovered MCP tools.
- **Observability & Audit Reflection**: Update `AgentStepEvent` payloads for `CONTEXT_ASSEMBLY` and `MCP_DISCOVERY` to reflect which tools and servers are active vs disabled, preserving the workshop's glass-box inspection experience.
- **Interactive UI Controls in Tools Drawer**: Add toggle switches to `tools-drawer.html` (`#toolsModal`) next to each registered Java tool, showing enabled/disabled status with instant toggle actions.
- **Interactive UI Controls in MCP Hub**: Add toggle switches in `mcp-drawer.html` (`#mcpHubModal`) on both the "Connected Servers" tab (server-level enable/disable) and the "Discovered Tools" tab (tool-level enable/disable).
- **Header Status Counter Updates**: Update the header badges in `header.html` to display active versus total tool and server counts (e.g., `Tools (3/4)` and `MCP Hub (2/3 servers, 7/9 tools)`).
- **REST / Web Endpoints**: Expose API endpoints for querying and toggling tool and MCP server states per session (`/api/session/{sessionId}/tools/toggle`, `/api/session/{sessionId}/mcp/servers/toggle`, and `/api/session/{sessionId}/mcp/tools/toggle`).

## Capabilities

### New Capabilities
- `tool-and-mcp-management`: Dynamic runtime configuration and toggling of individual reflection tools, MCP servers, and MCP tools per session, including context filtering, execution enforcement, and interactive UI controls.

### Modified Capabilities
*(None - this is a new capability in the system)*

## Impact

- **Core Engines**: `com.workshop.agent.engine.TransparentAgentEngine`, `com.workshop.agent.engine.McpAgentEngine`, and `com.workshop.agent.mcp.client.McpClientManager`.
- **Domain Model**: `com.workshop.agent.model.AgentSession` (tracks disabled tool and server identifiers).
- **Controllers & APIs**: `com.workshop.agent.web.AgentApiController` and `com.workshop.agent.web.AgentWebController` (new endpoints and model attributes).
- **Templates & Frontend**: `src/main/resources/templates/fragments/tools-drawer.html`, `src/main/resources/templates/fragments/mcp-drawer.html`, `src/main/resources/templates/fragments/header.html`, and `src/main/resources/static/js/agent-app.js`.
- **Tests**: Add unit and integration tests verifying tool filtering, server bypassing, toggle persistence per session, and API endpoint behavior.
