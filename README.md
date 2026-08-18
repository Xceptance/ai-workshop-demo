# AI Mini-Agent Workshop: Glass-Box Agent Platform

An educational, open-source Java & Spring Boot 4.1 mini-agent platform powered by **LangChain4j 1.0**, **Google Gemini (gemini-3.7-flash)**, **HTMX**, and **Bootstrap 5**.

Designed specifically to **demystify how AI agents work under the hood** by exposing every micro-step of the ReAct reasoning cycle, parameter negotiation, local method reflection, observation injection, token consumption, and disk auditing.

---

## 🌟 Why This Workshop Project?

Most agent frameworks and tools operate as opaque "black boxes," leaving engineers confused about:
- *How does the model actually choose and call a tool?*
- *What does the raw payload sent to the LLM look like?*
- *How does context window growth and token pricing accumulate during a multi-step task?*
- *How do we audit and log every step to disk for compliance and debugging?*

This project provides a **100% transparent ("Glass-Box") engine** with a real-time reactive UI and granular disk logging.

```mermaid
flowchart TD
    User([User / Learner]) -->|Query via HTMX UI| Web[Spring Boot Web Controller]
    Web --> Engine[Transparent Agent Engine]
    
    subgraph Glass-Box Execution Loop
        Engine -->|1. Context & Tool Schemas| Req[Raw Model Request Payload]
        Req -->|2. HTTP POST| Gemini[Google Gemini API / gemini-3.7-flash]
        Gemini -->|3. Tool Execution Request| Reason[Agent Decision & JSON Args]
        Reason -->|4. Method Dispatch| Tools[Java Tools: Calc, Files, System, Web]
        Tools -->|5. Observation / Result| Memory[Chat Memory & Message List]
        Memory -->|6. Re-evaluate / Final Answer| Gemini
    end
    
    Engine -->|Real-Time SSE Events| UI[Live Stepper UI & Token Meter]
    Engine -->|Complete Audit Traces| Disk[(./logs/sessions/ Audit Trail)]
```

---

## 🚀 Quick Start

### 1. Prerequisites
- **JDK 21+** installed (`java -version`)
- **Maven 3.9+** (`mvn -version`)
- A **Google Gemini API Key** (or run offline with the built-in Mock Agent!)

### 2. Configure Your API Key
You can provide your Gemini key in any of the following ways (in priority order):

1. **Local File (Recommended for local dev):** Create an `api.key` file in the project root:
   ```bash
   echo "AIzaSy..." > api.key
   ```
2. **Environment Variable:**
   ```bash
   export GEMINI_API_KEY="AIzaSy..."
   ```
3. **Application Properties:** In `src/main/resources/application.yml` or `-Dgemini.api-key=...`
4. **Offline Mode:** If no key is configured, the application automatically launches in **Offline Mock Mode**, allowing you to explore the UI and tool calling flow without an API key!

### 3. Run the Application
```bash
# Start the Spring Boot application
mvn spring-boot:run
```

Open your browser to: **[http://localhost:8080](http://localhost:8080)**

---

## 🔬 Key Features & Components

| Component | Description |
|---|---|
| **Two-Pane Glass-Box Dashboard** | Left: Interactive Chat with prompt presets. Right: Live ReAct execution timeline, step-by-step inspector, and token meters. |
| **Real-Time Streaming (SSE)** | Steps are broadcast live from the Java engine to the browser via Server-Sent Events (`/api/stream/{sessionId}`). |
| **Full Disk Audit Logging** | Every conversation turn is saved to `./logs/sessions/{sessionId}/` with `summary.json`, `trace_events.jsonl`, `raw_payloads.json`, and human-readable `transcript.md`. |
| **Educational Tool Suite** | Included Java tools (`@Tool`): Calculator, System & Time Info, Local File Explorer/Search, and Web Knowledge Lookup. |
| **Model Configurable** | Defaults to `gemini-3.7-flash` (also supports `gemini-2.0-flash`, `gemini-1.5-flash`, and offline mock). |

---

## 📂 Project Structure

```
ai-mini-agent-workshop/
├── pom.xml                                   # Spring Boot 4.1 & LangChain4j 1.0 dependencies
├── README.md                                 # Workshop documentation
├── api.key                                   # (Optional) Local API key file
├── docs/                                     # In-depth educational guides
│   ├── 01_WHAT_IS_AN_AGENT.md                # ReAct loop, context window, agent theory
│   ├── 02_HOW_TOOL_CALLING_WORKS.md          # JSON Schemas, parameter binding, observation injection
│   ├── 03_LANGCHAIN4J_ARCHITECTURE.md        # LangChain4j abstractions vs manual Java loop
│   ├── 04_OBSERVABILITY_AND_LOGGING.md       # Traces, tokens, cost accounting, debug techniques
│   └── 05_WORKSHOP_EXERCISES.md              # 5 progressive coding exercises with solutions
├── src/main/java/com/workshop/agent/
│   ├── MiniAgentWorkshopApplication.java     # Spring Boot entry point
│   ├── config/                               # Gemini & ApiKey discovery configuration
│   ├── engine/                               # TransparentAgentEngine ReAct execution loop
│   ├── logging/                              # Disk audit logger, Markdown transcripts, ANSI logs
│   ├── model/                                # Events, Session state, Token metrics, DTOs
│   ├── tools/                                # Java @Tool components & reflection registry
│   └── web/                                  # HTMX endpoints, SSE streams, REST controllers
├── src/main/resources/
│   ├── application.yml                       # Agent properties & system prompts
│   ├── static/                               # Glass-box CSS and reactive JavaScript
│   └── templates/                            # Thymeleaf & HTMX two-pane templates
└── logs/sessions/                            # Generated disk audit traces per session
```

---

## 📚 Workshop Documentation Curriculum

Explore the detailed guides in the [`docs/`](docs/) directory:

1. **[01. What is an AI Agent?](docs/01_WHAT_IS_AN_AGENT.md)** — Core theory, LLM vs. Agent, and the ReAct pattern.
2. **[02. How Tool Calling Works](docs/02_HOW_TOOL_CALLING_WORKS.md)** — JSON Schema generation, method dispatch, and error handling.
3. **[03. LangChain4j Architecture](docs/03_LANGCHAIN4J_ARCHITECTURE.md)** — High-level `AiServices` vs. Low-level transparent Java loops.
4. **[04. Observability & Token Logging](docs/04_OBSERVABILITY_AND_LOGGING.md)** — Inspecting disk traces, token meters, and debugging agent loops.
5. **[05. Workshop Exercises & Solutions](docs/05_WORKSHOP_EXERCISES.md)** — 5 hands-on challenges for attendees to build custom tools and guardrails.

---

## 🧪 Running Tests
```bash
mvn clean test
```

---

## 📄 License
MIT License. Created for AI Agent Workshops & Educational Demonstrations.
