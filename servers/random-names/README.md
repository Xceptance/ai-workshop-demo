# Random Names MCP Server

A standalone Model Context Protocol (MCP) server built with **Spring Boot 4.1**, **Spring AI 2**, and **Java 25** running on port **9000**.

## Purpose

This server demonstrates that an external MCP tool is dynamically invoked by the AI agent rather than the LLM answering from its own internal training weights. When prompted to select a participant, the agent dispatches a standard JSON-RPC `tools/call` over Streamable HTTP to this server, which selects a name from a fixed pool of 10 participants.

## Workshop Participants (10 Names)

1. **Alex**
2. **Rene**
3. **Ray**
4. **Jane**
5. **Katja**
6. **Katrin**
7. **Johannes**
8. **Bernd**
9. **Dude**
10. **Mario**

---

## The Three Core MCP Concepts

This server implements all three primitives of the Model Context Protocol:

### 1. Tools (Dynamic Execution)
Callable functions discovered via `tools/list` that the agent executes at runtime:
* `get_random_name`: Selects and returns a random name from the fixed list of 10 participants, with optional `exclude` parameter (e.g., exclude current speaker).
* `list_workshop_names`: Retrieves the full list of all 10 participants.
* `pick_pair_programming_duo`: Randomly picks two distinct participants from the pool to form a Driver and Navigator pair.

### 2. Resources (Contextual Data)
Passive, readable data sources attached via URI that agents can inspect for context:
* `names://participants/list`: Complete JSON list of all 10 registered participants.
* `names://participants/random`: Dynamic snapshot selecting a single random participant on read.
* `names://participants/stats`: Statistical metadata about the pool (size, alphabetical bounds).

### 3. Prompts (Pre-Packaged Templates)
Pre-configured prompt templates exposed to host UIs and clients with arguments:
* `pick_next_presenter`: Workflow template guiding the AI agent to call `get_random_name` and introduce the next presenter with transparency. Accepts optional `topic` argument.
* `setup_pair_programming`: Template guiding the agent to assign Driver and Navigator roles via `pick_pair_programming_duo`. Accepts optional `taskName` argument.
* `inspect_mcp_capabilities`: Explains and contrasts Tools, Resources, and Prompts.

---

## Running the Server

From the project root:

```bash
cd server/random-names
mvn spring-boot:run
```

Or from `servers/random-names`:
```bash
cd servers/random-names
mvn spring-boot:run
```

The server starts on **http://localhost:9000**.

### Endpoints
* **MCP Streamable HTTP Endpoint**: `POST http://localhost:9000/mcp`
* **Status / Inspection**: `GET http://localhost:9000/` or `GET http://localhost:9000/api/status`
* **Quick Random Test**: `GET http://localhost:9000/api/names/random`
* **All Names**: `GET http://localhost:9000/api/names`

### Verification via curl
```bash
# Health & Status check
curl http://localhost:9000/

# Test random selection
curl http://localhost:9000/api/names/random

# Initialize MCP handshake
curl -X POST http://localhost:9000/mcp \
  -H "Content-Type: application/json" \
  -H "Accept: text/event-stream, application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"test","version":"1.0.0"}}}'
```
