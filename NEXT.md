# Next Steps Plan: AI Mini-Agent Workshop & Presentation Overhaul

This document consolidates all current notes, recent slide refactorings, structural alignments, and actionable next steps for the **AI Mini-Agent Workshop** and its companion presentation (`650-ai-agents-mcp-and-more.html`).

---

## 1. Recent Evolution & Core Themes

### 1.1 Slide Design Modernization (Flat Concise Schema)
The presentation slides are being migrated from deeply nested, text-heavy bullet trees into a punchy, high-impact flat format:
```html
<div class="m">
    <ul>
        <li><strong>Key Concept</strong> Concise explanation or direct takeaway</li>
    </ul>
</div>
```
- **Completed Slides in Section 1**:
  - `What is an Agent?` (Definition, Autonomy, Feedback Loop)
  - `Agent Anatomy: Senses, Brain & Hands` (Senses, Brain, Hands)
  - `The ReAct Execution Loop` (Loop, Human Role, Harness Differences, Result Invariance)
  - `Security: Life inside the loop` (Attack Surface, Blast Radius, Harnesses vs. Custom, Guardrails, Sandboxing)

### 1.2 Critical Conceptual Notes Added
1. **Agent Non-Determinism Across Harnesses**:
   > *"Different agent, same element might not yield the same results at all."*
   - Different runners (Claude Code, Cursor, Antigravity, Kilo, Spring Boot / LangChain4j custom runners) diverge significantly in system prompts, context assembly, tool offerings, and autonomy gates.
   - This underlines why **externalized repository rules (`AGENTS.md`)** and **formal delta specs (OpenSpec)** are critical: they provide deterministic constraints regardless of which agent harness is used.
2. **Defensive Sandboxing & Blast Radius**:
   > *"Sandbox: Show the agent only what it requires - OS sandboxes."*
   - Tools (especially bash/shell and file modifications) turn prompt injection and untrusted web/file content into real system actions.
   - Blast radius mitigation requires **defense in depth**:
     - Read-only tools by default.
     - Human-in-the-loop confirmation gates for destructive commands (`rm -rf`, `git reset --hard`).
     - Strict negative constraints in `AGENTS.md`.
     - **OS-level sandboxing**: containerization, restricted workspace mounts, Linux namespaces, read-only filesystem layers.

---

## 2. Comprehensive Section-by-Section Backlog

### Section 1: Introduction: AI, Chat, and Agents
- [ ] **Flatten "Levels of Autonomy" slide**:
  - Convert nested list to 4 flat bullets:
    - `Level 1: Web Chat` Isolated Q&A sandbox; 100% manual copy-paste bridge.
    - `Level 2: Inline Assistance` Copilot autocompletions scoped to single-file cursor context.
    - `Level 3: Workspace Agents` Claude Code, Cursor, Antigravity with tool execution and human approval gates.
    - `Level 4: Background Autonomous Agents` Headless CI/issue runners handling end-to-end task branches and PRs.
- [ ] **Flatten "Tokens & Usage: Watching Consumption" slide**:
  - Convert to 4 flat bullets:
    - `Token Mechanics` Text chunks (~3–4 chars); code and whitespace consume higher token ratios.
    - `Input vs. Output` Input tokens (prompt, tools, history) vs. Output tokens (expensive, generation latency).
    - `Usage Visibility` Inspect live UI token meters, API metadata (`prompt_tokens`, `completion_tokens`), and disk traces.
    - `Cost Strategies` Prune raw context, ask for brevity, and reset early before history pollutes attention.
- [ ] **Flatten "Offloading to a New Chat: Clean Handoffs" slide**:
  - Convert to 4 flat bullets:
    - `The Problem` Attention degradation, compounding per-turn billing, and error anchoring on failed attempts.
    - `1. Checkpoint State` Have the agent write a short status summary (e.g. `HANDOFF.md` or scratchpad note).
    - `2. Commit Code` Check in working changes to Git as the durable source of truth.
    - `3. Fresh Start` Open a fresh chat seeded only with the summary note and relevant file references (80–90% token reduction).
- [ ] **Sync Agenda Slide Order**:
  - In `650-ai-agents-mcp-and-more.html` line 142, confirm agenda order matches the actual slide sequence:
    - Topic 4: Extending Agents with External Tools via MCP
    - Topic 5: How to Do Quality Work with Agents

---

### Section 2: Demo Agent & Behind the Scenes (Glass Box)
- [ ] **Refactor Section 2 Slides to Flat Schema**:
  - `Live Demo Walkthrough`: Launch command (`mvn spring-boot:run`), split-screen architecture (Chat vs. Live Trace), demystifying model pauses.
  - `The 6-Step Execution Loop`: Keep clear numbered steps (Prompt $\rightarrow$ Context Assembly $\rightarrow$ Tool Selection $\rightarrow$ Java Execution $\rightarrow$ Observation $\rightarrow$ Final Answer/Loop).
  - `What Are Tools?`: Software contract bridging non-deterministic models with deterministic runtimes, consolidating primitives into smart tools.
  - `Behind the Scenes: Reflection & Schemas`: Converting Java `@Tool` annotations and parameter types to JSON Schema.
  - `Context Accumulation`: Visualizing why every tool call turn adds 2 messages, swelling the prompt token payload.
  - `Disk Audit Logging`: Structure of `summary.json`, `trace_events.jsonl`, and `transcript.md`.
- [ ] **Verify Demo Application in `ai-mini-agent-workshop`**:
  - Ensure the Spring Boot backend runs cleanly with `mvn spring-boot:run`.
  - Verify that the SSE event stream updates the right-hand glass-box panel in real-time.

---

### Section 3: The Essentials: Steering with `AGENTS.md` & Skills
- [ ] **Verify Hands-On Skills in `ai-mini-agent-workshop`**:
  - `skills/git-smart-commit/SKILL.md`: Verify staged file audits (no secrets/artifacts), compilation gate (`mvn test-compile`), Conventional Commit drafting with evidence.
  - `skills/characterization-test/SKILL.md`: Verify pinning observed legacy behavior, concrete assertions, and pre-refactoring test pass criteria.
- [ ] **Reinforce the Three Steering Layers**:
  - `Rules (AGENTS.md)`: Always-on repository guardrails and constraints.
  - `Skills (SKILL.md)`: Model-invoked, on-demand capabilities and procedures.
  - `Workflows (.agents/workflows/*.md)`: User-invoked slash-command processes and rigid phase gates.

---

### Section 4: Extending Agents with External Tools via MCP
- [ ] **MCP Primitives Reality Slide**:
  - Emphasize industry reality: **Tools** dominate real-world usage; **Resources** are frequently bypassed in favor of active search tools; **Prompts** protocol is almost unused in practice (clients manage workflows with skills/system prompts).
- [ ] **Tool Proliferation & Ambiguity ("Sidequest Trap")**:
  - Keep the dual energy spot price provider example.
  - Document mitigation strategies: task-scoped server activation, distinctive tool descriptions, explicit "Talk & Guide" prompts.
- [ ] **Verify Local MCP Server in `ai-mini-agent-workshop`**:
  - Test SQLite or demo MCP server connection over `stdio` and HTTP/SSE.

---

### Section 5: How to Do Quality Work with Agents
- [ ] **Context & History Management**:
  - Highlight the "try again" trap: repeating failed prompts deepens error anchoring; enforce the 2–3 failure reset rule.
  - Context hygiene: `.claudeignore` / `.cursorignore` to filter build caches (`target/`, `node_modules/`, `.git/`).
  - Prompt caching: structure stable system instructions and tool definitions first to maximize cache hits.
- [ ] **OpenSpec Integration**:
  - 4 Artifacts: Proposal (`proposal.md`), Delta Specs (`specs/`), Design (`design.md`), Tasks (`tasks.md`).
  - Lifecycle: `/opsx:explore` $\rightarrow$ `/opsx:propose` $\rightarrow$ Validate $\rightarrow$ `/opsx:apply` $\rightarrow$ `/opsx:verify` $\rightarrow$ `/opsx:archive`.
- [ ] **Agile AI-Driven Development (BMad Method)**:
  - 5 Personas: Mary (BA), John (PM), Sally (UX), Winston (Architect), Amelia (Senior Dev).
  - 4 Phases: Ideation, Planning/Architecture, Sprint Planning readiness gates, Implementation & Retro.
- [ ] **Engineering Quality Gates**:
  - Planning gate before multi-file edits.
  - Mutation testing gate (`pitest`) to detect surviving mutants.
  - Code review checklist: $O(n^2)$ loops, hot path allocations, concurrency safety, test weakening detection.

---

### Section 6: Hands-on Programming Exercises
- [ ] **Exercise 1: Wild West Brute-Force on `LRUClockMap` (No `AGENTS.md`)**:
  - Verify starter code in `ai-mini-agent-workshop`.
  - Prepare test prompt: *"Add TTL support so entries expire after a duration. Make it work and add tests."*
  - Document expected failure modes: primitive wrapping, collision holes, `Thread.sleep` tests, test weakening.
- [ ] **Exercise 2: Disciplined Engineering on `LRUClockMap` (`AGENTS.md`, TDD, Review)**:
  - Verify `AGENTS.md` rules, characterization tests, parallel `long[] expiry` design, mock `LongSupplier clock`, and `pitest` mutation coverage.
- [ ] **Exercise 3: Spec-Driven Application — "CueCards" with OpenSpec & MCP**:
  - Verify CueCards data model (`id`, `title`, `tags`, `content`, `author`, `updatedAt`).
  - Verify REST endpoints and visual card deck UI.
  - Verify MCP tool endpoints (`list_cuecards`, `read_cuecard`, `save_cuecard`, `update_cuecard`).
  - Live demo: human creates cards in UI; external agent interacts via MCP in real time.

---

## 3. Immediate Action Plan

| Priority | Action Item | Target Location |
| :--- | :--- | :--- |
| **P1** | Flatten `Levels of Autonomy`, `Tokens & Usage`, and `Offloading` slides | `trainings/modern-trainings/java/650-ai-agents-mcp-and-more.html` |
| **P1** | Sync Agenda slide section numbers with actual presentation flow | `trainings/modern-trainings/java/650-ai-agents-mcp-and-more.html` |
| **P2** | Refactor Section 2 (Demo Agent) slides to flat concise schema | `trainings/modern-trainings/java/650-ai-agents-mcp-and-more.html` |
| **P2** | Review and verify `skills/` implementations (`git-smart-commit`, `characterization-test`) | `ai-mini-agent-workshop/.agents/skills/` |
| **P3** | Dry-run `LRUClockMap` Wild West vs. Disciplined exercises | `ai-mini-agent-workshop` |
| **P3** | Test CueCards OpenSpec workflow and live MCP server integration | `ai-mini-agent-workshop` |
| **P4** | Rehearse full slide deck timing and presentation flow | `650-ai-agents-mcp-and-more.html` |
