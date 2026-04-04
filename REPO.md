# MineAgent (Minecraft AI Assistant) + Extensions

This document is written for Codex (coding agent). It defines the project goals, architecture, and implementation plan for **MineAgent**, a Minecraft AI assistant base mod, and its extensions like **MineAgent AE** (AE2 addon).

---

## 0) Project Identity

**Base Mod:**
- **Mod name:** MineAgent
- **modid:** `mineagent`
- **Maven group:** `space.controlnet.mineagent`

**AE2 Extension:**
- **Mod name:** MineAgent AE
- **modid:** `mineagentae`
- **Maven group:** `space.controlnet.mineagent.ae`

**Matrix Extension:**
- **Mod name:** MineAgent Matrix
- **modid:** `mineagentmatrix`
- **Maven group:** `space.controlnet.mineagent.matrix`

**Java package namespaces (current):**
- Base: `space.controlnet.mineagent.*`
- AE2: `space.controlnet.mineagent.ae.*`
- Matrix: `space.controlnet.mineagent.matrix.*`

**Common:**
- **Primary target (initial):** Minecraft **1.20.1**
- **License:** AGPL-3.0

**Output jar names (current):**
- `mineagent-<ver>-forge-<mc-ver>.jar`
- `mineagent-<ver>-fabric-<mc-ver>.jar`
- `mineagentae-<ver>-forge-<mc-ver>.jar`
- `mineagentae-<ver>-fabric-<mc-ver>.jar`
- `mineagentmatrix-<ver>-forge-<mc-ver>.jar`
- `mineagentmatrix-<ver>-fabric-<mc-ver>.jar`

---

## 1) Product Goal

MineAgent (base + extensions) adds a new AE2-style block: **AI Terminal**. It provides an in-game chat interface to an agent that can:

1. **Search and inspect recipes** (JEI-like "R/U" capability) using Minecraft’s recipe registry (no JEI dependency).
2. **Query AE2 network inventory and craftables**.
3. **Plan and execute actions** via structured tool calls (e.g., submit AE2 crafting jobs).
4. **Use an approval workflow** for operations that mutate AE2/network state.
5. **Maintain persistent, shareable chat sessions** with multi-viewer realtime sync and server-authoritative concurrency (single in-flight request per session).

Non-goals (MVP):
- No JEI/EMI/REI dependency.
- No external sidecar service.
- No automatic item exporting/pulling to player inventory/world.
- No disk caching of recipe indices.

---

## 2) Loader & Version Strategy

### 2.1 Multi-loader (Architectury)
Use **Architectury** to support multiple loaders with a shared codebase:
- **Forge** (supported)
- **Fabric** (supported)
- **NeoForge** (supported via a dedicated module; practical targets are 1.20.2+ / 1.21+)

**Publishing:** produce separate jars per loader *and* Minecraft version:

- `mineagent-<mod-ver>-forge-<mc-ver>.jar`
- `mineagent-<mod-ver>-fabric-<mc-ver>.jar`
- `mineagent-<mod-ver>-neoforge-<mc-ver>.jar` (when enabled)

Examples:
- `mineagent-0.1.0-forge-1.20.1.jar`
- `mineagent-0.1.0-fabric-1.20.1.jar`

### 2.2 Multi-version
We will **not** attempt “one codebase compiles to many MC versions without version layers.�?Instead:
- Use a **multi-project Gradle workspace** with per-Minecraft-version modules.
- Expect a thin MC-version-specific layer for registries/recipes/UI/networking differences.

---

## 3) Repository Structure (Multi-project, Version-first)

### 3.1 Version-first layout (recommended)
Example for 1.20.1:

- `:base:core`
  - Pure domain logic, loader- and MC-version-agnostic.
  - Contains: agent state models, tool schemas, policy engine, proposal lifecycle, conversation/session state, LLM tool call parsing, recipe search algorithms.
  - Must NOT depend on `net.minecraft.*`, AE2 APIs, or Architectury APIs.
  - May include agent/LLM libraries (LangChain4j, Gson) as they are not MC-specific.

- `:base:common-1.20.1`
  - MC 1.20.1 shared implementation used by all loaders.
  - Contains: recipe indexing logic (using MC APIs), shared networking protocol definitions, shared UI model, tool registry and vanilla `mc.*` tools.
  - May use Architectury common facilities where appropriate.

- `:base:forge-1.20.1`
  - Forge bootstrap, entrypoints, loader-specific event wiring, platform implementations.

- `:base:fabric-1.20.1`
  - Fabric bootstrap, entrypoints, loader-specific event wiring, platform implementations.

- `:ext-ae:core`
  - AE2-specific tool DTOs/schemas (pure Java, no MC/AE2 classes).

- `:ext-ae:common-1.20.1`
  - AE2 tool implementations, AE2 part/block, AE2 network adapters.
  - Registers AE2 tool provider into base registry.

- `:ext-ae:forge-1.20.1`, `:ext-ae:fabric-1.20.1`
  - AE2 extension loader bootstrap and entrypoints.

- `:ext-matrix:*` (scaffolded)
  - Matrix bridge extension modules.

Future versions:
- `:base:common-1.21.x`, `:base:forge-1.21.x`, `:base:fabric-1.21.x`, etc.

---

## 4) Architecture Overview (Four Layers)

### A) Terminal & Interaction Layer (AI Terminal Block)
- Add an AE2-themed block: **AI Terminal**.
- Right-click opens a **chat UI** (screen/menu).
- UI shows:
  - chat messages
  - tool results (structured)
  - proposal cards (actions + risks + estimated impact)
  - execution status (indexing/thinking/waiting approval/executing/done/failed)
- Maintain server-authoritative conversation sessions:
  - sessions persist in world save (`data/`)
  - sessions can be viewed by multiple players (PUBLIC / TEAM / owner)
  - one in-flight request per session (no queue)
- AI language override:
  - client sends `clientLocale` on each request
  - optional `aiLocaleOverride` set per player in UI
  - server uses `effectiveLocale = override.orElse(clientLocale)` for prompt selection

### B) Tool & Execution Layer (Authoritative)
Expose internal capabilities as **tools** with strict validation and deterministic outputs. All world/AE2 mutations happen here on the server.

Key principles:
- Never trust client input.
- Enforce policy and budgets server-side.
- Provide cancellable and observable operations (especially AE2 crafting jobs).

### C) Agent Orchestration Layer (LangGraph4j)
Use **LangGraph4j** to implement a dynamic, cyclic agent loop:
```
User Message
    �?┌─────────────────────────────────────────�?�?          REASON (LLM Call)             �?←─────────────────�?�? Output: { thinking, action, ... }      �?                  �?�? action = "tool_call" | "respond"       �?                  �?└─────────────────────────────────────────�?                  �?         �?                                                   �?    ┌────┴────�?                                              �?    �?        �?                                              �?tool_call   respond                                           �?    �?        �?                                              �?Execute    Final Response �?Done                              �?    �?                                                        �?Append tool result to history ────────────────────────────────�?```
- The LLM decides at each step whether to call a tool or respond directly.
- Supports multi-step reasoning with up to 20 iterations.
- Prefer a "Cursor/Codex-like" stepwise loop rather than generating one huge plan.

### D) Model & Tool Integration (LangChain4j)
Use **LangChain4j** for:
- OpenAI model integration
- tool/function calling integration
- structured output support (JSON schemas / constraints)
- prompt templates with variable substitution
- hot-reloadable OpenAI model configuration

---

## 5) Agent Behavior & Orchestration Rules

### 5.1 High-level plan + stepwise execution
- First: generate a **high-level intent plan** for UI display (subgoals, dependencies).
- Execution: iterate in steps. Each step yields **1�? actions max** (configurable).

### 5.2 State machine
- `IDLE`
- `INDEXING` (recipe index not ready)
- `THINKING` (LLM proposing next step)
- `WAIT_APPROVAL` (proposal awaiting user decision)
- `EXECUTING` (running tool calls)
- `DONE`
- `FAILED`
- `CANCELED`

Notes:
- New user asks are accepted only in `IDLE`/`DONE`/`FAILED` (single in-flight; no queue).
- On world load, transient states normalize (`THINKING`/`EXECUTING` �?`WAIT_APPROVAL` if a proposal exists, else `IDLE`).

### 5.3 Budgets & limits (server enforced)
- max iterations (configurable via `maxIterations`)
- max actions per iteration (configurable via `maxToolCalls`)
- max craft quantity per action
- rate limit player-submitted queries per player/session (applied on the initial reasoning step, not every internal agent iteration)
- timeouts for LLM and for long-running jobs
- size limits for persisted session data (messages/decisions/sessions/message length)

### 5.4 Structured outputs only
- The agent must output **tool-only JSON** objects matching schema.
- Direct replies use the `response` tool with args `{ "message": "..." }`.
- No free-form “instructions�?without tool calls.

### 5.5 Prompt IDs + locale resolution
- Prompt IDs are explicit (e.g., `agent.reason`, `assistant_response.main`).
- Prompts are selected per request using `effectiveLocale = aiLocaleOverride.orElse(clientLocale)`.
- Default prompts are sourced from `assets/mineagent/lang/*.json` and generated into config as `*.default.prompt` for overrides.
- Prompt overrides: `<prompt_id>.prompt` (global) and `<prompt_id>.<locale>.prompt` (per-locale).

---

## 6) Policy / Approval / Safety

### 6.1 Risk levels
- `READ_ONLY`: no state change; auto-approved.
- `SAFE_MUTATION`: AE2 mutations (e.g., request crafting); approval required by default.
- `DANGEROUS_MUTATION`: disruptive operations (bulk jobs, exports, clears); always require approval.

### 6.2 Default approval behavior (MVP)
- Read-only tools execute immediately.
- Write tools generate a proposal and require explicit approval.
- Proposals are bound to the terminal that created them (dimension + blockpos + optional side for AE2 part) so approvals execute against the original terminal context.
- If the bound terminal cannot be resolved (removed/unloaded), approval fails and the session moves to `FAILED`.
- UI must show:
  - what will be crafted
  - count
  - missing materials / feasibility (if available)
  - how to cancel
  - bound terminal location

### 6.3 Audit logging (server)
Log:
- player
- timestamp
- tool name + args (sanitized)
- result + error
- duration
- approval/denial decisions

---

## 7) Tool Surface (MVP)

### 7.1 Recipe tools (no JEI dependency)
- `mc.find_recipes(itemId, pageToken, limit)`
  - JEI "R" behavior (recipes that craft the item)
- `mc.find_usage(itemId, pageToken, limit)`
  - JEI "U" behavior (recipes that use the item)
  - returns recipe summaries (output item, count, ingredients, recipe type)

### 7.2 AE2 tools
Read-only:
- `ae.list_items(query, craftableOnly, includeNbt, limit, pageToken)`
- `ae.list_craftables(query, limit, pageToken)`
- `ae.get_item(itemId, includeNbt)` (optional helper)

Write:
- `ae.simulate_craft(itemId, count)` (best-effort)
- `ae.request_craft(itemId, count, cpuHint?) -> jobId`
- `ae.job_status(jobId)`
- `ae.job_cancel(jobId)`

Notes:
- Do not implement export/pull-to-player/world in MVP.
- Job operations must be observable and cancellable.

---

## 8) Recipe Indexing (Async, In-memory)

- Build a recipe index from MC’s `RecipeManager`.
- Trigger on:
  - server start
  - datapack reload
- Must be **asynchronous**:
  - build new index in background
  - atomically swap references when complete
- While indexing:
  - UI shows “Indexing…�?and recipe search is temporarily unavailable or returns “index not ready�?
Index features:
- output �?recipeIds
- ingredient item/tag �?recipeIds
- keyword tokens for search (item display name / identifiers)
- pagination support

No disk caching for MVP.

---

## 9) Concurrency & Threading Requirements

- Never block:
  - server tick thread
  - client render thread
- Use background executors for:
  - indexing
  - agent reasoning and LLM calls
- World/AE2 actions:
  - run only in server-safe context
  - if APIs require main server thread, schedule accordingly
- Session mutations:
  - use single-winner atomic transitions for state changes
  - reject new asks while busy (no queue)
  - enforce all size limits server-side so persistence stays bounded
- Persistence:
  - sessions persist via vanilla `SavedData` to the world `data/` folder (works on Forge + Fabric for 1.20.1)
  - mark dirty on mutation; avoid doing save/mutation work off-thread
- Networking:
  - event-driven updates (avoid spam)
  - resilient to latency and player disconnects

---

## 10) UI / Networking Protocol (Guidelines)

- Client sends: user message (including `clientLocale` + `aiLocaleOverride`), approval decision, cancel request, session open/create/delete/update.
- Server sends: session list updates, session snapshots (messages/state/proposal/binding), tool results, job status, errors.
- Server broadcasts snapshots to all active viewers of a session after mutations.
- Viewers subscribe on terminal open / open session / create session; unsubscribe when the terminal menu closes.
- Version packets to allow schema evolution.
- Never allow the client to execute tools directly; server validates everything.

---

## 11) Acceptance Criteria (MVP)

1. AI Terminal opens and supports chat UI.
2. Recipe search works without JEI.
3. AE2 inventory/craftables query works.
4. “Craft X�?yields a structured proposal bound to the creating terminal.
5. On approval, a crafting job is created using the bound terminal context; if the terminal is unavailable the session fails with an error.
6. Crafting job status is observable; cancellation works.
7. Sessions persist to world save and survive restart; multi-viewer sessions stay in sync.
8. No blocking of main threads during indexing or agent reasoning.
9. Forge and Fabric 1.20.1 release builds are functional, and `./scripts/build-dist.sh` produces MineAgent-family release jars.

---

## 12) Implementation Guidelines for Codex

- Keep modules clean:
  - `:core` must not reference MC/AE2/Architectury types.
  - `:common-1.20.1` adapts MC/AE2 data into `:core` DTOs.
  - loader modules only contain bootstrapping and platform wiring.
- Prefer immutable snapshots for indices and session state updates.
- Prefer vanilla world persistence primitives in common when they work cross-loader (e.g., `SavedData` in 1.20.1), and keep saved payloads bounded.
- Provide excellent error messages:
  - index not ready
  - AE2 network not found
  - missing pattern / missing materials
  - CPU unavailable
  - LLM error/timeout
  - bound terminal unavailable
- Add logging early; agent systems require observability.
- Ensure configs and secrets are handled safely and documented.

---

## 13) Current Progress / Status (as of 2026-01-16)

> **Note:** This section documents the historical pre-refactor state (when the project was a single `mineagentae` mod). Package names (`space.controlnet.mineagent.ae`), asset paths (`assets/mineagentae`), and config paths (`config/mineagentae`) referenced here have been migrated to the new base+extensions structure. See Section 14-15 for the current refactored state.

### 13.1 Build & Runtime
- **Forge dev client can launch** via `:forge-1.20.1:runClient` with AE2 + GuideMe present (minor warnings about Narrator/flite and old datapacks).
- **Fabric dev client now launches** with `team_reborn_energy` added; remaining crash is Narrator `libflite.so` missing.
- **Per-loader Loom platform set**: `forge-1.20.1/gradle.properties` has `loom.platform=forge`, `fabric-1.20.1/gradle.properties` has `loom.platform=fabric`.
- **Java toolchain is property-driven**: `java_version` (set in `gradle.properties`) feeds release + toolchains for all modules; required (no fallback).
- **Resource pack warnings fixed**: added `pack.mcmeta` to `common-1.20.1`, `forge-1.20.1`, and `fabric-1.20.1` resources.
- **Distribution script** (`scripts/build-dist.sh`) now copies only final per‑loader jars into `dist/` (common/dev jars are ignored).

### 13.2 Dependencies & Mod Metadata
- **AE2 dependency id corrected**:
  - Forge `mods.toml` now depends on `ae2` (not `appliedenergistics2`).
  - Fabric `fabric.mod.json` depends on `ae2`.
- **Team Reborn Energy added (Fabric)**: `team_reborn_energy` is now included in Fabric deps to satisfy AE2’s embedded energy API.
- **Common now compiles against AE2 Fabric + Fabric API**: aligns with ae2_pattern_counter and avoids Forge-only capability leakage.
- **AE2 + GuideMe** are included as `modImplementation` on Forge for dev runtime.
- **Agent deps centralized**: root `addAgentDeps` helper; shaded/implemented only in platform modules.
- **Core module agent deps**: `core` now includes LangChain4j dependencies (via `addAgentDeps`) and Gson for `LangChainToolCallParser`. This is acceptable as agent/LLM libraries are not MC-specific.
- **Common module agent deps**: `common-1.20.1` still includes LangGraph4j for `AgentRunner` (which uses MC-specific `ServerPlayer`).
- **LangChain/LangGraph** remain normal `implementation` (not `modImplementation`) to avoid Forge mod-scanning crashes.

### 13.3 AI Terminal UX / UI
- **Chat input no longer triggers vanilla keybinds** while focused (e.g., `E` won’t close the terminal). Enter submits; ESC closes.
- **MineAgent AE creative tab** shows the AI Terminal part item (no vanilla Functional Blocks exposure).
- **Shared sessions UI (WIP but compiling):** AI Terminal screen has a sessions panel to list/open/create/delete sessions, plus visibility toggle.
- **AI language selector:** AI Terminal UI provides an override (Auto �?`en_us` �?`zh_cn`) sent with each message.
- **Visibility gating:** `TEAM` only appears when `ftbteams` is installed; otherwise visibility cycles `PRIVATE`/`PUBLIC` only.
- **UI alignment plan:** add header status indicator from session state, session “last active�?timestamps in sidebar, and per-message timestamps in chat log (no proposal ETA/input list yet).

- **Tool feedback formatting:** TOOL messages render friendly summaries (localized templates) instead of raw JSON; tool tooltips show raw input/output for debugging.
- **Tool error visibility:** tool failures surface an error line in the chat log so users (and the agent) see why a tool call failed.
- **Tool tooltip font scaling:** TOOL tooltip uses a smaller font scale for readability.
- **Chat log scrolling:** chat log is scrollable via mouse wheel.

### 13.4 AI Terminal Part
- **AE2 part moved to common**: `AiTerminalPart` now lives in `common-1.20.1` under `space.controlnet.mineagent.ae.common.part`.
- **Forge/Fabric use shared part**: loader entrypoints delegate to the common part implementation and registry.
- **Part registry unified**: `MineAgentAePartRegistries` in `common-1.20.1` registers the part item and models directly.
- **Naming/IDs**: item id `mineagentae:ai_terminal`, lang key `item.mineagentae.ai_terminal`.

### 13.5 Agent/LLM Safety
- **LLM initialization is lazy / reflection‑based** to avoid `NoClassDefFoundError` if agent deps are absent.
- **LangChainToolCallParser moved to `core`**: The LLM-call agent layer (`LangChainToolCallParser`) is now in `core` as it contains no Minecraft-specific dependencies. Uses a platform-neutral `Logger` interface for logging.
- **Parsing pipeline extracted to `core`**: `ReflectiveToolCallParser` and `ToolCallParsingService` now live in `core`; `MineAgentAeNetwork` delegates parsing/rate-limit/timeout behavior to them.
- **LLM config + hot reload:** server reads `config/mineagentae/llm.toml`, reloads models on `/mineagentae reload`, and applies rate-limit cooldown at runtime.
- **Prompt resolution:** prompts are sourced from `assets/mineagentae/lang/*.json`, rendered with variables, and emitted to `config/mineagentae/prompts/*.default.prompt` for override.
- **Rate limiting:** `AgentReasoningService` enforces per-player top-level query throttling via `LlmRateLimiter`; the cooldown is applied on the initial reasoning step for a player query, while follow-up iterations inside the same agent loop bypass that cooldown. `AgentRunner` manages the cooldown (default 1500ms) via `setRateLimitCooldown()`.
- **LLM timeout:** LLM call timeout is configurable via `timeoutSeconds` and updated on `/mineagentae reload` (no stale cached timeout).
- **LLM retries:** transient failures (timeout/connection/rate limit) retry up to `maxRetries` with backoff.
- **LLM raw output logging:** when `logResponses = true`, raw LLM responses are logged at debug.
- **LLM audit logging:** All LLM calls are logged via `LlmAuditEvent` with outcome tracking (SUCCESS/TIMEOUT/RATE_LIMITED/ERROR/PARSE_ERROR), duration, player ID, locale, and iteration number.

### 13.6 Code Organization & Refactoring
- **Core module expansion**: Significant refactoring has moved platform-neutral logic from `common-1.20.1` to `core`:
  - **Tool policy logic** (`ToolPolicy`): Risk classification, policy decisions, proposal building, validation
  - **Recipe search algorithm** (`RecipeSearchAlgorithm`): Core search logic, tokenization, candidate resolution
  - **Tool call parsing** (`ToolCallParser` interface + `LangChainToolCallParser` implementation): LLM-based natural language parsing
  - **Audit logging interface** (`AuditLogger`): Platform-neutral audit event logging with `log(AuditEvent)` for tool execution and `logLlm(LlmAuditEvent)` for LLM calls
  - **LLM audit types** (`LlmAuditEvent`, `LlmAuditOutcome`): Track LLM call outcomes with player, prompt ID, locale, iteration, duration, and outcome
  - **Session management** (`ServerSessionManager`, `ClientSessionStore`): Platform-neutral session state management
  - **Multi-session + metadata:** sessions now carry `sessionId`, title, visibility (`PRIVATE`/`TEAM`/`PUBLIC`), owner, timestamps.
  - **Sharing model:** if a player can view a session (PUBLIC / TEAM / owner), they can also chat + approve proposals in it; management operations (delete/rename/visibility) remain owner-only.
  - **ME network behavior:** tool execution always uses the current player’s open AI terminal / ME network context (no session-level ME scoping).
  - **DTOs**: All tool argument DTOs (`ToolArgs`), recipe DTOs, terminal data DTOs
  - **Network packets** (`core.net.c2s`, `core.net.s2c`): All C2S and S2C packet records moved to `space.controlnet.mineagent.ae.core.net.*` for consistent package structure
- **Terminal context split**: added core `TerminalContext` interface with common `TerminalContextFactory` adapter; `ToolRouter` and `AgentRunner` now use the adapter instead of resolving MC menus directly.
- **AI Terminal part moved to common**: `AiTerminalPart` now lives in `common-1.20.1` under `space.controlnet.mineagent.ae.common.part`.
- **Common package migration**: all common code now lives under `space.controlnet.mineagent.ae.common.*` to avoid split packages.
- **Core package consolidation**: all core code now lives under `space.controlnet.mineagent.ae.core.*` (network packets moved from `space.controlnet.mineagent.ae.net` to `space.controlnet.mineagent.ae.core.net`).
- **Part registry unified**: `MineAgentAePartRegistries` in `common-1.20.1` registers the part item and models directly (no `@ExpectPlatform`).
- **Tool specs abstraction:** core defines `AgentTool` + `ToolRender` + `ToolPayload`; common provides `AgentToolRegistry` and shared `ToolOutputFormatter` for UI rendering and item token formatting.
- **Recipe index executor lifecycle fixed**: `RecipeIndexService` recreates the executor after shutdown to avoid resume crashes.
- **Statistics**: Core now contains ~25.5% of total code (30 files, 769 lines), up from ~11.5%. Common-1.20.1 reduced to ~55.1% (16 files, 1660 lines).
- **Dependencies**: `core` now includes LangChain4j and Gson dependencies (agent/LLM libraries are not MC-specific, so allowed in core per architecture guidelines).

### 13.7 Known Next Decisions
1) **Continue code organization**: Review remaining `common-1.20.1` and `forge-1.20.1` code for opportunities to extract platform-neutral logic to the upstream `core` or `common-1.20.1`; keep loader modules as thin bootstraps/registries.

### 13.8 Code Sharing Plan (Next Focus)
- **Goal:** maximize shared logic by moving platform-neutral logic to `:core`, MC/AE2 shared code to `:common-1.20.1`, and keeping loader modules as thin bootstraps only.
- **Loader boundary:** keep only entrypoints + loader-specific wiring in `forge-1.20.1` and `fabric-1.20.1`.
- **Hard anchors (must stay out of core):** anything touching `net.minecraft.*`, `appeng.*`, or `dev.architectury.*` (recipes, UI/menu, networking, registries, AE2 host).
- **Architectury guidance:** prefer interface-based platform abstractions for complex logic; use `@ExpectPlatform` only for small static hooks; verify both loaders after boundary moves.

### 13.9 Shared Sessions v2 (Implemented)
- **Server-authoritative single in-flight gating:** new asks accepted only in `IDLE`/`DONE`/`FAILED`; busy sessions reject with an immediate snapshot broadcast (no queue).
- **Atomic transition helpers:** `ServerSessionManager` now provides single-winner state transitions (`tryStartThinking`, `trySetProposal`, `tryStartExecuting`, `tryFailProposal`, `tryResolveExecution`) and load-time state normalization.
- **Multi-viewer realtime sync:** server tracks session viewers and broadcasts session snapshots to all viewers after session mutations; viewers subscribe on terminal open/open-session/create-session and unsubscribe when the terminal menu closes.
- **Proposal binding + approval semantics:** proposals store a terminal binding (`dimensionId`, `x,y,z`, optional `side`) so approvals execute against the original terminal; if the bound terminal can’t be resolved, the session moves to `FAILED` with an error message and the proposal/binding are cleared.
- **Global persistence (world save):** sessions persist to the world `data/` folder using vanilla `SavedData` APIs (works on both Forge and Fabric for 1.20.1); transient `THINKING/EXECUTING` states normalize on load.
- **Decision history + size limits:** bounded approve/deny decision log is persisted; server-enforced caps exist for messages/decisions/sessions and max message length (currently via JVM properties).
- **Client UX:** send is disabled when the session is not idle-like; proposal cards show the bound terminal location.

### 13.10 LLM Models + Prompts (Implemented)
- **Prompt IDs + locale selection:** `agent.reason` prompt ID exists; each request uses `effectiveLocale = aiLocaleOverride.orElse(clientLocale)`.
- **Dynamic agent loop prompt:** `agent.reason` now expects tool-only JSON objects; direct responses are emitted via the `response` tool with args `{ "message": "..." }`.
- **Prompt sourcing + overrides:** defaults live in `assets/mineagentae/lang/*.json` and are generated into `config/mineagentae/prompts/*.default.prompt` for overrides; global (`<prompt_id>.prompt`) and locale (`<prompt_id>.<locale>.prompt`) overrides are supported.
- **Hot reload:** `/mineagentae reload` reloads LLM config and prompts, then rebuilds the active LLM client/model atomically.
- **LLM config:** OpenAI provider/model/baseUrl/keys/timeouts/retries/rate-limit cooldown are configurable in `config/mineagentae/llm.toml`.
- **LLM agent limits:** `maxToolCalls` and `maxIterations` are configurable in `config/mineagentae/llm.toml`; prompt history now uses the full session history instead of a recent-message window.
- **LLM max tokens default:** `maxTokens` now defaults to `128000` in `LlmConfig.defaults()` unless overridden in config.
- **OpenAI token param compatibility:** builders prefer `maxCompletionTokens` when available; GPT-5 models skip `maxTokens` to avoid unsupported parameter errors.
- **OpenAI-compatible endpoint override:** `baseUrl` can point the OpenAI client at a compatible endpoint instead of the default OpenAI API URL.
- **Observability:** prompt hash, locale, and prompt id are logged during parsing.
- **Tool specs prompting:** prompt now renders a per-tool section via `{{tools_section}}` instead of category-split blocks.

### 13.11 Inline @item Tokens (Implemented)
- **Token format:** `<item id="..." display_name="...">` embedded in message strings.
- **Client-side UI tokenization:**
  - Typing `@` opens a dropdown with item suggestions from `BuiltInRegistries.ITEM`.
  - Fuzzy search matches against localized display name and item ID.
  - Token insertion replaces `@query` with a token object rendered as icon + colored name.
  - On send, tokens are serialized to `<item id="..." display_name="...">` tags.
- **Client-side safety:** raw `<item ...>` typed by users is escaped (`<` �?`&lt;`, `>` �?`&gt;`).
- **Server-side validation:** `MineAgentAeNetwork.findInvalidItemTag()` validates all item IDs against `BuiltInRegistries.ITEM`; messages with invalid IDs are rejected with an error shown to the player.
- **Prompt integration:** `agent.reason` prompt instructs the LLM to treat `<item id="...">` tokens as authoritative item references.

### 13.12 Dynamic Agent Loop (Implemented)
- **Architecture:** Replaced the static 2-step pipeline (LLM #1 �?Tool �?LLM #2 �?Done) with a dynamic agentic loop where the LLM decides at each step whether to call a tool or respond directly.
- **Flow:**
  ```
  User Message �?REASON �?(tool_call �?EXECUTE �?loop back) OR (respond �?END)
  ```
- **Key components (in `core`):**
  - `AgentLoop`: LangGraph4j-based multi-node graph with `reason`, `execute`, and `respond` nodes plus per-request iteration/state handling.
  - `AgentDecision`: Record representing LLM's decision (`TOOL_CALL` or `RESPOND`) with optional thinking, tool call, or response.
  - `AgentReasoningService`: Service that calls LLM with `agent.reason` prompt and parses the JSON response into `AgentDecision`.
  - `AgentLoopResult`: Result of running the agent loop (success with proposal/response, or error).
  - `ConversationHistoryBuilder`: Formats session messages for inclusion in the prompt.
- **Key components (in `common-1.20.1`):**
  - `AgentRunner`: MC-specific wrapper that adapts `ServerPlayer`, terminal binding, and session context into the core `AgentLoop`.
  - `McSessionContext`: MC-facing `AgentSessionContext` implementation for session history, prompt rendering, tool execution affinity, and server-thread marshaling.
- **LLM output format:** tool-only JSON objects; direct responses via `response` tool; multiple tool calls per step supported (JSON array).
- **Iteration limit:** Max iterations per agent loop is configurable (`maxIterations`).
- **Multi-tool calls:** JSON arrays are supported; tool calls are capped by `maxToolCalls`.
- **Context preservation:** `sessionId`, `effectiveLocale`, and agent control state are carried through the graph; MC runtime objects are kept outside serialized graph state and rebound per active session during loop execution.
- **Proposal handling:** When a tool requires approval (e.g., `ae.request_craft`), the loop pauses with a proposal; after approval, the loop resumes with the tool result in session history.
- **Dead code removed:** `handleParsedOutcome`, `applyToolResult`, `parseCommandAsync`, `buildToolParserPrompt`, `buildAssistantPrompt`, `LLM_PARSER`, `ASSISTANT_RESPONDER`, `TOOL_LIST`, `ARGS_SCHEMA` removed from `MineAgentAeNetwork`.
- **Rate limiting:** `AgentReasoningService` uses `LlmRateLimiter` as a per-player query throttle on iteration `0`; internal follow-up iterations for the same user ask are not blocked by the cooldown.
- **LLM timeout:** LLM timeout is configurable and updated on `/mineagentae reload`; retry uses `maxRetries`.
- **Locale preservation on approval resume:** `SESSION_LOCALE` map in `MineAgentAeNetwork` stores the effective locale when a request starts; retrieved when resuming after approval to ensure consistent language.
- **LLM audit logging:** New `LlmAuditEvent` record and `LlmAuditOutcome` enum track all LLM calls with player, prompt ID, locale, iteration, duration, and outcome (SUCCESS/TIMEOUT/RATE_LIMITED/ERROR/PARSE_ERROR).

### 13.13 MCP Tool Runtime (Implemented on 2026-03-29)
- **Remote MCP tool support:** base/common now supports MCP-backed tools discovered at runtime and registered into `ToolRegistry` as normal agent tools.
- **Transports:** MCP client sessions support both `stdio` and public streamable HTTP transports.
- **Runtime lifecycle:** `McpRuntimeManager` loads MCP config on server start, reloads on `/mineagent reload`, unregisters stale runtimes, and keeps the previous healthy runtime when a reload falls back to defaults or a specific server fails discovery.
- **Provider-scoped replacement:** `ToolRegistry.registerOrReplace(...)` supports deterministic replacement of one provider's owned tool set without disturbing unrelated providers.
- **Schema projection:** remote MCP tool schemas are mapped into local `AgentTool` definitions, including execution-affinity metadata.
- **Execution affinity:** MCP tools currently run with `CALLING_THREAD` affinity so the remote invocation stays on the MCP runtime path instead of being forced through server-thread marshaling.
- **Invocation/result handling:** `McpToolProvider` validates JSON-object arguments, namespaces projected tool names by server alias, normalizes MCP result envelopes, preserves structured/text content, and returns fallback errors for unsupported or malformed remote responses.
- **Readonly/runtime regression coverage:** the MCP runtime now has focused regression coverage for config parsing, schema mapping, stdio lifecycle, streamable HTTP lifecycle, reload isolation, provider invocation, fallback rendering, and readonly integration paths.

### 13.14 AI Terminal Visual Identity (Implemented)
- **Custom textures:** AI Terminal now has unique textures distinguishing it from standard AE2 terminals.
- **Background:** Uses AE2's Dark Illuminated Panel style (`ae2:part/monitor_light` with `tintindex: 2`) for a clean solid background without the ME terminal grid pattern.
- **Animated rotating cube (power-on):** The `ai_terminal_on.png` texture displays a wireframe cube rotating horizontally.
  - 16 frames, 5 ticks per frame (4 seconds per full rotation)
  - Uses `.mcmeta` animation system (vanilla Minecraft feature)
  - Rendered with `tintindex: 3` (bright) for visibility
- **Static cube (power-off):** The `ai_terminal_off.png` texture shows the first frame of the cube animation.
  - Rendered with `tintindex: 3` (bright) for consistency with power-on state
- **Dynamic color support:** Both background and cube layers support AE2's color system (changeable via Color Applicator).
- **Model structure:**
  - `ai_terminal_on.json` - Background (monitor_light, tintindex 2) + animated cube overlay (tintindex 3)
  - `ai_terminal_off.json` - Background (monitor_light, tintindex 2) + static cube overlay (tintindex 3)
- **Texture files:**
  - `ai_terminal_on.png` - Animated rotating wireframe cube (16x256, 16 frames stacked)
  - `ai_terminal_on.png.mcmeta` - Animation configuration
  - `ai_terminal_off.png` - Static wireframe cube at first frame position (16x16)
- **Generation script:** `scripts/generate_ai_terminal_textures.py` generates all textures programmatically using 3D rotation math.
  - Configurable: cube size (1.3), tilt angle (25°), frame count (16), animation speed (5 ticks)
  - Static cube uses 0° rotation to match first animation frame
  - Output: `common-1.20.1/src/main/resources/assets/mineagentae/textures/part/`
- **Item model:** Uses `ae2:item/display_base` parent with `ai_terminal_off.png` for the cube icon.

---

## 14) Refactor Plan �?MineAgent Base + Extensions (2026-01-20)

### 14.0 Motivation
- The agent architecture is **general-purpose** and should not be locked to AE2-only gameplay.
- A **base mod + addon mods** structure enables broader Minecraft assistant use cases.
- Clear separation reduces **entanglement**, simplifies maintenance, and scales to more versions/extensions.
- Allows **independent feature growth** (AE2 tools, Matrix bridge) without coupling everything to the base.

### 14.1 Goals (from owner feedback)
- Rename and split into **base mod** + **extension mods**, no back-compat.
- **Base mod** provides a clean, minimal agent API and vanilla `mc.*` tools.
- **Extensions** are separate **addon jars** with their own `modid`s:
  - `mineagent` (base)
- `mineagentae` (AE2 extension)
  - `mineagentmatrix` (Matrix extension)
- Reduce entanglement: `core` is pure and stable; `common-*` uses core via clean interfaces.
- Keep UI identical across base and extensions.
- File migration rule: **copy first �?verify �?remove** (no delete-then-guess).

### 14.2 Directory Layout (nested, plain)
```
/base
  /core
  /common-1.20.1
  /forge-1.20.1
  /fabric-1.20.1

/ext-ae
  /core
  /common-1.20.1
  /forge-1.20.1
  /fabric-1.20.1

/ext-matrix
  /core
  /common-1.20.1
  /forge-1.20.1
  /fabric-1.20.1
```

### 14.3 Dependency Rules
**Base (`mineagent`)**
- `base/core`: no MC/AE2/Architectury deps.
- `base/common-*`: depends on `base/core` + MC APIs.
- `base/forge-*`, `base/fabric-*`: depend on `base/common-*`.

**AE2 extension (`mineagentae`)**
- `ext-ae/core`: depends on `base/core`.
- `ext-ae/common-*`: depends on
  - `ext-ae/core`
  - `base/core`
  - `base/common-*`
  - **AE2 mod APIs** (Forge/Fabric)
- `ext-ae/forge-*`, `ext-ae/fabric-*`: depend on
  - `ext-ae/common-*`
  - loader APIs
  - AE2 loader APIs

**Matrix extension (`mineagentmatrix`)**
- `ext-matrix/core`: depends on `base/core`.
- `ext-matrix/common-*`: depends on
  - `ext-matrix/core`
  - `base/core`
  - `base/common-*`
  - Matrix client libs (if used)
- `ext-matrix/forge-*`, `ext-matrix/fabric-*`: depend on
  - `ext-matrix/common-*`
  - loader APIs

### 14.4 Packaging (separate jars)
- `mineagent-<ver>-forge-1.20.1.jar`
- `mineagent-<ver>-fabric-1.20.1.jar`
- `mineagentae-<ver>-forge-1.20.1.jar`
- `mineagentae-<ver>-fabric-1.20.1.jar`
- `mineagentmatrix-<ver>-forge-1.20.1.jar`
- `mineagentmatrix-<ver>-fabric-1.20.1.jar`

### 14.5 Extension Types
- **Tool extension (AE2):** adds `ae.*` tools + AI terminal block/part.
- **Interface/bridge extension (Matrix):** adds matrix bot interface (not required to add tools).

### 14.6 Tool Provider API (base/common)
#### 14.6.1 API boundaries (clean entanglement)
- **base/core** exports only pure Java:
  - agent loop, session models, tool schema/types, proposal lifecycle.
  - no MC/AE2/Architectury classes.
- **base/common-* (mineagent)** owns:
  - UI screen/menu, networking, session sync, keybind/command open.
  - tool registry and execution routing.
  - vanilla `mc.*` tool implementations.
- **ext-ae/core (mineagentae)**:
  - AE2-specific tool DTOs/schemas (pure Java).
  - no MC/AE2 classes.
- **ext-ae/common-* (mineagentae)**:
  - AE2 tool implementations, AE2 part/block, AE2 network adapters.
  - registers AE2 tool provider into base registry.
- **ext-matrix/core (mineagentmatrix)**:
  - matrix bridge configs/DTOs (pure Java).
- **ext-matrix/common-* (mineagentmatrix)**:
  - matrix bot integration, message bridge to agent loop.
  - no tools required (optional later).

#### 14.6.2 Tool Provider API (concrete)
- Add in `base/common-*`:
  - `ToolProvider` interface:
    - `List<AgentTool> specs()`
    - `ToolOutcome execute(Optional<TerminalContext> terminal, ToolCall call, boolean approved)`
  - `ToolRegistry`:
    - `register(String providerId, ToolProvider provider)`
    - `List<AgentTool> getToolSpecs()`
    - `ToolOutcome executeTool(Optional<TerminalContext> terminal, ToolCall call, boolean approved)`
- Replace direct `AgentToolRegistry` usage with `ToolRegistry`.
- Base registers a default `mc` provider on common init.
- AE2 registers an `ae2` provider on its common init.

### 14.7 Migration Phases (strict copy -> verify -> remove)
**Phase A: Gradle layout + module IDs**
1) Update `settings.gradle` to include nested modules:
   - `:base:core`, `:base:common-1.20.1`, `:base:forge-1.20.1`, `:base:fabric-1.20.1`
   - `:ext-ae:*`, `:ext-matrix:*`
2) Update root build logic to allow per-module `archivesBaseName` and `modid`.
3) Create the new folder tree (empty) under `/base`, `/ext-ae`, `/ext-matrix`.

**Phase B: Base mod rename**
4) Copy current **core/common/forge/fabric** into `/base/*`.
5) Update modid/group for base:
   - `modid = mineagent`
   - `group = space.controlnet.mineagent`
6) Update resources + config names:
   - `assets/mineagentae` -> `assets/mineagent`
   - `config/mineagentae` -> `config/mineagent`
   - lang keys, prompt IDs, packet/channel IDs, etc.
7) Verify: build base `:base:forge-1.20.1` and `:base:fabric-1.20.1`.
8) Remove old root modules only after verification.

**Phase C: Tool provider refactor (base/common)**
9) Add `ToolProvider` + `ToolRegistry` to base common.
10) Replace existing tool calls to use `ToolRegistry`.
11) Move all `mc.*` tools into base and register default provider.
12) Verify: base agent loop works with only `mc.*` tools.

**Phase D: AE2 extension (mineagentae)**
13) Copy AE2-related code into `/ext-ae/core` and `/ext-ae/common-*`.
14) Update modid/group for AE2 extension:
   - `modid = mineagentae`
    - `group = space.controlnet.mineagent.ae`
15) Wire dependencies:
    - ext-ae/common-* -> base/core + base/common-* + AE2 APIs.
16) Register AE2 tool provider in ext-ae/common init.
17) Keep AE2 terminal part/block in ext-ae only.
18) Verify: base + AE2 extension run together.

**Phase E: Matrix extension (mineagentmatrix)**
19) Create `/ext-matrix/core` and `/ext-matrix/common-*`.
20) Add matrix bridge logic (server/client as needed).
21) Update modid/group:
    - `modid = mineagentmatrix`
    - `group = space.controlnet.mineagent.matrix`
22) Verify: base + matrix extension run together.

### 14.8 Acceptance Criteria (refactor)
- `mineagent` runs standalone and provides UI + `mc.*` tools.
- `mineagentae` loads only when `mineagent` + `ae2` are present.
- `mineagentmatrix` loads only when `mineagent` is present.
- Tools are discovered only via `ToolRegistry` providers.
- No AE2 classes in `base/*`.
- No MC/AE2 classes in any `*/core`.

### 14.9 File Move Procedure (mandatory)
1) **Copy first** using command-line tools.
2) Verify builds/tests or compile for affected modules.
3) **Remove old paths** only after verification.

### 14.10 Evaluation Protocol (mandatory)
After each phase that changes code layout or dependencies:
1) **Compilation test** for all touched modules (Forge + Fabric).
2) **runClient test** (use DISPLAY=:1):
   - `:base:forge-1.20.1:runClient`
   - `:base:fabric-1.20.1:runClient`
   - `:ext-ae:forge-1.20.1:runClient` (with AE2 present)
   - `:ext-ae:fabric-1.20.1:runClient` (with AE2 present)
   - `:ext-matrix:forge-1.20.1:runClient` (if bridge uses client runtime)
   - `:ext-matrix:fabric-1.20.1:runClient` (if bridge uses client runtime)
3) Record any missing native libs or runtime warnings (e.g., Narrator/flite) in the progress log.

---

## 15) Refactor Progress Log (as of 2026-01-20)

### 15.1 Completed (structural)
- **New module layout created:** `/base/*`, `/ext-ae/*`, `/ext-matrix/*` with nested `core/common-1.20.1/forge-1.20.1/fabric-1.20.1`.
- **Gradle wiring updated:** `settings.gradle` includes only new modules; root `gradle.properties` set to `mineagent`; per-extension `gradle.properties` set to `mineagentae` / `mineagentmatrix`.
- **Jar naming:** per-module `archives_base_name` and `maven_group` applied (root build.gradle enforces ext-ae/ext-matrix overrides to avoid capability collisions).
- **Base rename:** packages moved to `space.controlnet.mineagent`, modid `mineagent`, resources under `assets/mineagent`.
- **AE2 extension:** packages under `space.controlnet.mineagent.ae`, modid `mineagentae`, resources under `assets/mineagentae`.
- **Matrix extension scaffolded:** packages under `space.controlnet.mineagent.matrix`, modid `mineagentmatrix`, minimal bootstrap + metadata.
- **Legacy archive deleted:** `/old-root-modules` removed after successful validation.

### 15.2 Completed (code moves & refactors)
- **Tool provider split (base):** added `ToolProvider` + `ToolRegistry`; base registers `mc` provider.
- **Terminal context split (base):** `TerminalHost`, `TerminalContextResolver`, `TerminalContextRegistry`; base menu allows hostless open.
- **Commands (base):** `/mineagent open` (hostless) + `/mineagent reload`.
- **AE2 tool provider:** `AeToolProvider` registered from `MineAgentAe.init()`.
- **AE2 terminal part:** part implementation, models, and item now live in `ext-ae` only.
- **Resource moves:** AI terminal textures/models/blockstates/lang moved to `ext-ae` namespace.
- **Extension runtime dependencies fixed:** ext-ae and ext-matrix loader modules now correctly include base mod classes on dev runtime classpath via `common()` configuration.

### 15.3 Validation status
- **Compilation (2026-01-20):** success for base + ext-ae + ext-matrix:
  - `:base:*` (core/common/forge/fabric) �?  - `:ext-ae:*` (core/common/forge/fabric) �?  - `:ext-matrix:*` (core/common/forge/fabric) �?- **Runtime tests (2026-01-20):** all passed:
  - `:base:forge-1.20.1:runClient` �?(`MineAgent initialized`)
  - `:base:fabric-1.20.1:runClient` �?(`MineAgent initialized`)
  - `:ext-ae:forge-1.20.1:runClient` �?(`MineAgent initialized`, `MineAgentAe initialized`)
  - `:ext-ae:fabric-1.20.1:runClient` �?(`MineAgent initialized`, `MineAgentAe initialized`)
  - `:ext-matrix:forge-1.20.1:runClient` �?(`MineAgent initialized`, `MineAgentMatrix initialized`)
  - `:ext-matrix:fabric-1.20.1:runClient` �?(`MineAgent initialized`, `MineAgentMatrix initialized`)
- **Warnings:** Forge `FMLJavaModLoadingContext.get()` deprecation warnings; Loom version outdated warning; Narrator `libflite.so` missing (non-blocking).

### 15.4 Refactor Complete
The base + extensions refactor is **complete**. All acceptance criteria from §14.8 are met:
- �?`mineagent` runs standalone and provides UI + `mc.*` tools
- �?`mineagentae` loads only when `mineagent` + `ae2` are present
- �?`mineagentmatrix` loads only when `mineagent` is present
- �?Tools are discovered via `ToolRegistry` providers
- �?No AE2 classes in `base/*`
- �?No MC/AE2 classes in any `*/core`

### 15.5 Optional Future Cleanup
- Move AE2-specific output formatting out of base UI/formatter if strict base-only behavior is required.

### 15.6 UI Code Refactoring (2026-01-20)
- **`AiTerminalConstants.java` extracted:** All dimension and color constants (~45 constants) moved from `AiTerminalScreen` to a dedicated constants file with static imports.
- **`components/` package created:** Extracted 10 inner records/classes from `AiTerminalScreen`:
  - `ChatLine`, `ChatSpan` �?Chat message rendering records
  - `InputSpan`, `ItemToken`, `ItemSuggestion` �?Input tokenization records
  - `TokenRange`, `TokenMetrics` �?Token position/sizing records
  - `SessionRow`, `SessionRowHit` �?Session panel records
  - `FlatButton`, `UiButtonStyle` �?Reusable flat-styled button component and style enum
- **`AiTerminalScreen.java` reduced:** From ~2483 lines to ~2322 lines (~161 lines removed).
- **Further extraction deferred:** `TokenInputHandler`, `ChatMessageFormatter`, `SessionPanelController` extraction was evaluated but cancelled �?these methods depend heavily on screen instance state and would require complex context passing without proportional benefit.
- **`rootProject.name` fixed:** `settings.gradle` updated from `"MineAgentAe"` to `"MineAgent"` to match the renamed base mod.

### 15.7 Commit Sync Update (added on 2026-02-21)

The following three commits were not yet reflected in this document and are now recorded explicitly:

1) **`22a7506` (2026-01-20)**
   - Added/refined the multi-module MineAgent structure (`base`, `ext-ae`, `ext-matrix`) and completed large-scale package/resource migration from legacy naming.
   - Introduced/expanded base tool-provider architecture (`ToolProvider`, `ToolRegistry`, `McToolProvider`) and AE extension tool integration.
   - Moved and cleaned significant UI, rendering, networking, session, and build wiring across modules; removed deprecated legacy paths.

2) **`5116474` (2026-02-21)**
   - Added keyboard shortcut support via `MineAgentKeybinds` and updated client registration.
   - Fixed module naming consistency in build/scripts/settings metadata.
   - Updated language resources and repository helper scripts accordingly.

3) **`442b79a` (2026-02-21)**
   - Implemented P0 stability hardening for:
     - session state/proposal transition reliability,
     - non-sticky `INDEXING` recovery,
     - server-thread confinement for tool execution,
     - unified `65536` tool-args boundary handling (parse/network/persistence).
   - Added comprehensive regression coverage across `base/core`, `base/common-1.20.1`, and `ext-ae/common-1.20.1` for state machine, indexing, thread confinement, and boundary contracts.
- Updated `.gitignore` for repository-local workflow artifacts (`.sisyphus/`, IDE metadata paths).

### 15.8 Commit Sync Update (added on 2026-03-29)

The following recent commits were not yet reflected in this document and are now recorded explicitly:

1) **`86e74f5`, `a3e7aed`, `0e1d2ae`, `2cc06d5`, `ba8b020`, `bef506a`, `d6eb1e3` (2026-03-29)**
   - Added MCP runtime support in base/common with provider-scoped tool registration/replacement.
   - Added MCP schema mapping, execution-affinity handling, stdio transport, and public streamable HTTP transport.
   - Wired MCP runtime load/reload into server startup and `/mineagent reload`.
   - Added provider invocation normalization, fallback render behavior, and readonly/runtime regression coverage.

2) **`c71ed6f` (2026-03-29)**
   - Hardened runtime test isolation with a shared session/runtime lock for Fabric GameTest coverage.
   - Strengthened runtime regression coverage around shared session state and loader-safe GameTest execution ordering.

3) **`82373eb` (2026-03-29)**
   - Added shared whole-agent reliability GameTest scenarios in `base/common-1.20.1` with thin Fabric/Forge adapters.
   - Covered direct response, tool-loop, invalid model output, and model-exception failure paths through the real chat-packet-to-agent-loop request path.
   - Clarified/fixed query throttling semantics so `LlmRateLimiter` applies to top-level player asks, not every internal iteration.
   - Refactored `AgentLoop` graph state to keep serialized state deserialization-safe under Forge while moving live runtime objects to per-session in-memory maps.

4) **`e6eb9f9`, `b84aaf2`, `e34efc5` (2026-03-29)**
   - These commits were repository-workflow/documentation/gitignore maintenance only and did not change product runtime behavior.

---

## 16) Test Strategy Notes (synced on 2026-03-31)

### 16.1 Layered pyramid, current implemented state
1. **Unit JUnit (core-first):**
   - Active in `base/core` and extension core logic.
   - Focus: deterministic logic, parser/policy/state-machine rules.
2. **Integration JUnit (common modules):**
   - Active in `base/common-1.20.1` and `ext-ae/common-1.20.1`.
   - Focus: network/serialization boundaries, lifecycle/state transitions, regression contracts.
3. **GameTest (runtime invariants):**
   - Harness is active on both loaders.
   - Shared whole-agent reliability scenarios now live in `base/common-1.20.1` and run through thin Fabric/Forge adapters.
   - Shared cross-loader GameTest scenarios in `base/common-1.20.1` now cover the reload-command smoke path, delete-last-active-session fallback creation, real-host menu validity, session-visibility/session-list update no-op handling, base task6 proposal-binding-unavailable, task7 indexing-gate recovery, task8 viewer churn consistency, task9 server-thread confinement plus timeout/failure contracts, and task10 tool-args boundary, while loader modules stay thin wrappers or entrypoint adapters.
   - Shared cross-loader GameTest scenarios in `ext-ae/common-1.20.1` now cover bound-terminal approval success handoff, craft lifecycle isolation, terminal teardown live-job cleanup, binding invalidation after removal / wrong-side lookup miss, and the CPU-targeted unavailable-CPU branch, while the deterministic no-binding approval failure remains covered by common-module JUnit.
   - Fabric and Forge base runtime paths are both green in this workspace, including the shared agent reliability scenario.

### 16.2 Canonical execution commands
**JUnit matrix commands (PR lane style):**
```bash
./gradlew --no-daemon --configure-on-demand :base:core:test
./gradlew --no-daemon --configure-on-demand :base:common-1.20.1:test
./gradlew --no-daemon --configure-on-demand :ext-ae:core:test
./gradlew --no-daemon --configure-on-demand :ext-ae:common-1.20.1:test
```

**Aggregate JUnit coverage command:**
```bash
./gradlew --no-daemon --configure-on-demand jacocoUnitTestReport
```

**Fabric GameTest commands (nightly/parity path):**
```bash
timeout 25m ./gradlew --no-daemon --configure-on-demand :base:fabric-1.20.1:runGametest --stacktrace
timeout 25m ./gradlew --no-daemon --configure-on-demand :ext-ae:fabric-1.20.1:runGametest --stacktrace -Dfabric-api.gametest.filter=ae_smoke
timeout 25m ./gradlew --no-daemon --configure-on-demand :base:fabric-1.20.1:runGametest --stacktrace -Dfabric-api.gametest.filter=baseAgentSystemReliability
```

**Forge GameTest commands (dev-lane path):**
```bash
timeout 25m ./gradlew --no-daemon --configure-on-demand :base:forge-1.20.1:runGameTestServer --stacktrace
timeout 25m ./gradlew --no-daemon --configure-on-demand :ext-ae:forge-1.20.1:runGameTestServer --stacktrace
```

**Shared UI preview capture commands (common fixtures, both loaders):**
```bash
python3 scripts/capture_ui_preview.py --loader forge --scenario all --display :1
python3 scripts/capture_ui_preview.py --loader fabric --scenario proposal_pending --display :1
python3 scripts/capture_status_ui.py --loader fabric --scenario all --display :1
python3 scripts/capture_status_ui.py --loader forge --scenario all --display :1
```

- The AI terminal screenshot flow is shared in `base/common-1.20.1` and activates only when `MINEAGENT_UI_CAPTURE_SCENARIO` is present.
- Forge and Fabric both use the same common preview scenarios, snapshot assertions, and screenshot capture path; the loader choice only affects which `runServer` / `runClient` tasks launch the runtime.
- **Current workspace verification (2026-04-03):** `DISPLAY=:1 python3 scripts/capture_ui_preview.py --loader fabric --scenario all --display :1` and `DISPLAY=:1 python3 scripts/capture_ui_preview.py --loader forge --scenario all --display :1` both completed successfully and produced the full shared scenario set: `empty`, `chat_short`, `suggestions_visible`, `proposal_pending`, `executing`, `error_state`, `http_result`, and `session_list_dense`.
- The AI terminal header now includes a shared top-right `Status` button that opens a separate status screen listing the currently loaded tools from a server-authoritative tool catalog synced to the client when the terminal opens (instead of relying on client-local preview/provider state). Built-in tools stay under `Built-in`, MCP tools stay under `MCP`, and extension tools are split into per-extension groups by mod id (for example `mineagentae`) instead of a single combined `Ext` section.
- The shared status capture flow now runs against the real ext-enabled runtime (`:ext-ae:<loader>-1.20.1:runServer` + `:ext-ae:<loader>-1.20.1:runClient`), writes a real MCP stdio fixture into `config/mineagent/mcp.json`, and waits for the synced built-in/ext/MCP tool catalog before opening the status panel for screenshots.
- **Current workspace verification (2026-04-04):** `DISPLAY=:1 python3 scripts/capture_status_ui.py --loader fabric --scenario all --display :1` and `DISPLAY=:1 python3 scripts/capture_status_ui.py --loader forge --scenario all --display :1` completed successfully and produced `status_button` plus `status_panel` screenshots showing the new header button and a grouped tool-status panel with readable `Built-in`, per-extension mod-id sections such as `mineagentae`, and `MCP`. The Forge rerun also fixed the ext-AE Forge dev runtime by syncing processed `mods.toml`/compiled artifacts into `ext-ae/forge-1.20.1/bin/main` for normal `runServer` and `runClient` tasks.
- Generated UI capture PNGs under `artifacts/ui-captures/` are local runtime outputs and are intentionally kept out of git.

### 16.3 Report artifacts, parity, and evidence locations
- JUnit XML: `**/build/test-results/test/*.xml`
- Aggregate JUnit JaCoCo HTML: `build/reports/jacoco/jacocoUnitTestReport/html/index.html`
- Aggregate JUnit JaCoCo XML: `build/reports/jacoco/jacocoUnitTestReport/jacocoUnitTestReport.xml`
- Per-module JUnit JaCoCo reports: `**/build/reports/jacoco/test/**`
- Fabric GameTest XML:
  - `base/fabric-1.20.1/build/reports/gametest/runGametest.xml`
  - `ext-ae/fabric-1.20.1/build/reports/gametest/runGametest.xml`
- Forge runtime log (dev lane): `ci-reports/dev/forge-gametest.log`
- CI summaries:
  - `ci-reports/pr/*-summary.json`
- `ci-reports/dev/summary.json`
  - `ci-reports/nightly/summary.json`
- Parity report: `ci-reports/parity/gametest-parity-report.md`
- Shared UI preview capture PNGs: `artifacts/ui-captures/<loader>/<scenario>.png`
- Evidence archive: `.sisyphus/evidence/*`

### 16.4 Cross-loader runtime status (updated 2026-04-02)
- **Shared agent reliability coverage:** base runtime now includes a shared cross-loader reliability scenario that exercises the real chat packet → session transition → agent loop → tool execution / failure path.
 - **Shared scenario coverage:** common code now owns the base command-menu lifecycle cleanup, reload-command smoke path, deleted-session queued-append lifecycle, delete-last-active-session fallback, real-host menu validity, session-visibility/delete/rebind lifecycle, session-list update / TEAM no-op handling, proposal-binding-unavailable, indexing-gate recovery, viewer churn consistency, server-thread confinement, timeout/failure contract, tool-args boundary, and agent-reliability scenarios, plus the ext-AE craft-lifecycle isolation, bound-terminal approval success handoff, terminal teardown live-job cleanup, binding invalidation, and unavailable-CPU runtime branches. The deterministic bound-terminal no-binding failure path is covered by `NetworkProposalLifecycleBehaviorTest.task7_approvalDecisionBehavior_approveWithoutBindingFailsDeterministically()` instead of the loader runtime lane.
- **Loader adapters:** Fabric runtime methods and Forge GameTest classes are thin wrappers over the shared common scenarios, with loader-specific work limited to player creation and entrypoint wiring.
- **Current workspace status:** both base Fabric and base Forge GameTest runs pass in this workspace, and the previous Forge runtime blocker no longer applies to the current branch state.
- **Ext-AE Fabric isolation:** the ext-AE Fabric nightly/parity lane now keeps the base mod loaded for runtime dependencies but strips the base `fabric-gametest` entrypoint from the ext-AE local runtime artifact, so `:ext-ae:fabric-1.20.1:runGametest -Dfabric-api.gametest.filter=ae_smoke` discovers only the five AE smoke GameTests while the base Fabric lane remains the source of base runtime coverage.
- **Current workspace verification:** `:base:fabric-1.20.1:runGametest`, `:ext-ae:fabric-1.20.1:runGametest -Dfabric-api.gametest.filter=ae_smoke`, `:base:forge-1.20.1:runGameTestServer`, and `:ext-ae:forge-1.20.1:runGameTestServer` all pass in this workspace.
- **Parity evidence note:** the checked-in parity report now treats Fabric wrapper registration plus successful lane runs as the authoritative testcase inventory. Fabric's `runGametest.xml` remains a collected artifact, but it does not reliably enumerate every passing registered testcase in this workspace, so the XML is used as supplemental per-test status rather than the sole inventory source.

### 16.5 CI lane mapping
- **PR lane:** JUnit matrix only (`:base:core:test`, `:base:common-1.20.1:test`, `:ext-ae:core:test`, `:ext-ae:common-1.20.1:test`) with per-module JaCoCo artifacts.
- **Dev lane:** single `jacocoUnitTestReport` invocation (which runs the covered JUnit suite once and emits aggregate/per-module coverage) + Forge `:base:forge-1.20.1:runGameTestServer` with blocker-aware policy parsing.
- **Nightly lane:** single `jacocoUnitTestReport` invocation (which runs the covered JUnit suite once and emits aggregate/per-module coverage) + Fabric `:base:fabric-1.20.1:runGametest` and `:ext-ae:fabric-1.20.1:runGametest -Dfabric-api.gametest.filter=ae_smoke`.
- **Policy parser naming note:** the workflow-visible lane is `dev`, but the policy parser still uses the internal lane key `main` when collecting/enforcing the dev-lane summary.

### 16.6 JUnit coverage signal (updated 2026-03-31)
- **Coverage scope:** percentage-based coverage is now tracked for the stable JUnit layer only, across `base/core`, `base/common-1.20.1`, `ext-ae/core`, and `ext-ae/common-1.20.1`.
- **Aggregation model:** each covered module still emits its own JaCoCo report on `test`, and the root `jacocoUnitTestReport` task produces one aggregate HTML/XML report for CI artifacts and local inspection.
- **Runtime policy:** Fabric/Forge GameTest coverage remains scenario-based and is intentionally not folded into the main JaCoCo percentage signal.

### 16.7 Released MineAgent state (updated 2026-03-31)
- **Naming and module split:** the repository now treats MineAgent / MineAgent AE / MineAgent Matrix as the canonical shipped identities across source, resources, build metadata, and release automation.
- **Published release flow:** GitHub releases are published from `master` by `.github/workflows/release.yml`, using `gradle.properties` as the source of truth for `mod_version` and `minecraft_version`.
- **Current release metadata:** the repository is currently configured for `mod_version=0.0.1` on Minecraft `1.20.1`.
- **Verification before release:** the post-rename verification matrix passed for JUnit, base Forge GameTests, ext-AE Forge GameTests, base Fabric GameTests, ext-AE Fabric GameTests, and `./scripts/build-dist.sh`.

### 16.8 Built-in `http` tool contract (updated 2026-04-03)
- `http` is a first-party built-in tool from `base/common-1.20.1`.
- Execution affinity is `CALLING_THREAD`, so outbound network I/O stays off the Minecraft server thread.
- Each invocation is stateless and performs exactly one HTTP exchange with optional bounded redirect following.

**Prompt-facing request schema:**
```text
{url, method?, query?: [{name, value}], headers?: [{name, value}], bodyText?, bodyBase64?, timeoutMs?, followRedirects?, maxRedirects?, responseMode?}
```

**Result envelope:**
```text
{kind, request: {url, method, query: [{name, value}], headers: [{name, value}], bodyText?, bodyBase64?, timeoutMs, followRedirects, maxRedirects, responseMode}, response?: {statusCode, finalUrl, redirectCount, headers: [{name, value}], contentType?, charset?, declaredContentLength?, bodyText?, bodyBase64?, bodyBytes}, failure?: {code, message}, truncated}
```

**Frozen request/response behavior:**
- `method` is normalized to uppercase and defaults to `GET`.
- `url` must be an absolute `http://` or `https://` URL.
- `query` and `headers` are ordered `{name, value}` arrays that preserve duplicates.
- `bodyText` and `bodyBase64` are mutually exclusive.
- `GET` and `HEAD` requests with a body fail locally with `invalid_args`.
- `responseMode` accepts only `auto | text | json | bytes`.
- `response.headers` names are normalized to lowercase for deterministic rendering.
- Completed `1xx` / `2xx` / `3xx` / `4xx` / `5xx` exchanges populate `response`; local validation, timeout, transport, and runtime failures populate `failure`.
- The success envelope records only `finalUrl` and `redirectCount`; v1 does not return full redirect history.

**Frozen defaults and limits:**
- `timeoutMs`: default `10000`, allowed range `1000..25000`
- `followRedirects`: default `false`
- `maxRedirects`: default `5`, allowed range `0..10`
- `responseMode`: default `auto`
- Request body size after UTF-8 or base64 decoding must be `<= 32768` bytes.
- Response body buffering is capped at `262144` bytes; overflow returns `failure.code = response_too_large` and sets top-level `truncated = true`.
- `auto` text decoding treats `text/*`, `application/json`, `application/*+json`, `application/xml`, `text/xml`, `application/javascript`, and `application/x-www-form-urlencoded` as text candidates.
- If a text response omits `charset`, decoding falls back to UTF-8, but `response.charset` stays absent unless the server declared one.
- V1 is stateless across invocations: response `Set-Cookie` values are not persisted into later `Cookie` request headers.

**Stable local failure codes currently exercised by regression coverage:**
- `invalid_args`
- `tool_timeout`
- `too_many_redirects`
- `response_too_large`
- `unsupported_response_body`
- `tool_execution_failed`

**Deferred non-goals in this iteration:**
- SSRF / allowlist / approval-flow hardening, secret-redaction frameworks, and broader request-safety policy work
- cookie jar persistence across tool invocations
- streaming, SSE, or WebSocket support
- multipart/form-data helpers, retry frameworks, proxy support, TLS customization, or download-to-file behavior
- dedicated response-compression helpers beyond default Java `HttpClient` behavior

**Focused verification commands for the HTTP tool contract (run sequentially):**
```bash
./gradlew --no-daemon --configure-on-demand :base:common-1.20.1:test --tests 'space.controlnet.mineagent.common.tools.http.HttpToolContractRegressionTest'
./gradlew --no-daemon --configure-on-demand :base:common-1.20.1:test --tests 'space.controlnet.mineagent.common.tools.http.HttpToolRegistrationRegressionTest'
./gradlew --no-daemon --configure-on-demand :base:common-1.20.1:test --tests 'space.controlnet.mineagent.common.tools.http.HttpToolThreadAffinityRegressionTest'
./gradlew --no-daemon --configure-on-demand :base:common-1.20.1:test --tests 'space.controlnet.mineagent.common.tools.http.HttpToolFixtureRegressionTest'
./gradlew --no-daemon --configure-on-demand :base:common-1.20.1:test --tests 'space.controlnet.mineagent.common.tools.http.HttpToolProviderValidationRegressionTest'
./gradlew --no-daemon --configure-on-demand :base:common-1.20.1:test --tests 'space.controlnet.mineagent.common.tools.http.HttpToolExecutionRegressionTest'
./gradlew --no-daemon --configure-on-demand :base:common-1.20.1:test --tests 'space.controlnet.mineagent.common.tools.http.HttpToolRenderingRegressionTest'
./gradlew --no-daemon --configure-on-demand :base:common-1.20.1:test --tests 'space.controlnet.mineagent.common.tools.http.HttpToolDocumentationContractRegressionTest'
```
