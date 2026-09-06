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
    * **Tools**: functions the model can execute.
    * **Resources**: data the model can read (files, logs, database schemas).
    * **Prompts**: pre-made prompt templates and commands.
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
* **Example 1: Simple Track (`LRUClockMap`)**
  * Code: an open-addressing hash map with second-chance clock eviction.
  * Task: add TTL support / expiring entries.
  * Testing: write characterization tests for second-chance eviction, collision handling, and overwrites.
* **Example 2: Unit Testing, Coverage, Review & Mutation Testing (pitest)**
  * **The Goal**: Write unit tests for existing code, improve them using coverage, perform review cycles, and harden test assertions with mutation testing (`pitest`).
  * **Workflow in Action**:
    1. **Write Initial Tests**: Have the agent inspect an existing class and generate unit tests.
    2. **Coverage Check**: Run code coverage (JaCoCo) to identify uncovered branches and boundary conditions; guide the agent to cover them.
    3. **Review Cycle**: Inspect the tests for AI anti-patterns (weak assertions, tautologies, assertions that don't test anything).
    4. **Mutation Testing (`pitest`)**: Run `pitest` to mutate bytecode. Look at surviving mutants (tests passed even though the code was mutated!), guide the agent to analyze the survival, and harden assertions to kill all mutants.
* **Example 3: Small Application with its own MCP Server**
  * **The Idea**: Build a small application that manages simple data with a clean UI, and write an MCP server on top of it so external agents can interact with it.
  * **The Application**: A lightweight web app maintaining simple data (e.g. tasks, inventory, or notes) with a simple browser UI.
  * **Writing the MCP Server**:
    * Expose tools for an agent to query, create, or modify the data.
    * Expose resources so an agent can read the current state directly.
  * **Workflow in Action**:
    1. **Plan & Build the App**: Use the agent to create the data model, storage, and a simple UI (Talk, Plan, Guide, Trail).
    2. **Implement the MCP Server**: Have the agent build an MCP interface (stdio or HTTP/SSE) exposing the application's actions.
    3. **Drive It from an Agent**: Connect an external agent (Claude Code, Cursor, or our demo agent) to our new MCP server, prompt it to manage the data, and watch the UI update live.
    4. **Leave a Trail**: Document the MCP tool schemas, verify with tests, and record the setup steps.
