# Readonly MCP Tool Integration for ChatMC

## TL;DR
> **Summary**: Add readonly-only MCP support as a new provider-backed tool subsystem that fits ChatMC’s existing `ToolRegistry` / `ToolProvider` architecture, while supporting both local stdio MCP servers and public remote streamable-HTTP MCP servers in v1 through a pure shared `mcpServers` JSON file.
> **Deliverables**:
> - MCP config model + loader/validator for multiple namespaced servers
> - Dedicated MCP runtime + transport layer with stdio and streamable HTTP
> - `McpToolProvider` discovery/execution bridge that trusts user-configured readonly servers
> - Optional-first tool metadata rendering with deterministic fallback UI formatting
> - TDD regression suite for config, registry refresh, lifecycle, invocation, timeout, and rendering
> **Effort**: Large
> **Parallel**: YES - 3 waves
> **Critical Path**: 1 → 3 → 5/6 → 7 → 8 → 9

## Context
### Original Request
User wants a concrete implementation plan for MCP support to address tool scarcity. Confirmed constraints:
- v1 supports **readonly MCP only**
- v1 supports **both local MCP and remote MCP**
- v1 supports **multiple MCP servers with namespaced tool names**
- config file must use a **pure shared MCP JSON format** with no ChatMC-only sidecar block
- MCP `inputSchema` is authoritative
- `examples` are optional; do **not** synthesize examples
- metadata should be optional-first where practical
- rendering must be deterministic/template-based, not LLM-generated
- test strategy is **TDD**

### Interview Summary
- Current ChatMC tool rendering is effectively **summary-from-input, lines-from-output**.
- The user agrees that optional metadata is preferable to forcing complete MCP metadata.
- The user explicitly rejected LLM-generated helper metadata and agrees examples should remain optional.
- The namespacing model should be `Multiple namespaced`, e.g. `mcp.docs.search` and `mcp.wiki.fetch_page`.

### Metis Review (gaps addressed)
- Added a strict compatibility rule: `config/chatmc/mcp.json` contains only the shared `mcpServers` object shape; no ChatMC-specific keys are allowed in the file.
- Fixed lifecycle ambiguity: v1 refresh is **startup + `/chatmc reload` + shutdown cleanup only**. No `tools/list_changed`, no SSE fallback, no interactive OAuth.
- Added registry replacement as a prerequisite because current `ToolRegistry` only mutates by tool name and never unregisters stale tools.
- Added prompt-bloat guardrails: metadata sections with no content must be omitted, and MCP exposure is bounded only by the configured shared-file server set.
- Added server-thread safety guardrails: remote/stdio transport I/O must never block the Minecraft server thread.

## Work Objectives
### Core Objective
Implement a readonly MCP adapter for ChatMC that makes MCP tools appear as normal ChatMC tools without introducing a second tool system, while preserving server responsiveness, deterministic prompt behavior, and a pure shared-file configuration contract.

### Deliverables
- `config/chatmc/mcp.json` loader/parser/validator and defaults writer, using only a Claude/Codex-style top-level `mcpServers` object
- MCP server configuration model under `mcpServers.<alias>` with only shared fields:
  - stdio fields: `type: "stdio"`, `command`, optional `args`, optional `env`, optional `cwd`
  - remote fields: `type: "http"`, `url`
- strict accepted JSON Schema for ChatMC v1's shared subset
- `ToolRegistry` provider replacement/unregister support with deterministic snapshots
- `ExecutionAffinity` routing so MCP I/O runs off the MC server thread
- `McpToolProvider` and MCP runtime manager
- stdio and streamable-HTTP transports implemented with Java stdlib + Gson, not a second AI framework
- optional-first tool metadata rendering
- regression/integration tests in `base/core` and `base/common-1.20.1`

### Definition of Done (verifiable conditions with commands)
- `./gradlew --no-daemon --configure-on-demand :base:core:test --tests 'space.controlnet.chatmc.core.tools.mcp.McpConfigParserValidationRegressionTest'` passes
- `./gradlew --no-daemon --configure-on-demand :base:core:test --tests 'space.controlnet.chatmc.core.agent.McpPromptMetadataRenderingRegressionTest'` passes
- `./gradlew --no-daemon --configure-on-demand :base:common-1.20.1:test --tests 'space.controlnet.chatmc.common.tools.mcp.ToolRegistryRefreshDeterminismRegressionTest'` passes
- `./gradlew --no-daemon --configure-on-demand :base:common-1.20.1:test --tests 'space.controlnet.chatmc.common.tools.mcp.McpSchemaMappingRegistrationRegressionTest'` passes
- `./gradlew --no-daemon --configure-on-demand :base:common-1.20.1:test --tests 'space.controlnet.chatmc.common.tools.mcp.McpStdioClientLifecycleRegressionTest'` passes
- `./gradlew --no-daemon --configure-on-demand :base:common-1.20.1:test --tests 'space.controlnet.chatmc.common.tools.mcp.McpStreamableHttpClientLifecycleRegressionTest'` passes
- `./gradlew --no-daemon --configure-on-demand :base:common-1.20.1:test --tests 'space.controlnet.chatmc.common.tools.mcp.McpRuntimeManagerReloadIsolationRegressionTest'` passes
- `./gradlew --no-daemon --configure-on-demand :base:common-1.20.1:test --tests 'space.controlnet.chatmc.common.tools.mcp.McpToolProviderInvocationRenderRegressionTest'` passes
- `./gradlew --no-daemon --configure-on-demand :base:core:test :base:common-1.20.1:test` passes

### Must Have
- MCP implemented as ChatMC `ToolProvider` adapters, not a separate agent stack
- Qualified tool names exactly `mcp.<alias>.<remoteToolName>`
- Alias regex exactly `^[a-z0-9][a-z0-9-]{0,31}$`
- Duplicate aliases fail config validation
- Every configured alias is treated as enabled
- MCP tool registration trusts the user-configured server set and does not gate on `readOnlyHint`
- Remote transport limited to **public streamable HTTP** in v1
- Local transport limited to **stdio subprocess** in v1
- MCP discovery/refresh lifecycle limited to:
  - server startup
  - `/chatmc reload`
  - server shutdown cleanup
- MCP metadata fields may be empty/null, and prompt rendering must omit empty sections
- No synthetic examples
- No server-thread blocking for MCP network/process waits
- One failing MCP server must not unregister or poison healthy MCP servers

### Must NOT Have (guardrails, AI slop patterns, scope boundaries)
- No write-capable MCP tools
- No approval/proposal integration for MCP in this work
- No OAuth, bearer auth, or interactive browser/device auth
- No SSE fallback, WebSocket transport, MCP resources/prompts/sampling/roots
- No ChatMC-only keys inside `mcpServers.<alias>`
- No hidden last-write-wins collisions across server aliases
- No LLM-generated metadata, render text, or examples
- No unconditional “register every discovered tool” behavior

## Verification Strategy
> ZERO HUMAN INTERVENTION — all verification is agent-executed.
- Test decision: **TDD** with focused JUnit first in `base/core` and `base/common-1.20.1`
- QA policy: Every task below includes executable happy-path and failure-path scenarios
- Evidence: `.sisyphus/evidence/task-{N}-{slug}.{ext}`

## Execution Strategy
### Parallel Execution Waves
> Target: 5-8 tasks per wave. <3 per wave (except final) = under-splitting.
> Extract shared dependencies as Wave-1 tasks for max parallelism.

Wave 1: config contract, prompt contract, registry/runtime foundations (Tasks 1-4)

Wave 2: stdio transport, streamable HTTP transport, lifecycle/reload manager (Tasks 5-7)

Wave 3: provider bridge, rendering envelope, end-to-end regression suite (Tasks 8-9)

### Dependency Matrix (full, all tasks)
- 1 blocks 5, 6, 7, 8, 9
- 2 blocks 8, 9
- 3 blocks 7, 8, 9
- 4 blocks 7, 8, 9
- 5 blocks 7, 8, 9
- 6 blocks 7, 8, 9
- 7 blocks 8, 9
- 8 blocks 9
- 9 feeds Final Verification Wave

### Agent Dispatch Summary (wave → task count → categories)
- Wave 1 → 4 tasks → `unspecified-high`, `deep`
- Wave 2 → 3 tasks → `deep`, `unspecified-high`
- Wave 3 → 2 tasks → `unspecified-high`, `deep`
- Final Verification Wave → 4 tasks → `oracle`, `unspecified-high`, `deep`

## TODOs
> Implementation + Test = ONE task. Never separate.
> EVERY task MUST have: Agent Profile + Parallelization + QA Scenarios.

- [x] 1. Add MCP config model and validation

  **What to do**: Add a new pure-Java config surface under `base/core/src/main/java/space/controlnet/chatmc/core/tools/mcp/` with exact types `McpConfig`, `McpServerConfig`, `McpTransportKind`, `McpConfigParser`, and `McpConfigValidator`. The on-disk format must be `config/chatmc/mcp.json` with an exact top-level shape `{"mcpServers": {"<alias>": {...}}}` and no extra ChatMC policy block, so it stays a pure shared MCP JSON file. Keep `mcpServers.<alias>` aligned to the Claude/Codex common subset: stdio entries use `type: "stdio"`, `command`, optional `args`, optional `env`, optional `cwd`; remote entries use `type: "http"` and `url`. Define the accepted subset strictly: reject unknown top-level keys, reject unknown server-object keys, reject remote auth fields such as `oauth`, `bearerTokenEnv`, `headers`, and reject Codex-only extras such as `env_vars`. Add `base/common-1.20.1/src/main/java/space/controlnet/chatmc/common/tools/mcp/McpConfigLoader.java` that mirrors the current `LlmConfigLoader` pattern and writes JSON defaults on first boot.
  **Must NOT do**: Do not place Minecraft-specific types in `base/core`. Do not add OAuth fields, SSE config, inline ChatMC-specific control keys, or any non-shared server-object fields to the accepted config contract.

  **Recommended Agent Profile**:
  - Category: `unspecified-high` — Reason: Cross-module config model + parser + validator work with moderate complexity.
  - Skills: `[]` — Reason: Existing repo patterns are sufficient.
  - Omitted: `frontend-design` — Reason: No UI work.

  **Parallelization**: Can Parallel: YES | Wave 1 | Blocks: 5, 6, 7, 8, 9 | Blocked By: none

  **References** (executor has NO interview context — be exhaustive):
  - Pattern: `base/core/src/main/java/space/controlnet/chatmc/core/agent/LlmConfig.java:6-44` — existing pure-Java config record + defaults pattern
  - Pattern: `base/core/src/main/java/space/controlnet/chatmc/core/agent/LlmConfigParser.java:16-177` — config parser/validator separation pattern to mirror, while switching storage format to JSON
  - Pattern: `base/core/src/main/java/space/controlnet/chatmc/core/agent/LlmConfigValidator.java:10-31` — validation error aggregation pattern
  - Pattern: `base/common-1.20.1/src/main/java/space/controlnet/chatmc/common/llm/LlmConfigLoader.java:16-50` — config loader and default file creation pattern
  - External: `https://modelcontextprotocol.io/specification/2025-11-25/server/tools.md` — MCP tool metadata and `readOnlyHint`

  **Acceptance Criteria** (agent-executable only):
  - [ ] `./gradlew --no-daemon --configure-on-demand :base:core:test --tests 'space.controlnet.chatmc.core.tools.mcp.McpConfigParserValidationRegressionTest'` passes with scenarios for valid stdio config, valid HTTP config, duplicate aliases, invalid alias, unknown ChatMC-only keys, and rejection of non-shared fields such as `oauth` / `env_vars`.
  - [ ] `./gradlew --no-daemon --configure-on-demand :base:common-1.20.1:test --tests 'space.controlnet.chatmc.common.tools.mcp.McpConfigLoaderRegressionTest'` passes and proves `config/chatmc/mcp.json` defaults are written when absent.

  **QA Scenarios** (MANDATORY — task incomplete without these):
  ```
  Scenario: Valid local and remote config round-trip
    Tool: Bash
    Steps: Run ./gradlew --no-daemon --configure-on-demand :base:core:test --tests 'space.controlnet.chatmc.core.tools.mcp.McpConfigParserValidationRegressionTest.task17_roundTrip_validStdioAndHttpConfigs'
    Expected: Test passes and writes XML result for the class with zero failures.
    Evidence: .sisyphus/evidence/task-1-mcp-config.xml

  Scenario: Invalid alias and non-shared fields are rejected
    Tool: Bash
    Steps: Run ./gradlew --no-daemon --configure-on-demand :base:core:test --tests 'space.controlnet.chatmc.core.tools.mcp.McpConfigParserValidationRegressionTest.task17_invalidAliasAndNonSharedFields_areRejected'
    Expected: Test passes by asserting stable validation errors for both bad cases.
    Evidence: .sisyphus/evidence/task-1-mcp-config-error.xml
  ```

  **Commit**: YES | Message: `feat(mcp): add readonly server config model` | Files: `base/core/src/main/java/space/controlnet/chatmc/core/tools/mcp/*`, `base/common-1.20.1/src/main/java/space/controlnet/chatmc/common/tools/mcp/McpConfigLoader.java`, tests

- [x] 2. Make tool metadata optional-first and trim prompt bloat

  **What to do**: Keep the `AgentTool` contract source-compatible, but explicitly allow `resultSchema()` to return null/blank, `resultDescription()` to be empty, and `examples()` to be empty for MCP-backed tools. Update `AgentLoop.buildToolPrompt(...)` / `buildToolSection(...)` to omit empty `Arguments Details`, `Return Details`, and `Examples` sections instead of printing `(none)`. Extend `ToolResult` with an overload/factory that preserves payload on failure so MCP error responses can still carry structured content for UI and model self-correction.
  **Must NOT do**: Do not add LLM-generated examples or fallback prose. Do not break existing mc/ae tool renderers.

  **Recommended Agent Profile**:
  - Category: `unspecified-high` — Reason: Small code surface but cross-cutting prompt/contract behavior.
  - Skills: `[]` — Reason: Existing patterns are enough.
  - Omitted: `writing` — Reason: This is not documentation work.

  **Parallelization**: Can Parallel: YES | Wave 1 | Blocks: 8, 9 | Blocked By: none

  **References**:
  - Pattern: `base/core/src/main/java/space/controlnet/chatmc/core/agent/AgentLoop.java:245-345` — current prompt assembly and empty-section behavior
  - Pattern: `base/core/src/main/java/space/controlnet/chatmc/core/tools/AgentTool.java:8-23` — tool metadata contract
  - Pattern: `base/core/src/main/java/space/controlnet/chatmc/core/tools/ToolResult.java:3-10` — current success/error result model
  - Pattern: `base/common-1.20.1/src/main/java/space/controlnet/chatmc/common/tools/McToolProvider.java:165-233` — existing concrete `AgentTool` implementation

  **Acceptance Criteria**:
  - [ ] `./gradlew --no-daemon --configure-on-demand :base:core:test --tests 'space.controlnet.chatmc.core.agent.McpPromptMetadataRenderingRegressionTest'` passes and proves empty sections are omitted.
  - [ ] `./gradlew --no-daemon --configure-on-demand :base:core:test --tests 'space.controlnet.chatmc.core.tools.mcp.ToolResultPayloadOnErrorRegressionTest'` passes and proves failed MCP calls can preserve payload JSON.

  **QA Scenarios**:
  ```
  Scenario: Optional metadata does not emit '(none)' prompt sections
    Tool: Bash
    Steps: Run ./gradlew --no-daemon --configure-on-demand :base:core:test --tests 'space.controlnet.chatmc.core.agent.McpPromptMetadataRenderingRegressionTest.task17_emptyMetadataSections_areOmitted'
    Expected: Test passes by asserting the rendered prompt excludes empty details/returns/examples blocks.
    Evidence: .sisyphus/evidence/task-2-mcp-prompt.xml

  Scenario: MCP tool failure keeps structured payload
    Tool: Bash
    Steps: Run ./gradlew --no-daemon --configure-on-demand :base:core:test --tests 'space.controlnet.chatmc.core.tools.mcp.ToolResultPayloadOnErrorRegressionTest.task17_errorResult_preservesPayloadJson'
    Expected: Test passes by asserting `success=false`, `error!=null`, and `payloadJson` remains present.
    Evidence: .sisyphus/evidence/task-2-mcp-prompt-error.xml
  ```

  **Commit**: YES | Message: `refactor(agent): support optional mcp tool metadata` | Files: `base/core/src/main/java/space/controlnet/chatmc/core/agent/AgentLoop.java`, `base/core/src/main/java/space/controlnet/chatmc/core/tools/AgentTool.java`, `base/core/src/main/java/space/controlnet/chatmc/core/tools/ToolResult.java`, tests

- [x] 3. Refactor ToolRegistry for provider replacement and deterministic snapshots

  **What to do**: Replace the mutable append-only registration behavior with provider-scoped replace/unregister support and deterministic ordering. Add exact operations `registerOrReplace(providerId, provider)`, `unregister(providerId)`, and `unregisterByPrefix(prefix)`. Store provider-to-tool-name ownership so MCP reloads can remove stale tools. Return immutable snapshots sorted by tool name to keep prompt order stable across restarts and reloads.
  **Must NOT do**: Do not change existing static `mc` provider names. Do not allow silent stale-tool retention after reload.

  **Recommended Agent Profile**:
  - Category: `deep` — Reason: Shared registry semantics affect all tool providers and prompt ordering.
  - Skills: `[]` — Reason: Existing repo patterns are sufficient.
  - Omitted: `frontend-design` — Reason: No UI work.

  **Parallelization**: Can Parallel: YES | Wave 1 | Blocks: 7, 8, 9 | Blocked By: none

  **References**:
  - Pattern: `base/common-1.20.1/src/main/java/space/controlnet/chatmc/common/tools/ToolRegistry.java:20-76` — current flat registry and mutation model
  - Pattern: `base/common-1.20.1/src/main/java/space/controlnet/chatmc/common/ChatMC.java:29-49` — static provider registration at init/startup
  - Pattern: `base/core/src/main/java/space/controlnet/chatmc/core/agent/AgentLoop.java:250-266` — prompt ordering depends on `getToolSpecs()` order

  **Acceptance Criteria**:
  - [ ] `./gradlew --no-daemon --configure-on-demand :base:common-1.20.1:test --tests 'space.controlnet.chatmc.common.tools.mcp.ToolRegistryRefreshDeterminismRegressionTest'` passes and proves stale MCP tools are removed on replacement/unregister.
  - [ ] Registry tests prove tool ordering is stable and sorted after repeated reloads.

  **QA Scenarios**:
  ```
  Scenario: Provider replacement removes stale MCP tools
    Tool: Bash
    Steps: Run ./gradlew --no-daemon --configure-on-demand :base:common-1.20.1:test --tests 'space.controlnet.chatmc.common.tools.mcp.ToolRegistryRefreshDeterminismRegressionTest.task17_providerReplacement_removesStaleTools'
    Expected: Test passes by asserting old names disappear and only the new qualified tools remain.
    Evidence: .sisyphus/evidence/task-3-mcp-registry.xml

  Scenario: Deterministic order survives repeated reloads
    Tool: Bash
    Steps: Run ./gradlew --no-daemon --configure-on-demand :base:common-1.20.1:test --tests 'space.controlnet.chatmc.common.tools.mcp.ToolRegistryRefreshDeterminismRegressionTest.task17_sortedSnapshot_isStableAcrossReloads'
    Expected: Test passes by asserting a fixed sorted order over multiple register/unregister cycles.
    Evidence: .sisyphus/evidence/task-3-mcp-registry-error.xml
  ```

  **Commit**: YES | Message: `refactor(tools): support provider-scoped registry replacement` | Files: `base/common-1.20.1/src/main/java/space/controlnet/chatmc/common/tools/ToolRegistry.java`, tests

- [x] 4. Add execution affinity and MCP schema mapping

  **What to do**: Introduce an execution-affinity contract for tool providers so MCP work can run on a dedicated I/O executor while MC/AE tools stay on the Minecraft server thread. Add schema mapping helpers under `base/common-1.20.1/src/main/java/space/controlnet/chatmc/common/tools/mcp/` that convert MCP `inputSchema` into `argsSchema` / `argsDescription`, leave `resultSchema` and `examples` empty by default, and enforce qualified naming `mcp.<alias>.<remoteToolName>`. Register every discovered tool from every configured server alias and treat the shared-file config itself as the user's readonly trust boundary. If `readOnlyHint` is present, record it only for diagnostics/debug rendering; never use it as a registration gate.
  **Must NOT do**: Do not gate registration on `readOnlyHint`. Do not allow MCP tool execution on the MC server thread.

  **Recommended Agent Profile**:
  - Category: `deep` — Reason: This task changes shared execution routing and schema/registration semantics.
  - Skills: `[]` — Reason: Repo patterns are sufficient.
  - Omitted: `writing` — Reason: No docs-only work.

  **Parallelization**: Can Parallel: YES | Wave 1 | Blocks: 7, 8, 9 | Blocked By: 1

  **References**:
  - Pattern: `base/common-1.20.1/src/main/java/space/controlnet/chatmc/common/agent/AgentRunner.java:152-190` — current server-thread forcing and timeout behavior
  - Pattern: `base/common-1.20.1/src/main/java/space/controlnet/chatmc/common/tools/ToolProvider.java:14-17` — provider execution contract
  - Pattern: `base/common-1.20.1/src/main/java/space/controlnet/chatmc/common/tools/McToolProvider.java:87-103` — standard provider execution path
  - External: `https://modelcontextprotocol.io/specification/2025-11-25/server/tools.md` — tool metadata structure, including optional annotations such as `readOnlyHint`

  **Acceptance Criteria**:
  - [ ] `./gradlew --no-daemon --configure-on-demand :base:common-1.20.1:test --tests 'space.controlnet.chatmc.common.tools.mcp.McpSchemaMappingRegistrationRegressionTest'` passes and proves namespacing, schema mapping, and trust-user registration behavior.
  - [ ] `./gradlew --no-daemon --configure-on-demand :base:common-1.20.1:test --tests 'space.controlnet.chatmc.common.tools.mcp.ToolExecutionAffinityRegressionTest'` passes and proves MCP providers route to the I/O affinity path rather than server-thread blocking.

  **QA Scenarios**:
  ```
  Scenario: Configured MCP tool becomes mcp.<alias>.<name>
    Tool: Bash
    Steps: Run ./gradlew --no-daemon --configure-on-demand :base:common-1.20.1:test --tests 'space.controlnet.chatmc.common.tools.mcp.McpSchemaMappingRegistrationRegressionTest.task17_configuredTool_isNamespacedAndMapped'
    Expected: Test passes by asserting exact name `mcp.docs.search`, required args mapping, and empty examples.
    Evidence: .sisyphus/evidence/task-4-mcp-mapping.xml

  Scenario: Missing readOnlyHint does not block configured server tools
    Tool: Bash
    Steps: Run ./gradlew --no-daemon --configure-on-demand :base:common-1.20.1:test --tests 'space.controlnet.chatmc.common.tools.mcp.McpSchemaMappingRegistrationRegressionTest.task17_missingReadonlyHint_doesNotBlockRegistration'
    Expected: Test passes by asserting the tool remains registered and any missing hint is treated as informational only.
    Evidence: .sisyphus/evidence/task-4-mcp-mapping-error.xml
  ```

  **Commit**: YES | Message: `feat(mcp): add schema mapping and execution affinity` | Files: `base/common-1.20.1/src/main/java/space/controlnet/chatmc/common/tools/mcp/*`, `base/common-1.20.1/src/main/java/space/controlnet/chatmc/common/agent/AgentRunner.java`, tests

- [x] 5. Implement stdio MCP transport and client session

  **What to do**: Implement a minimal stdio MCP client under `base/common-1.20.1/src/main/java/space/controlnet/chatmc/common/tools/mcp/transport/` using `ProcessBuilder`, request/response correlation, `initialize`, `notifications/initialized`, `tools/list`, and `tools/call`. Add startup/close semantics, bounded response size, timeout handling, JSON parse failure handling, and process shutdown. Use a dedicated executor for process I/O and ensure the provider-facing API is synchronous only at the boundary above that executor.
  **Must NOT do**: Do not run stdio reads/writes on the Minecraft server thread. Do not implement MCP prompts/resources/sampling.

  **Recommended Agent Profile**:
  - Category: `deep` — Reason: Protocol transport/lifecycle work with concurrency and failure handling.
  - Skills: `[]` — Reason: No special skill matches better than direct engineering.
  - Omitted: `frontend-design` — Reason: No UI work.

  **Parallelization**: Can Parallel: YES | Wave 2 | Blocks: 7, 8, 9 | Blocked By: 1, 3, 4

  **References**:
  - Pattern: `base/common-1.20.1/src/main/java/space/controlnet/chatmc/common/agent/AgentRunner.java:164-189` — timeout/result mapping pattern
  - Pattern: `base/common-1.20.1/src/main/java/space/controlnet/chatmc/common/ChatMC.java:38-52` — startup/shutdown lifecycle hooks
  - External: `https://modelcontextprotocol.io/specification/2025-11-25/basic/lifecycle.md` — initialize/initialized lifecycle
  - External: `https://modelcontextprotocol.io/specification/2025-11-25/basic/transports.md` — stdio transport rules
  - External: `https://modelcontextprotocol.io/specification/2025-11-25/server/tools.md` — `tools/list` and `tools/call`

  **Acceptance Criteria**:
  - [ ] `./gradlew --no-daemon --configure-on-demand :base:common-1.20.1:test --tests 'space.controlnet.chatmc.common.tools.mcp.McpStdioClientLifecycleRegressionTest'` passes and proves initialize/list/call/close.
  - [ ] Stdio lifecycle tests prove malformed JSON, startup failure, and timeout map to stable tool errors.

  **QA Scenarios**:
  ```
  Scenario: Stdio MCP server initializes and lists configured tools
    Tool: Bash
    Steps: Run ./gradlew --no-daemon --configure-on-demand :base:common-1.20.1:test --tests 'space.controlnet.chatmc.common.tools.mcp.McpStdioClientLifecycleRegressionTest.task17_stdioInitialize_listAndCall_succeeds'
    Expected: Test passes by asserting initialize succeeds, one configured tool is listed, and a call returns normalized payload.
    Evidence: .sisyphus/evidence/task-5-mcp-stdio.xml

  Scenario: Stdio timeout maps to stable error
    Tool: Bash
    Steps: Run ./gradlew --no-daemon --configure-on-demand :base:common-1.20.1:test --tests 'space.controlnet.chatmc.common.tools.mcp.McpStdioClientLifecycleRegressionTest.task17_stdioTimeout_mapsToToolTimeoutError'
    Expected: Test passes by asserting the exact error classification used by the provider boundary.
    Evidence: .sisyphus/evidence/task-5-mcp-stdio-error.xml
  ```

  **Commit**: YES | Message: `feat(mcp): add stdio client transport` | Files: `base/common-1.20.1/src/main/java/space/controlnet/chatmc/common/tools/mcp/transport/*`, tests

- [x] 6. Implement public streamable HTTP MCP transport

  **What to do**: Implement a public streamable-HTTP MCP client transport using `java.net.http.HttpClient` with exact v1 constraints: request/response only, static base URL, no authentication, no OAuth, no SSE fallback, no websockets. Handle initialize/list/call, HTTP status mapping, JSON parse failures, disconnects, and timeouts. Keep per-server client instances isolated.
  **Must NOT do**: Do not add OAuth metadata discovery, interactive login, bearer auth, custom headers, or SSE fallback in this plan.

  **Recommended Agent Profile**:
  - Category: `deep` — Reason: Remote transport and failure isolation are the sharpest architecture risk in v1.
  - Skills: `[]` — Reason: Existing patterns plus MCP specs are enough.
  - Omitted: `github-cli` — Reason: No GitHub integration.

  **Parallelization**: Can Parallel: YES | Wave 2 | Blocks: 7, 8, 9 | Blocked By: 1, 3, 4

  **References**:
  - Pattern: `base/common-1.20.1/src/main/java/space/controlnet/chatmc/common/agent/AgentRunner.java:178-189` — timeout and failure mapping precedent
  - Pattern: `base/core/src/main/java/space/controlnet/chatmc/core/tools/ToolResult.java:3-10` — stable tool error shape
  - External: `https://modelcontextprotocol.io/specification/2025-11-25/basic/transports.md` — streamable HTTP transport
  **Acceptance Criteria**:
  - [ ] `./gradlew --no-daemon --configure-on-demand :base:common-1.20.1:test --tests 'space.controlnet.chatmc.common.tools.mcp.McpStreamableHttpClientLifecycleRegressionTest'` passes and proves initialize/list/call/404 failure/timeout behavior.
  - [ ] HTTP tests prove one failing remote server does not affect another server’s client instance.

  **QA Scenarios**:
  ```
  Scenario: Streamable HTTP MCP call succeeds against a public endpoint
    Tool: Bash
    Steps: Run ./gradlew --no-daemon --configure-on-demand :base:common-1.20.1:test --tests 'space.controlnet.chatmc.common.tools.mcp.McpStreamableHttpClientLifecycleRegressionTest.task17_httpInitialize_listAndCall_publicEndpoint_succeeds'
    Expected: Test passes by asserting successful list and normalized tool result with no auth headers.
    Evidence: .sisyphus/evidence/task-6-mcp-http.xml

  Scenario: HTTP 404 or timeout fails without poisoning healthy servers
    Tool: Bash
    Steps: Run ./gradlew --no-daemon --configure-on-demand :base:common-1.20.1:test --tests 'space.controlnet.chatmc.common.tools.mcp.McpStreamableHttpClientLifecycleRegressionTest.task17_httpNotFoundAndTimeout_areIsolated'
    Expected: Test passes by asserting stable HTTP/timeout errors and survival of the healthy server fixture.
    Evidence: .sisyphus/evidence/task-6-mcp-http-error.xml
  ```

  **Commit**: YES | Message: `feat(mcp): add public streamable http transport` | Files: `base/common-1.20.1/src/main/java/space/controlnet/chatmc/common/tools/mcp/transport/*`, tests

- [x] 7. Add MCP runtime manager and lifecycle wiring

  **What to do**: Add `McpRuntimeManager` in `base/common-1.20.1/src/main/java/space/controlnet/chatmc/common/tools/mcp/` that loads config, initializes per-server runtimes, discovers tools, registers providers, and tears everything down cleanly. Wire it into `ChatMC.init()` startup, `SERVER_STARTED`, `SERVER_STOPPED`, and `/chatmc reload` alongside the existing `PromptRuntime`/`McRuntimeManager` pattern. Use startup + reload as the only refresh model in v1. On reload, unregister all previous MCP providers first, then register the new healthy set atomically.
  **Must NOT do**: Do not implement live background `tools/list_changed` refresh. Do not allow one bad server to abort the whole MCP reload.

  **Recommended Agent Profile**:
  - Category: `unspecified-high` — Reason: Common-layer lifecycle orchestration with multiple integration points.
  - Skills: `[]` — Reason: Existing runtime patterns are enough.
  - Omitted: `writing` — Reason: Not docs-focused.

  **Parallelization**: Can Parallel: YES | Wave 2 | Blocks: 8, 9 | Blocked By: 1, 3, 4, 5, 6

  **References**:
  - Pattern: `base/common-1.20.1/src/main/java/space/controlnet/chatmc/common/ChatMC.java:29-55` — init/start/stop lifecycle hooks
  - Pattern: `base/common-1.20.1/src/main/java/space/controlnet/chatmc/common/commands/ChatMCCommands.java:54-63` — `/chatmc reload` integration point
  - Pattern: `base/common-1.20.1/src/main/java/space/controlnet/chatmc/common/llm/McRuntimeManager.java:17-64` — runtime manager reload/clear pattern
  - Pattern: `base/common-1.20.1/src/main/java/space/controlnet/chatmc/common/tools/ToolRegistry.java:29-75` — provider registration path to extend

  **Acceptance Criteria**:
  - [ ] `./gradlew --no-daemon --configure-on-demand :base:common-1.20.1:test --tests 'space.controlnet.chatmc.common.tools.mcp.McpRuntimeManagerReloadIsolationRegressionTest'` passes and proves startup/reload/stop lifecycle plus per-server isolation.
  - [ ] Reload tests prove stale MCP tools disappear after config changes.

  **QA Scenarios**:
  ```
  Scenario: Startup and reload register only healthy server providers
    Tool: Bash
    Steps: Run ./gradlew --no-daemon --configure-on-demand :base:common-1.20.1:test --tests 'space.controlnet.chatmc.common.tools.mcp.McpRuntimeManagerReloadIsolationRegressionTest.task17_reload_registersHealthyServersAndSkipsBrokenOnes'
    Expected: Test passes by asserting only healthy namespaced tools exist after reload.
    Evidence: .sisyphus/evidence/task-7-mcp-runtime.xml

  Scenario: Reload removes stale tools from previous config
    Tool: Bash
    Steps: Run ./gradlew --no-daemon --configure-on-demand :base:common-1.20.1:test --tests 'space.controlnet.chatmc.common.tools.mcp.McpRuntimeManagerReloadIsolationRegressionTest.task17_reload_unregistersStaleToolsBeforeRegisteringNewSet'
    Expected: Test passes by asserting old namespaced MCP tools are absent after the new config is applied.
    Evidence: .sisyphus/evidence/task-7-mcp-runtime-error.xml
  ```

  **Commit**: YES | Message: `feat(mcp): wire runtime lifecycle into startup and reload` | Files: `base/common-1.20.1/src/main/java/space/controlnet/chatmc/common/tools/mcp/McpRuntimeManager.java`, `base/common-1.20.1/src/main/java/space/controlnet/chatmc/common/ChatMC.java`, `base/common-1.20.1/src/main/java/space/controlnet/chatmc/common/commands/ChatMCCommands.java`, tests

- [x] 8. Implement McpToolProvider invocation and fallback rendering

  **What to do**: Implement `McpToolProvider` so discovered MCP tools are exposed through the current `ToolProvider` API and invoked through the transport clients. Normalize MCP call results into a deterministic JSON envelope with exact fields: `serverAlias`, `qualifiedTool`, `remoteTool`, `isError`, `textContent`, `structuredContent`, and `unsupportedContentTypes`. Add a generic MCP renderer that builds summary from args using deterministic key preference (`query`, `itemId`, `path`, `url`, `id`, then first scalar arg) and uses output lines from normalized result content, preferring `textContent`, then pretty-printed `structuredContent`, then explicit unsupported-content placeholders. Ensure all MCP calls return `ToolOutcome.result(...)`, never `ToolOutcome.proposal(...)`.
  **Must NOT do**: Do not introduce proposal/approval logic. Do not dump arbitrary nested payloads into custom bespoke renderers for each MCP tool.

  **Recommended Agent Profile**:
  - Category: `unspecified-high` — Reason: Provider bridge + rendering + normalized result behavior.
  - Skills: `[]` — Reason: Existing tool provider patterns are enough.
  - Omitted: `frontend-design` — Reason: Renderer is data/UI formatting, not frontend design.

  **Parallelization**: Can Parallel: YES | Wave 3 | Blocks: 9 | Blocked By: 1, 2, 3, 4, 5, 6, 7

  **References**:
  - Pattern: `base/common-1.20.1/src/main/java/space/controlnet/chatmc/common/tools/McToolProvider.java:28-103` — provider spec + execute pattern
  - Pattern: `base/common-1.20.1/src/main/java/space/controlnet/chatmc/common/tools/McToolProvider.java:236-239` — summary-from-input, lines-from-output render pattern
  - Pattern: `base/common-1.20.1/src/main/java/space/controlnet/chatmc/common/tools/ToolOutputFormatter.java:59-100` — generic output formatting fallback
  - Pattern: `base/core/src/main/java/space/controlnet/chatmc/core/tools/ToolPayload.java:3-6` — payload fields available to renderers
  - Pattern: `base/core/src/main/java/space/controlnet/chatmc/core/tools/ToolResult.java:3-10` — result envelope entry point

  **Acceptance Criteria**:
  - [ ] `./gradlew --no-daemon --configure-on-demand :base:common-1.20.1:test --tests 'space.controlnet.chatmc.common.tools.mcp.McpToolProviderInvocationRenderRegressionTest'` passes and proves invocation, normalized payload, summary generation, and lines fallback.
  - [ ] Invocation tests prove MCP error results return `ToolOutcome.result`, not proposals.

  **QA Scenarios**:
  ```
  Scenario: MCP provider returns deterministic summary and text-first lines
    Tool: Bash
    Steps: Run ./gradlew --no-daemon --configure-on-demand :base:common-1.20.1:test --tests 'space.controlnet.chatmc.common.tools.mcp.McpToolProviderInvocationRenderRegressionTest.task17_textContentResult_rendersSummaryAndLinesDeterministically'
    Expected: Test passes by asserting summary uses args preview and lines prefer text content.
    Evidence: .sisyphus/evidence/task-8-mcp-provider.xml

  Scenario: MCP error result stays non-proposal and renders structured failure details
    Tool: Bash
    Steps: Run ./gradlew --no-daemon --configure-on-demand :base:common-1.20.1:test --tests 'space.controlnet.chatmc.common.tools.mcp.McpToolProviderInvocationRenderRegressionTest.task17_errorResult_neverCreatesProposalAndPreservesStructuredFailure'
    Expected: Test passes by asserting `ToolOutcome.proposal()` is never used and failure payload remains renderable.
    Evidence: .sisyphus/evidence/task-8-mcp-provider-error.xml
  ```

  **Commit**: YES | Message: `feat(mcp): add provider invocation and fallback render` | Files: `base/common-1.20.1/src/main/java/space/controlnet/chatmc/common/tools/mcp/McpToolProvider.java`, `base/common-1.20.1/src/main/java/space/controlnet/chatmc/common/tools/mcp/McpFallbackRenderer.java`, tests

- [x] 9. Add end-to-end regression suite and combined verification path

  **What to do**: Add end-to-end regression coverage that exercises multiple servers, namespaced tool exposure, reload removal, tool-order determinism, timeout/disconnect mapping, and prompt visibility after metadata omission. Create fixtures/fake MCP servers for both stdio and HTTP. Add exactly `space.controlnet.chatmc.common.tools.mcp.McpEndToEndNamespacedRegressionTest` in `base/common-1.20.1` and exactly `space.controlnet.chatmc.core.agent.McpPromptToolOrderRegressionTest` in `base/core`. Ensure test names are concrete and aligned with acceptance criteria below.
  **Must NOT do**: Do not rely on manual server startup. Do not add flaky network-dependent tests.

  **Recommended Agent Profile**:
  - Category: `deep` — Reason: Final integration/regression hardening across all moving parts.
  - Skills: `[]` — Reason: Existing Gradle/JUnit patterns are enough.
  - Omitted: `artistry` — Reason: Deterministic regression work, not creative exploration.

  **Parallelization**: Can Parallel: YES | Wave 3 | Blocks: Final Verification Wave | Blocked By: 1, 2, 3, 4, 5, 6, 7, 8

  **References**:
  - Pattern: `base/core/build.gradle:16-24` — base/core JUnit setup
  - Pattern: `base/core/src/test/java/space/controlnet/chatmc/core/agent/ToolCallArgsParseBoundaryRegressionTest.java:55-94` — source-contract regression style
  - Pattern: `base/core/src/test/java/space/controlnet/chatmc/core/session/ServerSessionManagerStateMachineRegressionTest.java:28-388` — stable scenario-named assertions
  - Pattern: `base/common-1.20.1/src/main/java/space/controlnet/chatmc/common/agent/AgentRunner.java:152-190` — timeout/failure behavior to lock down

  **Acceptance Criteria**:
  - [ ] `./gradlew --no-daemon --configure-on-demand :base:core:test :base:common-1.20.1:test` passes.
  - [ ] `./gradlew --no-daemon --configure-on-demand :base:common-1.20.1:test --tests 'space.controlnet.chatmc.common.tools.mcp.McpEndToEndNamespacedRegressionTest'` passes.
  - [ ] `./gradlew --no-daemon --configure-on-demand :base:core:test --tests 'space.controlnet.chatmc.core.agent.McpPromptToolOrderRegressionTest'` passes.

  **QA Scenarios**:
  ```
  Scenario: Multiple namespaced servers coexist through reload and invocation
    Tool: Bash
    Steps: Run ./gradlew --no-daemon --configure-on-demand :base:common-1.20.1:test --tests 'space.controlnet.chatmc.common.tools.mcp.McpEndToEndNamespacedRegressionTest.task17_localAndRemoteServers_coexistUnderNamespacedTools'
    Expected: Test passes by asserting exact names `mcp.docs.search` and `mcp.wiki.fetch_page`, successful calls, and no cross-server collision.
    Evidence: .sisyphus/evidence/task-9-mcp-e2e.xml

  Scenario: Disconnect or timeout leaves stable errors and no duplicate registry state
    Tool: Bash
    Steps: Run ./gradlew --no-daemon --configure-on-demand :base:common-1.20.1:test --tests 'space.controlnet.chatmc.common.tools.mcp.McpEndToEndNamespacedRegressionTest.task17_disconnectAndTimeout_keepRegistryDeterministic'
    Expected: Test passes by asserting stable error mapping, no duplicate tools after retry/reload, and healthy servers remain available.
    Evidence: .sisyphus/evidence/task-9-mcp-e2e-error.xml
  ```

  **Commit**: YES | Message: `test(mcp): harden readonly integration regressions` | Files: `base/core/src/test/java/space/controlnet/chatmc/core/agent/*Mcp*.java`, `base/common-1.20.1/src/test/java/space/controlnet/chatmc/common/tools/mcp/*`, fixtures

## Final Verification Wave (MANDATORY — after ALL implementation tasks)
> 4 review agents run in PARALLEL. ALL must APPROVE. Present consolidated results to user and get explicit "okay" before completing.
> **Do NOT auto-proceed after verification. Wait for user's explicit approval before marking work complete.**
> **Never mark F1-F4 as checked before getting user's okay.** Rejection or user feedback -> fix -> re-run -> present again -> wait for okay.
- [x] F1. Plan Compliance Audit — oracle
  - **Execution**: Run an `oracle` review against `.sisyphus/plans/readonly-mcp-tools.md`, the final MCP-related diff, and the completed test outputs.
  - **Checks**: Every implemented file change maps to a plan task; every task acceptance command was executed; no plan task was skipped or silently merged away.
  - **Approval condition**: Oracle returns APPROVE with zero missing-task findings.
  - **Evidence**: `.sisyphus/evidence/f1-plan-compliance.md`
  - **QA Scenario**:
    ```
    Tool: task(subagent_type="oracle")
    Steps:
      1. Provide `.sisyphus/plans/readonly-mcp-tools.md`, the final MCP diff, and the executed verification command list.
      2. Ask Oracle to return only `APPROVE` or `BLOCK`, plus concrete missing-plan findings if blocked.
      3. Save the response verbatim to `.sisyphus/evidence/f1-plan-compliance.md`.
    Expected: Oracle returns `APPROVE`.
    ```
- [x] F2. Code Quality Review — unspecified-high
  - **Execution**: Run an `unspecified-high` review over all MCP production files plus the registry/threading changes.
  - **Checks**: Thread-safety, process/HTTP cleanup, deterministic registry behavior, no server-thread blocking on MCP I/O, no dead code in prompt-metadata optionalization.
  - **Approval condition**: Reviewer returns APPROVE with no blocking defects.
  - **Evidence**: `.sisyphus/evidence/f2-code-quality.md`
  - **QA Scenario**:
    ```
    Tool: task(category="unspecified-high")
    Steps:
      1. Provide the final MCP-related production files and test outputs.
      2. Ask the reviewer to return only `APPROVE` or `BLOCK` based on thread safety, cleanup, registry determinism, and prompt-contract correctness.
      3. Save the response verbatim to `.sisyphus/evidence/f2-code-quality.md`.
    Expected: Reviewer returns `APPROVE`.
    ```
- [x] F3. Real Manual QA — unspecified-high (+ interactive_bash if UI)
  - **Execution**: For this backend-only feature, interpret the template label as **agent-executed QA replay**. Run the combined verification command `./gradlew --no-daemon --configure-on-demand :base:core:test :base:common-1.20.1:test`, then execute the task-7 and task-9 end-to-end regression classes individually to confirm reload and multi-server coexistence paths.
  - **Checks**: Passing combined suite, passing reload-isolation regression, passing namespaced coexistence regression, and stable evidence artifacts captured from the resulting XML/log outputs.
  - **Approval condition**: All three commands pass with zero failed tests.
  - **Evidence**: `.sisyphus/evidence/f3-manual-qa.md`
  - **QA Scenario**:
    ```
    Tool: Bash
    Steps:
      1. Run `./gradlew --no-daemon --configure-on-demand :base:core:test :base:common-1.20.1:test`.
      2. Run `./gradlew --no-daemon --configure-on-demand :base:common-1.20.1:test --tests 'space.controlnet.chatmc.common.tools.mcp.McpRuntimeManagerReloadIsolationRegressionTest.task17_reload_registersHealthyServersAndSkipsBrokenOnes'`.
      3. Run `./gradlew --no-daemon --configure-on-demand :base:common-1.20.1:test --tests 'space.controlnet.chatmc.common.tools.mcp.McpEndToEndNamespacedRegressionTest.task17_localAndRemoteServers_coexistUnderNamespacedTools'`.
      4. Save command outputs and resulting XML paths to `.sisyphus/evidence/f3-manual-qa.md`.
    Expected: All three commands exit 0 with zero failed tests.
    ```
- [x] F4. Scope Fidelity Check — deep
  - **Execution**: Run a `deep` review over the final MCP implementation against the plan’s Must Have / Must NOT Have sections.
  - **Checks**: No write-capable MCP, no proposal/approval integration, no OAuth/bearer/SSE/WebSocket fallback, no synthetic examples, no unconditional full-catalog auto-registration, and config remains a pure shared-file subset with user-trusted readonly server selection.
  - **Approval condition**: Reviewer returns APPROVE with no scope violations.
  - **Evidence**: `.sisyphus/evidence/f4-scope-fidelity.md`
  - **QA Scenario**:
    ```
    Tool: task(category="deep")
    Steps:
      1. Provide the final MCP diff and `.sisyphus/plans/readonly-mcp-tools.md`.
      2. Ask the reviewer to return only `APPROVE` or `BLOCK` against the Must Have / Must NOT Have scope list.
      3. Save the response verbatim to `.sisyphus/evidence/f4-scope-fidelity.md`.
    Expected: Reviewer returns `APPROVE`.
    ```

## Commit Strategy
- Commit 1: `feat(mcp): add readonly server config model`
- Commit 2: `refactor(agent): support optional mcp tool metadata`
- Commit 3: `refactor(tools): support provider-scoped registry replacement`
- Commit 4: `feat(mcp): add schema mapping and execution affinity`
- Commit 5: `feat(mcp): add stdio client transport`
- Commit 6: `feat(mcp): add public streamable http transport`
- Commit 7: `feat(mcp): wire runtime lifecycle into startup and reload`
- Commit 8: `feat(mcp): add provider invocation and fallback render`
- Commit 9: `test(mcp): harden readonly integration regressions`

## Success Criteria
- ChatMC loads `config/chatmc/mcp.json` and supports multiple namespaced MCP servers in one runtime.
- Tools are exposed from the user-configured shared-file server set without extra ChatMC gating fields.
- Local stdio MCP and public remote streamable-HTTP MCP both work without blocking the Minecraft server thread.
- MCP metadata can be sparse without prompt bloat or fake examples.
- Reload removes stale MCP tools and preserves deterministic ordering for prompt injection.
- All specified core/common tests pass with no manual intervention.

## MCP JSON Schema Draft
> This schema is the exact ChatMC v1 accepted subset of the shared Claude/Codex-style MCP JSON shape. It is intentionally stricter than the full ecosystems: ChatMC v1 accepts only stdio and public http server forms and rejects extra fields like `oauth`, `env_vars`, custom headers, and ChatMC-only control keys.

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "https://chatmc/controlnet/mcp.schema.json",
  "title": "ChatMC MCP Config v1",
  "type": "object",
  "additionalProperties": false,
  "required": ["mcpServers"],
  "properties": {
    "mcpServers": {
      "type": "object",
      "propertyNames": {
        "type": "string",
        "pattern": "^[a-z0-9][a-z0-9-]{0,31}$"
      },
      "additionalProperties": {
        "oneOf": [
          {
            "type": "object",
            "additionalProperties": false,
            "required": ["type", "command"],
            "properties": {
              "type": { "const": "stdio" },
              "command": { "type": "string", "minLength": 1 },
              "args": {
                "type": "array",
                "items": { "type": "string" }
              },
              "env": {
                "type": "object",
                "additionalProperties": { "type": "string" }
              },
              "cwd": { "type": "string", "minLength": 1 }
            }
          },
          {
            "type": "object",
            "additionalProperties": false,
            "required": ["type", "url"],
            "properties": {
              "type": { "const": "http" },
              "url": {
                "type": "string",
                "format": "uri",
                "pattern": "^https?://"
              }
            }
          }
        ]
      }
    }
  }
}
```
