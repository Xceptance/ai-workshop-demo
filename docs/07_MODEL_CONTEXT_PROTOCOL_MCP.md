# 07. Model Context Protocol (MCP): The Universal Agent-Tool Bus

## 1. Why MCP Matters: Breaking the Tool Silo

In the first demonstration of this workshop (**Classic Agent Demo**), we saw how an LLM calls local Java methods annotated with `@Tool`:
1. The framework reflects over local classes (`CalculatorTool`, `SystemInfoTool`).
2. It generates JSON schemas for parameter validation.
3. When the LLM decides to call a tool, Java executes the method in-memory (`Method.invoke()`).

While in-process reflection is fast, simple, and ideal for monoliths, it has severe architectural limitations in modern distributed environments:
* **Language Lock-in**: All tools must be written in the same language and classpath as the agent runtime (e.g. Java). You cannot directly call a Python data analysis script, a Node.js scraper, or a Go microservice without writing custom glue code.
* **Process Coupling**: If a tool crashes or consumes massive memory, it brings down the entire agent process.
* **Security & Sandboxing**: In-process tools run with the exact same OS permissions as the web server. They cannot be easily isolated in containers or distinct user boundaries.
* **No Universal Standard**: Every agent framework (LangChain, LlamaIndex, Spring AI, Semantic Kernel) invented its own proprietary tool interface.

The **Model Context Protocol (MCP)** solves this by standardizing the connection between AI models and tools across process and network boundaries.

```
┌────────────────────────────────────────────────────────────────────────┐
│                          MCP Architecture                              │
│                                                                        │
│   ┌─────────────────────┐                 ┌────────────────────────┐   │
│   │   Agent Host        │                 │   MCP Server (Local)   │   │
│   │  (Spring Boot Java) │    JSON-RPC     │ (Java/Python/Node/CLI) │   │
│   │                     │◄───────────────►│  • Math                │   │
│   │   McpClientManager  │  over STDIO /   │  • System Info         │   │
│   │                     │    Loopback     │  • Filesystem          │   │
│   └──────────┬──────────┘                 └────────────────────────┘   │
│              │                                                         │
│              │             JSON-RPC                                    │
│              │         over Streamable HTTP                            │
│              ▼                                                         │
│   ┌─────────────────────┐                                              │
│   │  MCP Server (Remote)│                                              │
│   │   Open-Meteo        │                                              │
│   │  • Current Weather  │                                              │
│   │  • Forecasts        │                                              │
│   └─────────────────────┘                                              │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 2. In-Process Reflection vs. Out-of-Process MCP

| Dimension | Classic Demo (In-Process Reflection) | MCP Demo (Model Context Protocol) |
|---|---|---|
| **Communication** | In-memory JVM dispatch (`Method.invoke()`) | Standard JSON-RPC 2.0 messages over Stdio or HTTP |
| **Tool Discovery** | Spring Bean scanning at application startup | Dynamic protocol handshake (`tools/list`) |
| **Language Support** | Java only | Any language (Java, Python, Node, Go, Rust) |
| **Security Boundary** | Shared memory space with the application | Separate process or sandboxed container |
| **Deployment** | Monolithic jar file | Local subprocesses or distributed microservices |
| **Protocol Inspection** | Internal Java stack traces | Full wire auditing (`initialize`, `tools/list`, `tools/call`) |

---

## 3. The Protocol Wire Mechanics: JSON-RPC 2.0

MCP is strictly grounded in the **JSON-RPC 2.0** specification. Every message is a plain JSON object with `jsonrpc: "2.0"`.

### Phase 1: Handshake & Negotiation

Before discovering or executing any tools, the client and server negotiate protocol versions and capabilities.

#### 1. Client `initialize` Request:
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "initialize",
  "params": {
    "protocolVersion": "2024-11-05",
    "capabilities": {},
    "clientInfo": {
      "name": "ai-mini-agent-workshop",
      "version": "1.0.0"
    }
  }
}
```

#### 2. Server `initialize` Response:
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "result": {
    "protocolVersion": "2024-11-05",
    "capabilities": {
      "tools": { "listChanged": false }
    },
    "serverInfo": {
      "name": "local-workshop-tools",
      "version": "1.0.0"
    }
  }
}
```

#### 3. Client Acknowledgment Notification:
```json
{
  "jsonrpc": "2.0",
  "method": "notifications/initialized"
}
```

---

### Phase 2: Dynamic Tool Discovery (`tools/list`)

The agent queries the server for its available tools and JSON Schema parameter declarations:

#### 1. Client Request:
```json
{
  "jsonrpc": "2.0",
  "id": 2,
  "method": "tools/list",
  "params": {}
}
```

#### 2. Server Response:
```json
{
  "jsonrpc": "2.0",
  "id": 2,
  "result": {
    "tools": [
      {
        "name": "get_current_weather",
        "description": "Get real-time weather conditions for any location worldwide.",
        "inputSchema": {
          "type": "object",
          "properties": {
            "location": {
              "type": "string",
              "description": "City name or coordinates (e.g. 'Berlin', 'Tokyo')"
            }
          },
          "required": ["location"]
        }
      }
    ]
  }
}
```

The agent engine takes this JSON schema and translates it directly into Gemini's `ToolSpecification`, injecting it into the context window sent to the LLM.

---

### Phase 3: Tool Execution Dispatch (`tools/call`)

When the LLM decides to call a tool, the agent engine formats an outbound JSON-RPC request over the wire:

#### 1. Client Request:
```json
{
  "jsonrpc": "2.0",
  "id": 3,
  "method": "tools/call",
  "params": {
    "name": "get_current_weather",
    "arguments": {
      "location": "Berlin"
    }
  }
}
```

#### 2. Server Response:
```json
{
  "jsonrpc": "2.0",
  "id": 3,
  "result": {
    "content": [
      {
        "type": "text",
        "text": "Current Weather for Berlin, Germany:\n- Condition: Partly cloudy ⛅\n- Temperature: 18.5 °C (65.3 °F)\n- Wind Speed: 11.2 km/h"
      }
    ],
    "isError": false
  }
}
```

The agent extracts the observation text from `result.content[0].text` and injects it back into the LLM context as a `ToolExecutionResultMessage`.

---

## 4. MCP Transports: STDIO vs. Streamable HTTP

MCP defines two standard transport mechanisms:

### 1. STDIO Transport (Local Processes)
* **How it works**: The agent host launches a subprocess (e.g. `java -jar ...`, `node server.js`, `python -m mcp_server`) and communicates via the process's standard input (`stdin`) and standard output (`stdout`).
* **Delimitation**: Each JSON-RPC message is sent as a single newline-terminated line (`\n`).
* **Use Cases**: Desktop agents like Claude Desktop, Cursor, and Windsurf running tools on the local workstation.

### 2. Streamable HTTP / SSE Transport (Distributed Services)
* **How it works**: The MCP server runs as an independent HTTP server. Messages are exchanged via standard HTTP POST requests and streaming Server-Sent Events (SSE).
* **Use Cases**: Microservices, cloud-hosted tools (e.g. GitHub, databases, weather services), and multi-tenant architectures.

---

## 5. Exploring the Workshop MCP Demo

In the top navbar of the web dashboard:
1. Click **MCP Demo** to activate Model Context Protocol mode.
2. Click **MCP Hub** to inspect:
   * **Connected Servers**:
     - `local-mcp-server`: Java-based local tools (Math, System, Files, Search).
     - `open-meteo-weather`: Live global weather data (no API key required).
     - `world-weather-online`: Remote cloud endpoint demonstrating external error handling.
   * **Discovered Tools**: The live JSON schemas declared by each server.
   * **Wire Traffic Log**: Real-time record of all JSON-RPC request/response payloads with microsecond timing.
3. Try asking:
   > *"What is the current temperature in Berlin and Tokyo, and calculate the difference between them?"*
4. Watch the Right-Hand Execution Timeline:
   - Look for the purple **# MCP Wire Tool Call** cards.
   - Expand **View MCP Request (JSON-RPC 2.0)** to see the raw `tools/call` JSON payload sent over the wire.
   - Expand **View MCP Response (JSON-RPC 2.0)** to see the server's response.
   - Inspect the disk trace at `./logs/sessions/{sessionId}/mcp_traffic.jsonl`!
