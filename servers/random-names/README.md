# Random Names MCP Server

A dedicated, standalone Model Context Protocol (MCP) server built with **Spring Boot 4.1**, **Spring AI 2.0.1**, and **Java 25**, running on port **9000**.

All Java source code in this server is strictly formatted in **Allman style** (opening braces placed on their own line) and extensively documented with educational guidance for workshop attendees.

---

## Workshop Purpose & Learning Objective

When working with Large Language Models (LLMs), a central challenge in agentic architectures is proving that the model is **dynamically invoking an external capability at runtime** rather than generating answers from its static pre-trained weights.

This server serves as the concrete workshop proof:
1. It maintains a managed pool of workshop participant names.
2. When the AI agent needs to pick a participant, form pairs, or search attendees, it calls the MCP server via JSON-RPC 2.0 over Streamable HTTP (SSE).
3. The server tags its responses with provenance markers (such as `[MCP Server: random-names-server (Port 9000)]`), giving the agent and user irrefutable evidence of external tool execution during the ReAct (Reasoning + Acting) loop.

---

## Initial Workshop Participants (10 Names)

The server initializes with the exact 10 workshop participants:

| # | Participant | # | Participant |
|---|-------------|---|-------------|
| 1 | **Alex**    | 6 | **Katrin**  |
| 2 | **Rene**    | 7 | **Johannes**|
| 3 | **Ray**     | 8 | **Bernd**   |
| 4 | **Jane**    | 9 | **Dude**    |
| 5 | **Katja**   | 10| **Mario**   |

Participants can be dynamically added, removed, and searched via both MCP Tools and parallel REST endpoints.

---

## The Three Core MCP Concepts

The Model Context Protocol defines three primary primitives. This server provides reference implementations of all three:

```
                  ┌──────────────────────────────────────────────┐
                  │          Model Context Protocol (MCP)        │
                  └───────┬──────────────┬──────────────┬────────┘
                          │              │              │
             ┌────────────▼───┐  ┌───────▼──────┐  ┌────▼─────────────┐
             │     Tools      │  │  Resources   │  │     Prompts      │
             │   (Execution)  │  │  (Context)   │  │   (Templates)    │
             └────────────────┘  └──────────────┘  └──────────────────┘
```

### 1. Tools (Dynamic Execution)
Callable functions discovered by AI agents at runtime via `tools/list` and executed via `tools/call`:

* **`get_random_name`**: Selects and returns a single random participant from the pool with timestamp, server name, and port metadata. Accepts an optional `exclude` parameter (e.g., exclude current speaker to avoid picking the same person twice).
* **`list_workshop_names`**: Returns the complete formatted roster of current participants and total count.
* **`pick_pair_programming_duo`**: Selects two distinct participants and assigns them to **Driver** and **Navigator** roles for live pairing sessions.
* **`add_workshop_name`**: Adds a new participant to the pool (case-insensitive deduplication, trim validation).
* **`remove_workshop_name`**: Removes an existing participant from the pool (case-insensitive lookup).
* **`search_names_by_letter`**: Filters and returns all participants whose first name starts with the given letter or prefix (case-insensitive).

### 2. Resources (Contextual Data)
Passive, readable data sources attached via custom URI schemas that agents can read for background context without computational side-effects:

* **`names://participants/list`**: Complete JSON document containing all active participants, pool count, and server status.
* **`names://participants/random`**: Dynamic snapshot resource that produces a live random participant snapshot each time it is read.
* **`names://participants/stats`**: Statistical metadata about the pool (total size, alphabetical first/last bounds, timestamp).

### 3. Prompts (Pre-Packaged Prompt Templates)
Structured, reusable interaction patterns exposed to client UIs and agents with typed arguments:

* **`pick_next_presenter`**: Guides the AI to invoke `get_random_name` with an optional `topic` argument and announce the selection transparently.
* **`setup_pair_programming`**: Guides the AI to invoke `pick_pair_programming_duo` with an optional `taskName` argument, setting up collaborative pairing.
* **`inspect_mcp_capabilities`**: Educational prompt contrasting Tools, Resources, and Prompts for workshop attendees.

---

## Architecture & Code Structure

```
servers/random-names/
├── pom.xml                                      # Maven config with Spring Boot 4.1 & Spring AI 2.0.1
├── README.md                                    # This guide
└── src/
    ├── main/
    │   ├── java/com/workshop/randomnames/
    │   │   ├── RandomNamesServerApplication.java# Spring Boot entry point & MCP configuration
    │   │   ├── domain/
    │   │   │   ├── NameService.java             # Thread-safe in-memory participant repository
    │   │   │   └── RandomNameResult.java        # Structured record for audit provenance
    │   │   ├── mcp/
    │   │   │   ├── RandomNamesTools.java        # MCP Concept 1: 6 Dynamic Tools (@McpTool)
    │   │   │   ├── RandomNamesResources.java    # MCP Concept 2: 3 Context Resources (@McpResource)
    │   │   │   └── RandomNamesPrompts.java      # MCP Concept 3: 3 Prompt Templates (@McpPrompt)
    │   │   └── web/
    │   │       └── RandomNamesRestController.java# Parallel REST API for browser inspection
    │   └── resources/
    │       └── application.yml                  # Port 9000 & Streamable HTTP /mcp config
    └── test/
        └── java/com/workshop/randomnames/
            └── RandomNamesServerIntegrationTest.java # 4-layer integration test suite
```

---

## Running the Server

### 1. Build and Run via Maven

From the repository root:
```bash
mvn spring-boot:run -f servers/random-names/pom.xml
```

Or from within `servers/random-names`:
```bash
cd servers/random-names
mvn spring-boot:run
```

The server binds to **http://localhost:9000**.

### 2. Verify with Human REST Endpoints

Open a browser or use `curl`:

```bash
# Server status & initial 10 participants
curl -s http://localhost:9000/ | jq .

# Quick random name selection
curl -s http://localhost:9000/api/names/random | jq .

# Search participants by letter (e.g. 'K' -> Katja, Katrin)
curl -s "http://localhost:9000/api/names/search?letter=K" | jq .

# Add a participant
curl -s -X POST "http://localhost:9000/api/names?name=Ada" | jq .

# Remove a participant
curl -s -X DELETE "http://localhost:9000/api/names/Ada" | jq .
```

---

## Verifying the MCP Protocol (JSON-RPC 2.0 over Streamable HTTP)

AI clients connect to `POST http://localhost:9000/mcp`. You can simulate client interactions using `curl`:

### Step 1: Initialize Session Handshake
```bash
curl -i -X POST http://localhost:9000/mcp \
  -H "Content-Type: application/json" \
  -H "Accept: text/event-stream, application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 1,
    "method": "initialize",
    "params": {
      "protocolVersion": "2024-11-05",
      "capabilities": {},
      "clientInfo": {
        "name": "workshop-inspector",
        "version": "1.0.0"
      }
    }
  }'
```
*Notice the response includes server capabilities and optionally an `Mcp-Session-Id` header.*

### Step 2: List Discovered Tools
```bash
curl -s -X POST http://localhost:9000/mcp \
  -H "Content-Type: application/json" \
  -H "Accept: text/event-stream, application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 2,
    "method": "tools/list",
    "params": {}
  }' | jq .
```

### Step 3: Execute a Tool Call (`get_random_name`)
```bash
curl -s -X POST http://localhost:9000/mcp \
  -H "Content-Type: application/json" \
  -H "Accept: text/event-stream, application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 3,
    "method": "tools/call",
    "params": {
      "name": "get_random_name",
      "arguments": {
        "exclude": "Mario"
      }
    }
  }' | jq .
```

### Step 4: Execute a Tool Call (`search_names_by_letter`)
```bash
curl -s -X POST http://localhost:9000/mcp \
  -H "Content-Type: application/json" \
  -H "Accept: text/event-stream, application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 4,
    "method": "tools/call",
    "params": {
      "name": "search_names_by_letter",
      "arguments": {
        "letter": "K"
      }
    }
  }' | jq .
```

### Step 5: Read a Resource (`names://participants/list`)
```bash
curl -s -X POST http://localhost:9000/mcp \
  -H "Content-Type: application/json" \
  -H "Accept: text/event-stream, application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 5,
    "method": "resources/read",
    "params": {
      "uri": "names://participants/list"
    }
  }' | jq .
```

---

## Connecting to AI Clients (Claude Desktop / Antigravity / Cursor)

To attach this server to an external AI agent client, configure the MCP client settings (e.g., `claude_desktop_config.json` or `.gemini/antigravity-ide/mcp_config.json`):

```json
{
  "mcpServers": {
    "random-names-server": {
      "url": "http://localhost:9000/mcp",
      "transport": "sse"
    }
  }
}
```

Once connected, prompt the agent:
> *"Please pick a random participant from our workshop to present the next topic. Do not pick Mario."*

The agent will emit a tool invocation for `get_random_name(exclude="Mario")` directed to your Spring Boot MCP server on port 9000.

---

## Coding Style Guidelines

All source files in this demo repository adhere to **Allman Style** formatting:
- Opening curly braces (`{`) are placed on their own line matching the indentation of the enclosing statement.
- Comprehensive Javadoc and inline comments explain design decisions, thread-safety models, Spring AI reflection mechanics, and MCP protocol boundaries.
