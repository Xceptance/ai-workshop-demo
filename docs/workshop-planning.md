# Workshop Planning: Topic Outline

A straightforward, jargon-free overview of the workshop topics and progression.

---

## 1. Introduction: AI, Chat, and Agents
* **Traditional Software vs. AI**
  * Rule-based code (`if/else`, loops, SQL): 100% predictable, rigid.
  * Modern AI: Pattern recognition and language generation, flexible but non-deterministic.
  * What AI can and cannot do (strong at text, translation, and synthesis; weak at precise math, verified facts, or direct system access).
* **Chat AI (The Sandbox)**
  * Conversational web interfaces (ChatGPT, Claude).
  * The copy-paste bottleneck: human acts as the manual bridge between the browser, IDE, and terminal.
* **Agents (Doing Work)**
  * The three core parts: Senses (read files), Brain (decide next step), Hands (run tools/commands).
  * The loop: Look at system -> Decide action -> Run tool -> Check result -> Repeat until done.
* **Levels of Autonomy**
  * Level 1: Web chat (Q&A).
  * Level 2: In-editor autocomplete (Copilot).
  * Level 3: Workspace agents with tool access and human approval (Claude Code, Cursor, Antigravity).
  * Level 4: Background agents working on tasks/issues autonomously.
* **Tokens & Usage: Watching Consumption**
  * What is a token? Text chunks (~3–4 characters) that the model reads and writes.
  * Input vs. Output: Input tokens (your prompt + context) vs. Output tokens (the model's generated answer, which costs more and takes longer).
  * Seeing and watching tokens: checking token meters in tools, asking for token counts, and inspecting API usage metadata.
  * Basic strategies to manage usage:
    * Keep prompts focused; avoid pasting huge raw logs or irrelevant files.
    * Ask for concise, direct answers instead of lengthy explanations.
    * Start a fresh chat when switching tasks so you don't pay to re-send old conversation history.

---

## 2. Demo Agent & Behind the Scenes
* **Live Demo Walkthrough**
  * Running the local demo application (`mvn spring-boot:run`).
  * Seeing the split screen: chat on the left, live execution steps on the right.
  * Watching the 6-step loop on a real query:
    1. User asks a question.
    2. App gathers context and available tools.
    3. Model chooses a tool to call.
    4. Java runs the tool method.
    5. Result is returned to the model.
    6. Model produces the final answer.
* **Behind the Scenes (What Usually Gets Hidden)**
  * The raw HTTP request and response sent to the AI provider.
  * How Java methods and parameters become JSON schemas.
  * How the app converts JSON arguments back into typed Java method calls.
  * Context accumulation: why every tool call adds two messages and increases token count/cost.
  * Audit logging: inspecting disk files (`summary.json`, `trace_events.jsonl`, `transcript.md`).

---

## 3. The Essentials: Steering with `AGENTS.md` & Skills
* **`AGENTS.md` (The Repository Guide)**
  * Why it exists: giving the agent a single, clear manual for the project so it stops guessing.
  * What belongs inside:
    * Build, run, and test commands (`mvn clean test`, Java version).
    * Project directory layout and architecture rules.
    * Explicit boundaries: what the agent is *not* allowed to touch or change.
    * Required steps (e.g. write a plan before editing code).
* **Skills (`SKILL.md`)**
  * What a skill is: a folder with instructions and helper files for one specific task (e.g. adding a new tool).
  * Why skills save context: the agent only loads the full skill file when you ask for that specific task.
  * Difference: `AGENTS.md` is always active for the whole repo; skills are loaded on demand.
* **Hands-On: Setting up `AGENTS.md` and Skills**
  * **Setup `AGENTS.md` live**:
    * Create a real `AGENTS.md` in the repository with build commands, directory map, and a negative rule (e.g. "do not edit `pom.xml`").
    * Watch the agent read it at the start of a task and follow it without being prompted.
  * **Design Skill 1: `add-agent-tool`**:
    * Create `skills/add-agent-tool/SKILL.md` with YAML frontmatter (name, description) and a step-by-step checklist for writing a Java `@Tool`, handling errors, and adding tests.
  * **Design Skill 2: `characterization-test`**:
    * Create `skills/characterization-test/SKILL.md` with rules for testing existing code behavior before refactoring.
  * **Watch How They Are Used**:
    * Give the agent a prompt (e.g. "Add a currency converter tool").
    * Observe how the agent spots the skill in its index, loads `SKILL.md` on demand, and executes the exact steps.

---

## 4. Extending Agents with External Tools via MCP
* **How Tool Calling Works**
  * The model does not run code; it returns JSON requesting a tool name and arguments.
  * The host application runs the code and returns the text output.
  * Handling errors: passing exceptions back to the model so it can fix its mistake.
* **Model Context Protocol (MCP)**
  * What MCP is: an open standard for running tools outside the agent process.
  * Connection types: local subprocesses (`stdio`) or network streams (`HTTP/SSE`).
  * Why MCP is close to agents: it uses the exact same tool-calling pattern, just over a standard protocol.
  * The 3 MCP primitives:
    * **Tools**: functions the model can execute. (*Almost everyone only builds Tools!*)
    * **Resources**: data the model can read (files, logs, database schemas). Often bypassed in favor of active query tools.
    * **Prompts**: pre-made prompt templates and commands. (*Almost nobody touches the Prompts protocol!* Host agents manage workflows via client-side skills and system prompts).
* **Extending Agents via MCP in Practice**
  * **Why it matters**: write a tool once in Python, Node, or Go $\rightarrow$ any agent can use it immediately without recompiling.
  * **Connecting an MCP tool**:
    * Plug an external MCP server (e.g. SQLite database or GitHub tool) into the agent via config.
    * Watch the agent discover the new tools automatically through `tools/list`.
    * Ask a question requiring the external tool (e.g. "query the database for active users").
    * Watch the agent send JSON-RPC `tools/call` to the external process, receive the observation, and answer.

---

## 5. How to Do Quality Work with Agents
* **Managing History**
  * Problems with long chats: model loses track of early instructions, gets stuck on old errors.
  * The "try again" trap: why repeating prompts in a failed thread makes things worse.
  * When to stop: reset the chat and clear uncommitted files after 2–3 failed attempts.
* **Managing Context**
  * Context hygiene: ignoring build folders (`target/`, `node_modules/`) with `.claudeignore` or `.cursorignore`.
  * Prompt caching: keeping system prompts and tool lists stable to save cost and speed up requests.
  * Clean handoffs: summarizing current progress into a `HANDOFF.md` file before starting a fresh chat.
* **Writing Specifications First (OpenSpec)**
  * Why prompt-and-pray coding fails on non-trivial features.
  * Spec-first workflow: Explore -> Propose spec -> Human reviews spec -> Agent writes code -> Sync docs.
  * Why it saves time: 2 minutes to review a 1-page markdown spec vs. 45 minutes to debug broken code.
* **Engineering Workflows & Quality Gates**
  * Planning gate: require a plan before touching multiple files.
  * Characterization tests: write tests locking down current behavior before refactoring.
  * Unsticking protocol: stop prompting -> revert dirty files -> test one small hypothesis.
  * Code review checklist:
    * Did the agent introduce slow $O(n)$ loops?
    * Is it creating unnecessary garbage objects in high-throughput code?
    * Are concurrency and thread safety handled correctly?
    * Did the agent weaken or delete existing tests to make them pass?

---

## 6. Programming Examples: Talk, Plan, Guide, and Trail
* **The 4 Core Practices**
  * **Talk**: give specific file paths, clear acceptance criteria, and explicit "DO NOT" rules.
  * **Plan**: have the agent write a short design and ask questions before editing code.
  * **Guide**: implement in phases (data -> logic -> UI -> tests); don't do everything in one prompt.
  * **Trail**: explain non-obvious code in comments, document verification results, and update project notes.
* **Exercise 1: The Wild West — Brute Force on `LRUClockMap` (No `AGENTS.md`)**
  * **The Goal**: Experience firsthand why raw, unguided agent development breaks down on non-trivial code.
  * **The Code**: An open-addressing hash map (`LRUClockMap.java`) with second-chance clock eviction and zero-allocation parallel arrays.
  * **The Task**: Give the agent a single, unconstrained prompt: *"Add TTL support so entries expire after a duration. Make it work and add tests."*
  * **Failure Modes Observed**:
    * Agent guesses wrong build/test commands.
    * Breaks memory invariants by wrapping primitives into `Entry` objects (destroying cache efficiency).
    * Introduces hash collision holes in open-addressing linear probing.
    * Writes flaky timing tests with `Thread.sleep()`.
    * Falls into the "try again" trap: edits or deletes failing tests to make them pass.

* **Exercise 2: Disciplined Engineering — `LRUClockMap` with `AGENTS.md`, TDD & Review**
  * **The Goal**: Take the exact same code and requirement, but apply professional guardrails and a multi-step workflow.
  * **The Guardrails (`AGENTS.md`)**:
    * Explicit build/test commands (`mvn test -Dtest=...`).
    * Negative rules: *"Do NOT allocate wrapper objects (use parallel arrays)"*, *"NEVER delete or weaken tests"*, *"No Thread.sleep"*.
  * **Workflow in Action (Talk, Plan, Guide, Trail)**:
    1. **Characterize**: Write characterization tests locking in existing collision probing and clock eviction *before* touching code.
    2. **Plan**: Prompt the agent for a technical design proposal (parallel `long[] expiry` array, injected `LongSupplier clock`).
    3. **Guide (TDD)**: Implement test-first using a mock clock for deterministic assertions.
    4. **Trail (Harden with Mutation Testing)**: Run code coverage (JaCoCo) and mutation testing (`pitest`) to detect weak assertions, guide the agent to analyze surviving mutants, and harden tests.

* **Exercise 3: Spec-Driven Application — "CueCards" with OpenSpec & MCP**
  * **The Idea**: Build a complete, cohesive small application that humans and machines (agents) can set, read, update, and manage together.
  * **The Application ("CueCards")**:
    * A lightweight living memory board: compact cue cards containing rules, cheat sheets, runbook snippets, or lessons learned.
    * Fields: `id`, `title`, `tags` (`#java`, `#git`, `#reminder`), `content` (markdown), `author` (*human* vs *agent*), `updatedAt`.
    * A clean, visual card deck UI with tag filtering, search, and markdown preview.
  * **The OpenSpec Pipeline**:
    1. **Proposal (`proposal.md`)**: Human & agent shared memory board, goals, non-goals.
    2. **Delta Specs (`specs/cuecard-management/`)**: Behavioral contracts (unique slugs/titles, tag normalization, search semantics, update rules).
    3. **Design & Tasks (`design.md`, `tasks.md`)**: In-memory/file storage $\rightarrow$ REST controller $\rightarrow$ Web UI $\rightarrow$ Verification.
  * **The MCP Extension**:
    * Expose MCP tools: `list_cuecards(tag)`, `read_cuecard(id_or_title)`, `save_cuecard(title, content, tags)`, `update_cuecard(id, content)`.
    * **The Live Demo**: A human creates cards in the browser; an external agent reads instructions from a card and posts back newly learned lessons over MCP; the card board updates live in real-time.
