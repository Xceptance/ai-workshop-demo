# Workshop Planning: Topic Outline

A straightforward, jargon-free overview of the workshop topics and progression.

---

## 1. Introduction: AI, Chat, and Agents
* **Traditional Software vs. AI**
  * Rule-based code (`if/else`, loops, SQL): 100% predictable, rigid, direct system authority.
  * Modern AI: Statistical pattern recognition and language generation, flexible but non-deterministic.
  * What AI can and cannot do (strong at text, translation, synthesis, and boilerplate; weak at precise math, verified facts, or direct system access without tools).
* **Chat AI (The Sandbox)**
  * Conversational web interfaces (ChatGPT, Claude, Gemini).
  * The copy-paste bottleneck: human acts as the manual bridge between the browser, IDE, and terminal.
  * Context drift & synchronization loss: chat snippets quickly diverge from the real repository state.
* **Agents (Doing Work)**
  * The three core parts: Senses (read files, inspect diffs), Brain (reason, plan next step), Hands (run tools, execute commands).
  * The ReAct loop: Look at system $\rightarrow$ Decide action $\rightarrow$ Run tool $\rightarrow$ Check result $\rightarrow$ Repeat until done.
  * Agent architecture differences: harnesses (Claude Code, Cursor, Antigravity, Kilo) vary in system prompts, context management, and auxiliary tool creation.
* **Security: Life Inside the Loop**
  * Tools define the attack vector: bash and file access expose the environment to indirect prompt injection and destructive operations (`rm -rf`, `git reset --hard`).
  * Harnesses vs. Custom Agents: pre-built tools enforce permission confirmation modals; custom agent runners (Spring Boot, LangChain4j) require developer-implemented sandboxing and authorization.
  * Defensive guardrails: read-only defaults, human-in-the-loop approvals, strict workspace boundaries, and negative constraints in `AGENTS.md`.
* **Levels of Autonomy**
  * Level 1: Web chat (Q&A sandbox, 100% manual copy-paste).
  * Level 2: In-editor autocomplete (Copilot, passive single-file inline suggestions).
  * Level 3: Workspace agents with tool access and human approval (Claude Code, Cursor, Antigravity).
  * Level 4: Background agents working on tasks/issues autonomously (headless workers triggered by issues/CI, submitting PRs).
* **Tokens & Usage: Watching Consumption**
  * What is a token? Text chunks (~3–4 characters or ~0.75 words) that the model reads and writes.
  * Input vs. Output: Input tokens (prompt + context + tool schemas) vs. Output tokens (the model's generated answer, which costs significantly more and takes longer).
  * Seeing and watching tokens: checking token meters in tools, inspecting API usage metadata (`prompt_tokens`, `completion_tokens`), and reviewing audit traces.
  * Basic strategies to manage usage:
    * Keep prompts focused; avoid pasting huge raw logs or irrelevant files.
    * Ask for concise, direct answers instead of lengthy explanations.
    * Reset early: start a fresh chat when switching tasks so you don't pay to re-send old conversation history.
* **Offloading to a New Chat (Clean Handoffs)**
  * The problem with long conversations: attention degradation, compounding token billing on every turn, and error anchoring (fixating on failed attempts).
  * The 3-step offloading workflow:
    1. **Summarize State**: Have the agent write a short progress checkpoint (e.g. `HANDOFF.md`) capturing current status, decisions, and remaining tasks.
    2. **Commit Code**: Check in working changes to Git so the repo is the durable source of truth.
    3. **Fresh Start**: Open a new chat session seeded only with the summary note and relevant file references.
  * The payoff: 80–90% reduction in prompt token overhead, faster response times, and restored reasoning sharpness.

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
  * How Java methods and parameters become JSON schemas via reflection.
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
  * What a skill is: a folder with instructions and helper files for one specific task.
  * Why skills save context: the agent only indexes a lightweight metadata card and loads the full `SKILL.md` on demand.
  * Difference: `AGENTS.md` is always active for the whole repo; skills are loaded on demand.
* **Workflows (`.agents/workflows/*.md`)**
  * What a workflow is: a prescriptive markdown procedure orchestrating multi-phase lifecycles via slash commands (e.g. `/opsx-propose`, `/opsx-apply`).
  * Process vs. Capability: Workflows enforce rigid process boundaries and gates (e.g. "plan first, wait for human review, do not touch code yet"), whereas Skills provide modular domain knowledge and recipes.
  * Three Steering Layers: Rules (`AGENTS.md` = always-on guardrails) vs. Skills (`SKILL.md` = model-invoked on-demand capabilities) vs. Workflows (`workflows/*.md` = user-invoked slash-command processes).
* **Hands-On: Setting up `AGENTS.md` and Skills**
  * **Setup `AGENTS.md` live**:
    * Create a real `AGENTS.md` in the repository with build commands, directory map, and a negative rule (e.g. "do not edit `pom.xml`").
    * Watch the agent read it at the start of a task and follow it without being prompted.
  * **Design Skill 1: `git-smart-commit` (Option 2)**:
    * Create `skills/git-smart-commit/SKILL.md` with rules for auditing staged files (no secrets/artifacts), running compile checks (`mvn test-compile`), and drafting Conventional Commits with verification evidence.
  * **Design Skill 2: `characterization-test` (Option 3)**:
    * Create `skills/characterization-test/SKILL.md` with rules for pinning legacy code behavior and edge cases with concrete assertions before refactoring.
  * **Watch How They Are Used**:
    * Prompt the agent to commit recent changes or write a safety-net test.
    * Observe how the agent spots each skill in its index, loads `SKILL.md` on demand, and executes the exact procedure.

---

## 4. How to Do Quality Work with Agents
* **Managing History**
  * Problems with long chats: context decay, attention dilution, and the model losing track of early instructions.
  * The "try again" trap: repeating prompts in a failed thread reinforces failure patterns and makes things worse.
  * The reset rule: stop prompting, revert dirty files, and start a fresh session after 2–3 failed attempts.
* **Managing Context**
  * Context hygiene: ignoring build folders and caches (`target/`, `.git/`, `node_modules/`) with `.claudeignore` or `.cursorignore`.
  * Prompt caching: structuring stable system prompts, tool definitions, and repository guidelines so the provider can cache tokens, slashing cost and latency.
  * Clean handoffs: summarizing current progress into a `HANDOFF.md` file before starting a fresh chat.
* **Writing Specifications First (OpenSpec)**
  * Why prompt-and-pray coding fails on non-trivial features: ambiguous scope, architectural drift, self-graded "success".
  * The 4 OpenSpec artifacts:
    1. **Proposal (`proposal.md`)**: Problem statement, goals, non-goals (Why & What).
    2. **Delta Specs (`specs/`)**: Strict behavioral contracts using `ADDED`, `MODIFIED`, `REMOVED` with testable scenarios.
    3. **Design (`design.md`)**: Technical architecture, component trade-offs, impact analysis (How).
    4. **Tasks (`tasks.md`)**: Granular checklist executed task-by-task with isolated context.
  * Spec-driven workflow: `/opsx:explore` $\rightarrow$ `/opsx:propose` $\rightarrow$ Human review & `openspec validate` $\rightarrow$ `/opsx:apply` $\rightarrow$ `/opsx:verify` $\rightarrow$ `/opsx:archive`.
  * Why it saves time: 2 minutes to review a 1-page markdown spec vs. 45 minutes to debug broken code.
  * Living specifications vs. throwaway plans: archived changes update living specs in Git, preventing documentation rot.
* **Agile AI-Driven Development (The BMad Method)**
  * What BMad is: an open-source Agile framework orchestrating specialized AI personas instead of relying on a single generalist LLM.
  * The specialized personas:
    * **Mary (Business Analyst)**: Market analysis, competitive research, requirements elicitation.
    * **John (Product Manager)**: PRD creation, user stories, value prioritization.
    * **Sally (UX Designer)**: User journeys, wireframes, interaction models.
    * **Winston (System Architect)**: Architecture spine, technical spikes, component boundaries.
    * **Amelia (Senior Software Engineer)**: Test-driven implementation, code review, automated delivery.
  * The 4-phase lifecycle:
    * Phase 1: Ideation & Discovery (`bmad-brainstorming`, `bmad-prfaq`, `bmad-product-brief`).
    * Phase 2: Planning & Architecture (`bmad-prd`, `bmad-ux`, `bmad-architecture`, `bmad-create-epics-and-stories`).
    * Phase 3: Sprint Planning (`bmad-sprint-planning` readiness gates: PASS/CONCERNS/FAIL).
    * Phase 4: Implementation & Retro (`bmad-build`, `bmad-code-review`, `bmad-qa-generate-e2e-tests`, `bmad-retrospective`).
  * Collaborative tools: `bmad-party-mode` (roundtable debates), `bmad-advanced-elicitation` (Socratic, Red Team critique).
  * OpenSpec vs. BMad:
    * OpenSpec defines *what* to build through machine-validated contracts and delta specs.
    * BMad defines *how* cross-functional teams discover, plan, and deliver agile software.
    * Complementary: OpenSpec delta specs serve as the formal contracts inside BMad's agile execution phases.
* **Engineering Workflows & Quality Gates**
  * Planning gate: require an approved design before touching multiple files.
  * Characterization tests: write tests locking down current behavior before refactoring.
  * Unsticking protocol: stop prompting $\rightarrow$ revert dirty files $\rightarrow$ test one small hypothesis.
  * Code review checklist:
    * Did the agent introduce slow $O(n^2)$ loops?
    * Is it creating unnecessary garbage allocations in hot paths?
    * Are concurrency and thread safety handled correctly?
    * Did the agent weaken or delete existing tests to declare success?

---

## 5. Extending Agents with External Tools via MCP
* **How Tool Calling Works**
  * The model does not execute code; it returns a JSON object specifying tool name and arguments.
  * The host application runs the code and returns the text output as a tool observation.
  * Handling errors: passing exceptions back to the model so it can reason and fix its mistake.
* **Model Context Protocol (MCP)**
  * What MCP is: an open standard for running tools outside the agent process over JSON-RPC 2.0.
  * Connection types: local subprocesses (`stdio`) or network streams (`HTTP/SSE`).
  * Why MCP is close to agents: standardizes the tool-calling pattern across different runtimes and languages.
  * The 3 MCP primitives:
    * **Tools**: functions the model can execute. (*Industry reality: almost everyone only builds Tools!*)
    * **Resources**: passive readable context attached via URI (files, schemas, logs). Frequently bypassed in favor of active search/query tools.
    * **Prompts**: pre-made prompt templates declared by servers into host UIs. (*Almost nobody touches the Prompts protocol*; host agents manage workflows client-side via skills and system prompts).
* **Extending Agents via MCP in Practice**
  * Why it matters: write a tool once in Python, Node, or Go $\rightarrow$ any agent can use it immediately without recompiling.
  * Connecting an MCP tool:
    * Plug an external MCP server (e.g. SQLite database or GitHub tool) into the agent via config (`mcp_config.json`).
    * Watch the agent discover the new tools automatically through `tools/list`.
    * Ask a question requiring the external tool (e.g. "query the database for active users").
    * Watch the agent send JSON-RPC `tools/call` to the external process, receive the observation, and answer.
* **Pitfall: Tool Proliferation & Ambiguity**
  * Tools are context: every MCP tool schema consumes prompt tokens on every turn, diluting model attention.
  * Semantic collisions: overlapping MCP server tools (e.g. competing energy spot price providers) cause routing ambiguity.
  * The "sidequest" trap: a vague prompt triggers confusion; the agent reaches for filesystem tools to inspect the repo instead of calling either tool, derailing completely.
  * Mitigations:
    * Scope and activate MCP servers selectively per task instead of globally.
    * Ensure tool names and descriptions have distinct, disambiguated triggers.
    * Steer with explicit prompts (Talk & Guide) rather than open-ended queries.

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
