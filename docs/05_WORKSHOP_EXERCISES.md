# 05. Hands-On Workshop Exercises (With Solutions)

This guide contains 14 progressive coding, testing, and engineering exercises designed for workshop attendees, structured across the complete agentic engineering lifecycle:

* **Part I: Mastering Tool Calling & Live ReAct Mechanics** (Exercises 1–5)
* **Part II: How to Talk & Plan** (Exercises 6, 11) — OpenSpec SDD & `AGENTS.md` steering contracts
* **Part III: How to Guide & Trail: Simple Track (`LRUClockMap`)** (Exercises 7, 8) — Characterization testing, clock eviction & concurrency
* **Part IV: How to Guide & Trail: Custom MCP Server Track** (Exercise 14) — Building a small application with a simple UI and writing its own MCP server
* **Part V: Review Loops, Style Enforcement & Living Knowledge** (Exercises 9, 10, 12, 13) — 4-Point AI code review, documentation gates, UI guidebooks & custom skills

---

## Exercise 1: Build a Custom Currency Converter Tool

### 🎯 Objective:
Create a new Spring component `CurrencyConverterTool.java` annotated with `@Tool` that converts amounts between USD, EUR, GBP, and JPY.

### Starter Template (`src/main/java/com/workshop/agent/tools/CurrencyConverterTool.java`):
```java
package com.workshop.agent.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

@Component
public class CurrencyConverterTool {

    // TODO: Annotate with @Tool and implement conversion rates
    public String convertCurrency(
            String fromCurrency,
            String toCurrency,
            double amount
    ) {
        // TODO: Implement conversion logic
        return "";
    }
}
```

### ✅ Solution:
```java
package com.workshop.agent.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;
import java.util.Map;

@Component
public class CurrencyConverterTool {

    private static final Map<String, Double> USD_RATES = Map.of(
            "USD", 1.0,
            "EUR", 0.92,
            "GBP", 0.78,
            "JPY", 155.40
    );

    @Tool("Converts a monetary amount between currencies (USD, EUR, GBP, JPY).")
    public String convertCurrency(
            @P("The source 3-letter currency code (e.g. USD, EUR)") String fromCurrency,
            @P("The target 3-letter currency code (e.g. JPY, GBP)") String toCurrency,
            @P("The numeric amount to convert") double amount
    ) {
        String from = fromCurrency.toUpperCase().trim();
        String to = toCurrency.toUpperCase().trim();

        if (!USD_RATES.containsKey(from) || !USD_RATES.containsKey(to)) {
            return "Unsupported currency. Supported currencies: " + USD_RATES.keySet();
        }

        double amountInUsd = amount / USD_RATES.get(from);
        double converted = amountInUsd * USD_RATES.get(to);

        return String.format("%.2f %s is approximately %.2f %s", amount, from, converted, to);
    }
}
```
*Don't forget to register it in `ToolRegistry.java` constructor if needed, or allow Spring autowiring!*

---

## Exercise 2: Implementing a Context Window Sliding Limit

### 🎯 Objective:
Modify `TransparentAgentEngine.java` to prevent context explosion by retaining only the last 6 messages when constructing the LLM request payload.

### ✅ Solution in `TransparentAgentEngine.java`:
```java
// Keep system prompt + last N messages
int maxHistoryToKeep = 6;
List<ChatMessageDto> allMsgs = session.getMessages();
List<ChatMessageDto> recentMsgs = allMsgs.size() > maxHistoryToKeep
        ? allMsgs.subList(allMsgs.size() - maxHistoryToKeep, allMsgs.size())
        : allMsgs;

for (ChatMessageDto prev : recentMsgs) {
    if ("user".equalsIgnoreCase(prev.role()) && !prev.id().equals(userMsgId)) {
        conversationMessages.add(UserMessage.from(prev.content()));
    } else if ("assistant".equalsIgnoreCase(prev.role())) {
        conversationMessages.add(AiMessage.from(prev.content()));
    }
}
```

---

## Exercise 3: Adding Human-in-the-Loop Confirmation for High-Risk Tools

### 🎯 Objective:
Create a tool execution filter that detects high-risk actions (e.g., file writes or system shutdowns) and requires explicit human approval before proceeding.

### Concept:
In `ToolRegistry.java`:
```java
if (toolName.startsWith("delete") || toolName.startsWith("write")) {
    // Check if user confirmed
    if (!isConfirmedByHuman(request)) {
        return new ToolExecutionResult(toolName, argumentsJson, 
            "HALTED: This action requires human confirmation. Please ask the user for confirmation.", 
            0, false, "Awaiting confirmation");
    }
}
```

---

## Exercise 4: Inspecting Disk Logs & Calculating Total Workshop Cost

### 🎯 Objective:
Open the generated `./logs/sessions/` directory in your terminal. Write a short bash one-liner or Python snippet to sum the total tokens used across all sessions.

```bash
# Sum total tokens across all session summaries
jq -s 'map(.tokenStats.totalTokens) | add' logs/sessions/*/summary.json
```

---

## Exercise 5: Multi-Step Chained Reasoning Challenge

### 🎯 Objective:
Submit this multi-step query into the Agent UI:
> *"What is the current server time in UTC? Then calculate what 24 * 365 is. Finally, list what documentation files exist in the 'docs' directory."*

Watch the right-hand **Glass-Box Inspector** execute:
1. `getCurrentDateTime(timezone="UTC")`
2. `calculate(operation="MULTIPLY", a=24, b=365)`
3. `listDirectory(relativePath="docs")`
4. Synthesize all three observations into one coherent final response!

---

## Exercise 6: Spec-Driven Development with OpenSpec (SDD)

### 🎯 Objective:
Instead of "vibe prompting" code changes directly into the repository, follow the 5-step OpenSpec lifecycle ($\text{Explore} \rightarrow \text{Propose} \rightarrow \text{Human Review} \rightarrow \text{Apply} \rightarrow \text{Archive}$) to define and review a system capability specification before generating code.

### Step 1: Create an OpenSpec Proposal (`openspec/changes/add-json-validator/proposal.md`):
```markdown
# OpenSpec Proposal: Add JSON Schema Validator Tool

## 1. Intent & Scope
Provide a new agent tool `JsonValidatorTool` that validates raw JSON strings against a schema and reports syntax/semantic errors.

## 2. Capability Delta Spec (`delta-spec.md`)
### ADDED Capabilities
- `validateJson(jsonString, schemaString)`: Returns validation result with line/column errors if invalid.

### MODIFIED Invariants
- `ToolRegistry.java`: Automatically registers `JsonValidatorTool` during bootstrap.

## 3. Verification Plan
- Unit tests asserting validation of valid and malformed JSON payloads.
```

### Step 2: Human Review Gate:
The human engineer reviews the proposal markdown and approves or requests changes to the contract *before* any Java files are touched.

### Step 3: Agent Applies Implementation & Syncs to `openspec/specs/`:
Once approved, the agent implements `JsonValidatorTool.java`, runs `mvn test`, and archives the delta proposal into the living specification library at `openspec/specs/tools/json-validator.md`.

---

## Exercise 7: Characterization Unit Testing on Existing Code Before Refactoring

### 🎯 Objective:
Before letting an AI agent refactor an existing component (e.g. `CalculatorTool.java` or `ToolRegistry.java`), generate a comprehensive **Characterization Test Suite** that pins down all existing behaviors, edge cases, and error messages to create an immutable safety net.

### Prompt to the Agent:
> *"Analyze `CalculatorTool.java`. Do NOT modify the production code. Write a comprehensive characterization test class `CalculatorToolCharacterizationTest.java` that covers all operations (ADD, SUBTRACT, MULTIPLY, DIVIDE, POWER, SQRT), boundary conditions (division by zero, negative square roots), whitespace handling, and case insensitivity. Verify that 100% of the tests pass against current behavior."*

### ✅ Characterization Test Pattern (`src/test/java/com/workshop/agent/tools/CalculatorToolCharacterizationTest.java`):
```java
package com.workshop.agent.tools;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

public class CalculatorToolCharacterizationTest {

    private CalculatorTool calculator;

    @BeforeEach
    void setUp() {
        calculator = new CalculatorTool();
    }

    @ParameterizedTest
    @CsvSource({
        "ADD, 10, 5, 15.0",
        "add, 10, 5, 15.0",
        " SUBTRACT , 10, 5, 5.0",
        "MULTIPLY, 10, 5, 50.0",
        "DIVIDE, 10, 5, 2.0"
    })
    void testBasicOperations(String op, double a, double b, double expected) {
        assertEquals(expected, calculator.calculate(op, a, b), 0.0001);
    }

    @Test
    void testDivisionByZeroThrowsArithmeticException() {
        assertThrows(ArithmeticException.class, () -> calculator.calculate("DIVIDE", 10.0, 0.0));
    }

    @Test
    void testNegativeSquareRootThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> calculator.powerOrRoot("SQRT", -4.0, 0.0));
    }
}
```

---

## Exercise 8: ClockMap Concurrency Stress-Testing & Property Tests

### 🎯 Objective:
`ClockMap` is a high-performance, concurrent hash map benchmark. Direct an AI agent to write multi-threaded stress tests and property tests that probe for race conditions, thread contention, and capacity resizing invariants.

### Prompt to the Agent:
> *"Write a multi-threaded concurrency stress test `ClockMapConcurrencyTest.java` for `ClockMap`. Spawn 8 concurrent threads using `ExecutorService` and `CountDownLatch`. Have threads simultaneously perform 10,000 randomized `put`, `get`, and `remove` operations. Assert that after all threads terminate, the map size equals expected entries and no internal bucket pointers are corrupted."*

### ✅ Solution Blueprint:
```java
@Test
void testConcurrentPutAndGetUnderContention() throws InterruptedException {
    final ClockMap<Integer, String> map = new ClockMap<>(16);
    final int threadCount = 8;
    final int opsPerThread = 5000;
    final ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    final CountDownLatch startLatch = new CountDownLatch(1);
    final CountDownLatch doneLatch = new CountDownLatch(threadCount);
    final AtomicInteger errors = new AtomicInteger(0);

    for (int t = 0; t < threadCount; t++) {
        final int threadId = t;
        executor.submit(() -> {
            try {
                startLatch.await(); // Synchronize thread blast-off
                for (int i = 0; i < opsPerThread; i++) {
                    int key = (threadId * opsPerThread) + i;
                    map.put(key, "val-" + key);
                    String retrieved = map.get(key);
                    if (retrieved == null || !retrieved.equals("val-" + key)) {
                        errors.incrementAndGet();
                    }
                }
            } catch (Exception e) {
                errors.incrementAndGet();
            } finally {
                doneLatch.countDown();
            }
        });
    }

    startLatch.countDown(); // Fire all threads
    assertTrue(doneLatch.await(10, TimeUnit.SECONDS), "Stress test timed out - potential deadlock!");
    assertEquals(0, errors.get(), "Detected concurrency read/write inconsistencies!");
    assertEquals(threadCount * opsPerThread, map.size());
    executor.shutdown();
}
```

---

## Exercise 9: AI Code Review Cycles, Code Coverage & Mutation Testing (pitest)

### 🎯 Objective:
Review an AI-generated test suite, measure code coverage to find blind spots, and use mutation testing (`pitest`) to catch weak assertions and guide the agent to harden test quality.

### 📋 Step 1: The 4-Point Review Checklist
1. **[ ] Algorithmic Complexity Check:** Did the AI introduce an accidental $O(n)$ linear scan or stream filter inside a hot loop instead of an $O(1)$ hash lookup?
2. **[ ] Allocation & GC Pressure Check:** Is the AI allocating short-lived objects (e.g. new strings, boxed primitives, temporary stream collectors) inside high-frequency execution paths?
3. **[ ] Concurrency & Memory Model Check:** Are shared mutable fields marked `volatile`, protected by locks, or managed via `AtomicReference`? Is lock acquisition order deterministic to prevent deadlocks?
4. **[ ] Test Mutilation & Assertion Guard:** Did the AI pass tests by silently weakening assertions, deleting edge-case tests, or hardcoding return values?

### 📊 Step 2: Improving Tests Using Code Coverage
1. Generate test coverage report:
   ```bash
   mvn test jacoco:report
   ```
2. Open `target/site/jacoco/index.html` and inspect uncovered branches and error conditions.
3. Prompt the agent with the coverage gap:
   > *"Our coverage report shows the exception handling branch in `CalculatorTool` line 48 is uncovered. Write a test specifically covering invalid operations without touching existing tests."*

### 🔬 Step 3: Hardening Assertions with Mutation Testing (`pitest`)
1. Run mutation coverage:
   ```bash
   mvn test-compile org.pitest:pitest-maven:mutationCoverage
   ```
2. **Analyze Surviving Mutants:**
   * Pitest mutates bytecode (e.g. changing `a + b` to `a - b`, or replacing conditionals `>` with `>=`).
   * If a mutant **survives**, your unit tests passed even though the application logic was broken—meaning your test assertions are too weak!
3. **Prompt the Agent to Kill Surviving Mutants:**
   > *"Pitest reported a surviving mutant in `CalculatorTool`: negated conditional on line 42 survived. Here is our current test. Explain why the mutant survived and add a precise boundary assertion that kills it."*
4. Re-run pitest to verify 100% mutation kill rate.

---

## Exercise 10: Enforcing Coding Style, Quality Gates & Mandatory Documentation

### 🎯 Objective:
Enforce coding standards (formatting, zero raw hex values, naming conventions) and make pre-flight planning and post-flight walkthrough documentation mandatory in your agent workflow.

### Workflow:
1. **Configure Style Invariants in `AGENTS.md`:**
   - Enforce BigDecimal for financial calculations.
   - Forbid raw `#hex` styling in UI components (must use CSS variables).
   - Require characterization tests before refactoring.
2. **Execute the Decision & Documentation Gate:**
   - Before modifying code: Ask agent to generate `implementation_plan.md` with problem summary, user review items, and verification plan.
   - After modifying code: Ask agent to generate `walkthrough.md` with summary of changes and exact test execution outputs.

---

## Exercise 11: Authoring an `AGENTS.md` Steering Contract

### 🎯 Objective:
Create a repository-level `AGENTS.md` file in the project root that defines the tech stack, canonical commands, core architectural rules, and non-negotiable negative guardrails for coding agents.

### ✅ Starter & Solution Template (`./AGENTS.md`):
```markdown
# Repository Agent Guidelines: AI Mini-Agent Workshop

## 1. Tech Stack & Environment Baseline
- Runtime: JDK 21+, Maven 3.9+
- Frameworks: Spring Boot 4.1, LangChain4j 1.0, Google Gemini 3.7 Flash, HTMX, Bootstrap 5.3

## 2. Canonical Development Commands
- Build & Run Tests: `mvn clean test`
- Run Single Test: `mvn test -Dtest=ClassName#methodName`
- Start Local App: `mvn spring-boot:run` (runs at http://localhost:8080)

## 3. Architecture & Directory Routing
- Entry Point: `src/main/java/com/workshop/agent/MiniAgentWorkshopApplication.java`
- Java Tools: Put new `@Tool` components in `src/main/java/com/workshop/agent/tools/`
- ReAct Loop: Implemented in `src/main/java/com/workshop/agent/engine/TransparentAgentEngine.java`
- Disk Logs: Written to `./logs/sessions/{sessionId}/`

## 4. Mandatory Decision & Documentation Workflow
- 🛑 Planning Gate: Tasks touching >1 file require an approved `implementation_plan.md` first.
- 🛑 Walkthrough Gate: Completed tasks require a `walkthrough.md` with test execution proofs.

## 5. Non-Negotiable Invariants & Negative Guardrails
- 🛑 NEVER modify `pom.xml` dependencies without explicit human confirmation.
- 🛑 NEVER commit API keys or write unmasked secrets to disk logs.
- 🛑 ALWAYS preserve existing comments, docstrings, and characterization tests.
- 🛑 ALWAYS maintain English-only documentation throughout the repository.

## 6. Verification Standards
- Every new `@Tool` must include an automated JUnit test.
- All tests must pass before submitting a Pull Request.
```

---

## Exercise 12: Authoring a Reusable UI Pattern Guidebook

### 🎯 Objective:
Create a modular UI pattern guide in `.knowledge/ui-patterns/stat_card.md` that defines design tokens, semantic HTML structure, and anti-patterns for metric cards so an agent can generate consistent UI components.

### ✅ Starter & Solution Template (`.knowledge/ui-patterns/stat_card.md`):
```markdown
---
title: Stat Card Component Pattern
type: ui-pattern
tags: [ui, bootstrap5, metrics, cards]
framework: bootstrap-5.3
---

# Metric / Stat Card Pattern

## 1. Context & Use Cases
Use this pattern to display single KPI metrics, token counters, or live status summaries in dashboards.

## 2. Design Tokens
- Background: `var(--bs-body-bg)` or glassmorphism `backdrop-filter: blur(8px)`
- Border: `1px solid var(--bs-border-color-translucent)`
- Value Accent: `var(--bs-primary)` / font-weight `700`

## 3. Canonical Blueprint (HTML/Thymeleaf)
```html
<div class="card shadow-sm border-0 bg-body-tertiary h-100">
  <div class="card-body d-flex flex-column justify-content-between p-3">
    <span class="text-muted small text-uppercase fw-semibold" th:text="${statTitle}">Total Tokens</span>
    <h3 class="mb-0 fw-bold text-primary" th:text="${statValue}">1,420</h3>
    <small class="text-secondary mt-1" th:text="${statSubtitle}">Duration: 1.42s</small>
  </div>
</div>
```

## 4. Negative Guardrails ("DO NOT...")
- 🛑 DO NOT use inline `style="..."` attributes.
- 🛑 DO NOT hardcode arbitrary hex colors; use CSS variables or Bootstrap theme classes.

## 5. Verification Checklist
- [ ] Card layout stacks cleanly on mobile viewports (< 768px).
- [ ] Accessible font contrast matches WCAG AA standards.
```

---

## Exercise 13: Writing a Custom Agent Skill (`SKILL.md`)

### 🎯 Objective:
Create a reusable agent skill in `skills/add-agent-tool/SKILL.md` that provides step-by-step instructions for an agent on how to create, test, and register a new Java `@Tool` in this repository.

### ✅ Starter & Solution Template (`skills/add-agent-tool/SKILL.md`):
```markdown
---
name: add-agent-tool
description: Step-by-step procedure for creating, testing, and registering a new Java @Tool component in the AI Mini-Agent Workshop. Use whenever the user asks to add a new tool or capability to the agent.
---

# How to Add a New Agent Tool

## 1. Pre-Flight Checklist
- Confirm the tool name, required parameters, return types, and business logic.

## 2. Step-by-Step Procedure
1. **Create the Tool Class:**
   - Create `src/main/java/com/workshop/agent/tools/<ToolName>Tool.java`.
   - Annotate class with `@Component`.
   - Annotate public method with `@Tool("Clear description of what tool does")`.
   - Annotate each parameter with `@P("Description of parameter")`.
2. **Implement Safe Error Handling:**
   - Wrap risky operations in `try-catch` and return descriptive error strings (e.g. `"ERROR: ..."`).
3. **Add Automated Unit Tests:**
   - Create `src/test/java/com/workshop/agent/tools/<ToolName>ToolTest.java`.
   - Test both success and error edge cases.
4. **Verify Reflection & Execution:**
   - Run `mvn test -Dtest=<ToolName>ToolTest`.
   - Launch app via `mvn spring-boot:run` and verify the tool appears in the Glass-Box tool list.

## 3. Negative Guardrails
- 🛑 DO NOT throw uncaught checked exceptions from `@Tool` methods; always return meaningful error messages.
- 🛑 DO NOT access external networks without timeout parameters.
```

---

## Exercise 14: Custom MCP Server Track — Small Application with its own MCP Server

### 🎯 Objective:
Put all 4 skills into practice (**Talk**, **Plan**, **Guide**, and **Leave a Proper Trail**) by directing an AI agent to build a small web application maintaining simple data (e.g. tasks, items, or inventory) with a clean browser UI, and then expose a custom **MCP Server** directly on top of it so external agents can query and manipulate the data.

---

### 🗣️ Step 1: How to TALK (The Master Prompt)
Avoid vague prompts. Anchor the agent with sharp file contexts, negative guardrails, and explicit acceptance criteria:

```markdown
We want to build a small data-tracking web application with its own Model Context Protocol (MCP) server.

## Context & Invariants:
- Backend: Java 21 / Spring Boot (or lightweight HTTP server).
- Data: Simple in-memory or H2/SQLite store for items (id, title, status, priority, timestamp).
- Frontend: Simple clean browser UI showing the current list of items and live status.
- MCP Server: JSON-RPC 2.0 endpoint (over stdio or HTTP/SSE) exposing tools and resources.

## Negative Guardrails ("DO NOT..."):
- 🛑 DO NOT write code before the OpenSpec proposal is approved.
- 🛑 DO NOT hardcode complex enterprise dependencies; keep the data model and storage minimal.
- 🛑 DO NOT tightly couple the MCP handler with web controllers; use a shared service layer.

## Acceptance Criteria:
1. Web UI displays current items and auto-refreshes or updates smoothly.
2. MCP server responds to `tools/list` declaring `items_list`, `items_add`, and `items_update`.
3. An external agent can call `items_add` via JSON-RPC, and the new item immediately appears in the web UI.
4. Unit and integration tests verify both the web UI controller and the MCP JSON-RPC protocol.
```

---

### 📋 Step 2: How to PLAN (The OpenSpec Gate)
Command the agent to enter **Planning Mode** and author the OpenSpec change proposal in `openspec/changes/custom-mcp-app/`:
1. `proposal.md`: Problem statement, domain data model, transport choice (stdio or HTTP/SSE), and non-goals.
2. `delta-spec.md`: Concrete tool schemas (`items_list`, `items_add`, `items_update`) with parameter validation rules.
3. `tasks.md`: Phased execution checklist with verification commands.

*Human Gate:* Review the proposed specification (2-minute review). Approve or request adjustments *before* code generation begins.

---

### 🧭 Step 3: How to GUIDE (Phased In-Flight Steering)
Steer the agent through five discrete execution phases:

* **Phase 1: Simple Domain Model & Store:**
  * Define `Item.java` (`id`, `title`, `status`, `priority`, `createdAt`).
  * Implement `ItemRepository.java` (concurrent in-memory map or H2 store).
* **Phase 2: Simple Browser UI:**
  * Create a clean web page (`index.html`) displaying the item table and a simple add form.
* **Phase 3: Custom MCP Server Interface:**
  * Implement the MCP protocol handler (JSON-RPC 2.0):
    * `tools/list` $\rightarrow$ returns JSON Schema for `items_list`, `items_add`, `items_update`.
    * `tools/call` $\rightarrow$ dispatches to `ItemService` and returns `{content: [{type: "text", text: ...}]}`.
    * `resources/list` & `resources/read` $\rightarrow$ exposes `items://all` as raw JSON.
* **Phase 4: Protocol Integration Tests:**
  * Write automated tests sending raw JSON-RPC strings over stdio/HTTP and asserting valid MCP responses.
* **Phase 5: External Agent Connection & Live Driving:**
  * Configure an external agent (Claude Code, Cursor, or our workshop demo agent) to register our new MCP server.
  * Prompt the agent: *"Add three high-priority items to our project tracker for the upcoming release."*
  * Watch the agent execute the MCP tool and watch the items appear live in the browser UI!

---

### 👣 Step 4: How to LEAVE A PROPER TRAIL (Durable Artifacts)
Ensure the agent leaves a permanent engineering trail:
1. **Inline Invariant Comments:** Documenting the JSON-RPC error codes and thread-safety invariants.
2. **Post-Flight Walkthrough (`walkthrough.md`):** Complete summary of implemented files, architectural decisions, and raw test execution proofs.
3. **Session Checkpoint (`HANDOFF.md`):** Clean summary allowing future chats to continue without token bloat.
4. **Knowledge Item (`.knowledge/mcp/custom_server_pattern.md`):** Capturing the lightweight MCP server pattern for future team projects.
