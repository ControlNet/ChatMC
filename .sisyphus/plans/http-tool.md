# HTTP Tool Implementation Plan

## TL;DR
> **Summary**: Add a first-party built-in tool named `http` that gives MineAgent complete single-request HTTP capability from the common runtime layer, using Java 17 `HttpClient` and explicit deterministic contracts for request validation, response decoding, limits, rendering, and test coverage.
> **Deliverables**:
> - Dedicated common-layer `http` tool provider and executor, registered from `MineAgent.init()`
> - Complete request/response contract with duplicate-preserving headers/query params, text/base64 request body support, redirect controls, and multi-mode response decoding
> - Stable tool error taxonomy, structured payload envelope, and HTTP-specific output rendering
> - Local loopback regression suite covering validation, statuses, redirects, binary/text decoding, limits, and timeout behavior
> - `REPO.md` updates documenting the new runtime behavior, defaults, and verification commands
> **Effort**: Large
> **Parallel**: YES - 3 waves
> **Critical Path**: 1 → 2 → 4 → 5 → 6 → 7 → 8 → 9

## Context
### Original Request
- User wants a new agent tool for outbound HTTP requests.
- Final naming decision: the tool is exactly `http`, with no `mc` or `mcp` namespace.
- Product direction is explicit: this phase should provide **complete HTTP request capability**, not a restricted fetch-only subset.
- User explicitly deferred request-safety hardening (SSRF/allowlists/etc.) to a future pass, but did not waive runtime correctness, determinism, or verification rigor.

### Interview Summary
- Existing first-party tools are registered through `ToolRegistry` and executed through `McSessionContext`; built-in runtime code belongs in `base/common-1.20.1`, while `base/core` stays runtime-neutral.
- `McSessionContext.executeTool(...)` dispatches `SERVER_THREAD` tools onto the MC main thread and leaves `CALLING_THREAD` tools on the caller thread, so network I/O must run with `CALLING_THREAD` affinity.
- `McpHttpTransport` already proves the repository accepts Java 17 `java.net.http.HttpClient`, explicit timeouts, response-size limits, and redirect control patterns for network-facing features.
- `McpToolProvider` and related tests establish the repo’s preferred style for normalized JSON envelopes, deterministic error contracts, and preservation of structured payloads on failures.
- `ToolOutputRendererRegistry` and `ToolOutputFormatter` are the correct extension points if the generic JSON fallback is insufficient for good UX.

### Metis Review (gaps addressed)
- Redirect behavior is frozen to a per-request `followRedirects` flag plus bounded `maxRedirects`; v1 returns only `finalUrl` and `redirectCount`, not full redirect history.
- Header and query fidelity is frozen to duplicate-preserving ordered entry arrays (`[{"name":"Accept","value":"application/json"}]`), not lossy maps.
- Request body behavior is frozen: `bodyText` and `bodyBase64` are mutually exclusive; `GET` and `HEAD` with bodies are rejected as `invalid_args` for deterministic semantics.
- Response decoding is frozen to `responseMode = auto | text | json | bytes`; `auto` treats `text/*`, `application/json`, `application/*+json`, `application/xml`, `text/xml`, `application/javascript`, and `application/x-www-form-urlencoded` as text, with UTF-8 fallback when charset is absent.
- Cookie persistence is explicitly out of scope for v1; every `http` invocation is stateless.
- Compression helpers are explicitly out of scope for v1; no special gzip/br handling beyond what Java `HttpClient` already surfaces.
- Audit/logging default is frozen: no additional redaction system is added in this phase; request/response payloads remain subject only to the explicit truncation/size limits defined below.

## Work Objectives
### Core Objective
Implement a first-party built-in tool named `http` that gives MineAgent a complete, deterministic, single-request HTTP execution path without introducing MCP indirection, while keeping all outbound network I/O off the Minecraft server thread.

### Deliverables
- `http` tool metadata and registration via `MineAgent.init()`
- Dedicated package `base/common-1.20.1/src/main/java/space/controlnet/mineagent/common/tools/http/` containing:
  - request/response contract records or package-private data carriers
  - provider and executor implementation
  - validation helpers and response-decoding helpers
  - optional HTTP-specific renderer
- Request schema fixed to:
  - `method`: HTTP token string, normalized to uppercase
  - `url`: absolute `http://` or `https://` URL
  - `query`: ordered array of `{name, value}` entries appended to the URL while preserving duplicates
  - `headers`: ordered array of `{name, value}` entries preserving duplicates
  - `bodyText`: optional string body
  - `bodyBase64`: optional base64-encoded binary body
  - `timeoutMs`: optional per-request timeout
  - `followRedirects`: optional boolean
  - `maxRedirects`: optional integer, used only when redirects are enabled
  - `responseMode`: `auto | text | json | bytes`
- Result payload envelope fixed to top-level `kind = "http_result"` with:
  - `request`: normalized effective request summary
  - `response`: populated for completed HTTP exchanges regardless of status code
  - `failure`: populated for local validation / timeout / transport / runtime failures
  - `truncated`: explicit boolean on body-bearing outcomes
- Stable tool error taxonomy for local failures (`invalid_args`, `tool_timeout`, `tool_execution_failed`, `response_too_large`, `unsupported_response_body`, etc.)
- Focused JUnit coverage using loopback `HttpServer` fixtures only
- `REPO.md` documentation for runtime behavior and verification

### Definition of Done (verifiable conditions with commands)
- `./gradlew --no-daemon --configure-on-demand :base:common-1.20.1:test --tests 'space.controlnet.mineagent.common.tools.http.HttpToolRegistrationRegressionTest'` passes
- `./gradlew --no-daemon --configure-on-demand :base:common-1.20.1:test --tests 'space.controlnet.mineagent.common.tools.http.HttpToolProviderValidationRegressionTest'` passes
- `./gradlew --no-daemon --configure-on-demand :base:common-1.20.1:test --tests 'space.controlnet.mineagent.common.tools.http.HttpToolExecutionRegressionTest'` passes
- `./gradlew --no-daemon --configure-on-demand :base:common-1.20.1:test --tests 'space.controlnet.mineagent.common.tools.http.HttpToolRenderingRegressionTest'` passes
- `./gradlew --no-daemon --configure-on-demand :base:common-1.20.1:test` passes

### Must Have
- Tool name exactly `http`
- Dedicated provider registered separately from `McToolProvider`
- `CALLING_THREAD` execution affinity
- Request/response contracts that preserve duplicate headers and query params
- Mutual-exclusion validation for `bodyText` and `bodyBase64`
- `GET` and `HEAD` with body rejected deterministically
- Completed `1xx/2xx/3xx/4xx/5xx` responses represented as successful tool results
- Local timeout, transport, parsing, validation, and truncation failures represented as stable tool errors with structured payloads
- Response body decoding that supports text, JSON-as-text, and binary/base64 modes
- Local loopback-only regression coverage; no public-internet tests
- `REPO.md` updated as part of the same implementation work

### Must NOT Have (guardrails, AI slop patterns, scope boundaries)
- No MCP server or MCP tool projection for this feature
- No reuse-by-refactor of `McpHttpTransport` that entangles MCP JSON-RPC/session logic with the built-in `http` tool
- No server-thread execution path for network I/O
- No cookie jar persistence across tool invocations
- No streaming/SSE/WebSocket support
- No multipart/form-data helper layer, retry framework, proxy support, TLS customization, or download-to-file behavior
- No security-policy hardening project (SSRF allowlists/deny lists, approval flows, secret redaction framework) in this iteration
- No hidden lossy normalization of duplicate headers or query parameters

### Default Policy Decisions (auto-applied)
- `timeoutMs`: default `10000`, min `1000`, max `25000`
- `followRedirects`: default `false`
- `maxRedirects`: default `5`, max `10`
- `responseMode`: default `auto`
- Request body limit: decoded UTF-8 / binary payload must be `<= 32768` bytes
- Response body read limit: `<= 262144` bytes, with truncation support captured in payload metadata rather than unlimited buffering
- Response headers are returned as duplicate-preserving ordered entry arrays, normalized to lowercase names for deterministic rendering
- No special response compression features are added beyond default Java client behavior
- No dedicated config file is added in v1; all limits are internal constants documented in `REPO.md`

## Verification Strategy
> ZERO HUMAN INTERVENTION — all verification is agent-executed.
- Test decision: **TDD** using focused JUnit suites in `:base:common-1.20.1:test`
- QA policy: Every task below includes executable happy-path and failure-path scenarios using loopback fixtures or reflective thread-confinement checks
- Evidence: `.sisyphus/evidence/task-{N}-{slug}.{ext}`

## Execution Strategy
### Parallel Execution Waves
> Target: 5-8 tasks per wave. <3 per wave (except final) = under-splitting.
> Extract shared dependencies as Wave-1 tasks for max parallelism.

Wave 1: contract freeze, registration/affinity, local HTTP fixture harness (Tasks 1-3)

Wave 2: request validation/building, execution/decoding/limits, rendering integration (Tasks 4-6)

Wave 3: regression matrix, documentation sync, full module verification and evidence sweep (Tasks 7-9)

### Dependency Matrix (full, all tasks)
- 1 blocks 4, 5, 6, 7, 8, 9
- 2 blocks 4, 5, 6, 7, 8, 9
- 3 blocks 5, 7, 9
- 4 blocks 5, 6, 7, 8, 9
- 5 blocks 6, 7, 8, 9
- 6 blocks 7, 8, 9
- 7 blocks 8, 9
- 8 blocks 9
- 9 feeds Final Verification Wave

### Agent Dispatch Summary (wave → task count → categories)
- Wave 1 → 3 tasks → `unspecified-high`, `deep`
- Wave 2 → 3 tasks → `deep`, `unspecified-high`
- Wave 3 → 3 tasks → `unspecified-high`, `quick`
- Final Verification Wave → 4 tasks → `oracle`, `unspecified-high`, `deep`

## TODOs
> Implementation + Test = ONE task. Never separate.
> EVERY task MUST have: Agent Profile + Parallelization + QA Scenarios.

- [x] 1. Freeze the `http` tool contract and metadata shape

  **What to do**: Create the dedicated common-layer package `base/common-1.20.1/src/main/java/space/controlnet/mineagent/common/tools/http/` and define the exact request/response contract types used by the built-in tool. Freeze the request shape to ordered duplicate-preserving arrays for `query` and `headers`, explicit mutual exclusivity between `bodyText` and `bodyBase64`, uppercase method normalization, `responseMode = auto|text|json|bytes`, and bounded redirect/timeout defaults. Build the `AgentToolSpec` for tool name `http` with complete args/result schema strings, argument descriptions, result descriptions, and at least two examples (JSON GET and JSON POST). Ensure the payload envelope always uses `kind = "http_result"` so future renderers can match deterministically.
  **Must NOT do**: Do not add this contract to `McToolProvider`. Do not use lossy `Map<String, String>` representations for headers or query params. Do not leave redirect/body/text-vs-binary semantics implicit.

  **Recommended Agent Profile**:
  - Category: `unspecified-high` — Reason: Contract design touches prompt schema, runtime payload shape, and all downstream tests.
  - Skills: `[]` — Reason: Existing repository patterns are sufficient.
  - Omitted: [`writing`] — Reason: This task is code-contract work, not prose-first documentation.

  **Parallelization**: Can Parallel: YES | Wave 1 | Blocks: 4, 5, 6, 7, 8, 9 | Blocked By: none

  **References** (executor has NO interview context — be exhaustive):
  - Pattern: `base/core/src/main/java/space/controlnet/mineagent/core/tools/AgentTool.java:9-65` — prompt-facing tool metadata contract with optional helper methods.
  - Pattern: `base/core/src/main/java/space/controlnet/mineagent/core/tools/AgentToolSpec.java:7-44` — simplest immutable metadata implementation to reuse instead of another custom inner class.
  - Pattern: `base/common-1.20.1/src/main/java/space/controlnet/mineagent/common/tools/McToolProvider.java:28-83` — existing first-party tool metadata style and example formatting expectations.
  - Pattern: `base/core/src/main/java/space/controlnet/mineagent/core/tools/ToolResult.java:3-15` — success/error result model, including payload-preserving error factory.
  - Pattern: `base/core/src/main/java/space/controlnet/mineagent/core/tools/ToolMessagePayload.java:17-39` — tool history payload serialization that must preserve structured JSON output.
  - Pattern: `base/core/src/main/java/space/controlnet/mineagent/core/agent/ToolCallArgsParseBoundary.java:3-22` — existing 65536-char args JSON ceiling that constrains request-body size choices.

  **Acceptance Criteria** (agent-executable only):
  - [ ] `./gradlew --no-daemon --configure-on-demand :base:common-1.20.1:test --tests 'space.controlnet.mineagent.common.tools.http.HttpToolContractRegressionTest'` passes and asserts the frozen request/response schema, defaults, duplicate-preserving entry arrays, and example payloads.
  - [ ] Tool metadata for `http` includes exact schema text describing `method`, `url`, `query`, `headers`, `bodyText`, `bodyBase64`, `timeoutMs`, `followRedirects`, `maxRedirects`, and `responseMode`.

  **QA Scenarios** (MANDATORY — task incomplete without these):
  ```
  Scenario: Contract examples and defaults are frozen
    Tool: Bash
    Steps: Run ./gradlew --no-daemon --configure-on-demand :base:common-1.20.1:test --tests 'space.controlnet.mineagent.common.tools.http.HttpToolContractRegressionTest.taskHttp_contract_defaultsAndExamples_areDeterministic'
    Expected: Test passes by asserting exact schema/default/example strings for the `http` tool metadata.
    Evidence: .sisyphus/evidence/task-1-http-contract.xml

  Scenario: Duplicate-preserving header/query entries remain non-lossy
    Tool: Bash
    Steps: Run ./gradlew --no-daemon --configure-on-demand :base:common-1.20.1:test --tests 'space.controlnet.mineagent.common.tools.http.HttpToolContractRegressionTest.taskHttp_contract_duplicateEntries_arePreserved'
    Expected: Test passes by asserting repeated `name/value` pairs survive request-contract parsing without collapse.
    Evidence: .sisyphus/evidence/task-1-http-contract-error.xml
  ```

  **Commit**: YES | Message: `feat(http): freeze tool contract and metadata` | Files: `base/common-1.20.1/src/main/java/space/controlnet/mineagent/common/tools/http/*`, contract tests

- [x] 2. Register the built-in `http` provider and lock execution affinity to `CALLING_THREAD`

  **What to do**: Add a dedicated `HttpToolProvider` under `base/common-1.20.1/src/main/java/space/controlnet/mineagent/common/tools/http/` and register it from `MineAgent.init()` with provider id `http`. The provider must expose exactly one tool named `http`, return `ToolProvider.ExecutionAffinity.CALLING_THREAD`, and provide deterministic `invalid_tool` / `unknown_tool` / `invalid_args` behavior before any network I/O occurs. Add focused regression coverage proving `McSessionContext.executeTool(...)` executes `http` on the caller thread even when no Minecraft server is present, matching the existing MCP-affinity pattern.
  **Must NOT do**: Do not place outbound I/O on `SERVER_THREAD`. Do not piggyback on `McToolProvider`. Do not register the tool under any alternate name or namespace.

  **Recommended Agent Profile**:
  - Category: `deep` — Reason: Thread-affinity mistakes here would produce the wrong runtime behavior even if the request code itself is correct.
  - Skills: `[]` — Reason: Existing thread-confinement tests provide the needed pattern.
  - Omitted: [`frontend-design`] — Reason: No UI work.

  **Parallelization**: Can Parallel: YES | Wave 1 | Blocks: 4, 5, 6, 7, 8, 9 | Blocked By: none

  **References**:
  - Pattern: `base/common-1.20.1/src/main/java/space/controlnet/mineagent/common/MineAgent.java:30-57` — built-in registration point for first-party common-layer providers.
  - Pattern: `base/common-1.20.1/src/main/java/space/controlnet/mineagent/common/tools/ToolProvider.java:14-27` — `ExecutionAffinity` contract and provider shape.
  - Pattern: `base/common-1.20.1/src/main/java/space/controlnet/mineagent/common/tools/ToolRegistry.java:33-148` — provider registration, lookup, and execution dispatch semantics.
  - Pattern: `base/common-1.20.1/src/main/java/space/controlnet/mineagent/common/agent/McSessionContext.java:57-102` — caller-thread vs server-thread dispatch logic.
  - Test: `base/common-1.20.1/src/test/java/space/controlnet/mineagent/common/tools/mcp/ToolExecutionAffinityRegressionTest.java:27-105` — exact reflective pattern for proving `CALLING_THREAD` execution with no server thread.

  **Acceptance Criteria**:
  - [ ] `./gradlew --no-daemon --configure-on-demand :base:common-1.20.1:test --tests 'space.controlnet.mineagent.common.tools.http.HttpToolRegistrationRegressionTest'` passes and proves `http` is registered by exact name through `MineAgent.init()`.
  - [ ] `./gradlew --no-daemon --configure-on-demand :base:common-1.20.1:test --tests 'space.controlnet.mineagent.common.tools.http.HttpToolThreadAffinityRegressionTest'` passes and proves `http` executes on the caller thread.

  **QA Scenarios**:
  ```
  Scenario: Tool registration exposes exact `http` name
    Tool: Bash
    Steps: Run ./gradlew --no-daemon --configure-on-demand :base:common-1.20.1:test --tests 'space.controlnet.mineagent.common.tools.http.HttpToolRegistrationRegressionTest.taskHttp_registration_exposesExactToolName'
    Expected: Test passes by asserting `ToolRegistry.getToolSpec("http")` is non-null and no alias names are registered.
    Evidence: .sisyphus/evidence/task-2-http-registration.xml

  Scenario: Caller-thread affinity works without Minecraft server thread
    Tool: Bash
    Steps: Run ./gradlew --no-daemon --configure-on-demand :base:common-1.20.1:test --tests 'space.controlnet.mineagent.common.tools.http.HttpToolThreadAffinityRegressionTest.taskHttp_callingThreadAffinity_executesWithoutServerThread'
    Expected: Test passes by asserting provider execution occurs on the worker thread and never returns `tool_execution_failed` due to missing server.
    Evidence: .sisyphus/evidence/task-2-http-registration-error.xml
  ```

  **Commit**: YES | Message: `feat(http): register built-in provider on calling thread` | Files: `base/common-1.20.1/src/main/java/space/controlnet/mineagent/common/MineAgent.java`, `base/common-1.20.1/src/main/java/space/controlnet/mineagent/common/tools/http/HttpToolProvider.java`, registration/affinity tests

- [x] 3. Build the loopback HTTP fixture harness and deterministic test utilities

  **What to do**: Add reusable loopback-only HTTP fixture helpers under `base/common-1.20.1/src/test/java/space/controlnet/mineagent/common/tools/http/` using `com.sun.net.httpserver.HttpServer`. The fixture layer must support: custom status codes, duplicate headers, delayed responses for timeout tests, redirect chains, binary payload responses, charset-specific text bodies, and body echo inspection for request-building assertions. Keep this harness isolated to tests so all later tasks can prove behavior without public-network dependencies.
  **Must NOT do**: Do not hit public internet services. Do not require GameTests. Do not bury fixture logic inside individual test classes where it cannot be reused by the execution matrix.

  **Recommended Agent Profile**:
  - Category: `unspecified-high` — Reason: Shared fixture design determines the quality and speed of every downstream regression test.
  - Skills: `[]` — Reason: Existing test utilities plus JDK `HttpServer` are enough.
  - Omitted: [`playwright`] — Reason: No browser automation.

  **Parallelization**: Can Parallel: YES | Wave 1 | Blocks: 5, 7, 9 | Blocked By: none

  **References**:
  - Test Pattern: `base/common-1.20.1/src/test/java/space/controlnet/mineagent/common/tools/mcp/McpRuntimeManagerReloadIsolationRegressionTest.java:64-207` — loopback `HttpServer` fixture strategy already used in this repo.
  - Test Pattern: `base/common-1.20.1/src/test/java/space/controlnet/mineagent/common/tools/mcp/McpRuntimeManagerReloadIsolationRegressionTest.java:251-515` — reusable fixture objects, temp resources, and close markers.
  - Utility: `base/common-1.20.1/src/test/java/space/controlnet/mineagent/common/testing/TimeoutUtility.java` — bounded wait pattern for deterministic timeout assertions.

  **Acceptance Criteria**:
  - [ ] `./gradlew --no-daemon --configure-on-demand :base:common-1.20.1:test --tests 'space.controlnet.mineagent.common.tools.http.HttpToolFixtureRegressionTest'` passes and proves the fixture layer can emit delayed, duplicate-header, redirect, text, and binary responses deterministically.
  - [ ] All later HTTP tests depend on this fixture package instead of bespoke inline servers.

  **QA Scenarios**:
  ```
  Scenario: Fixture emits duplicate headers and binary payloads deterministically
    Tool: Bash
    Steps: Run ./gradlew --no-daemon --configure-on-demand :base:common-1.20.1:test --tests 'space.controlnet.mineagent.common.tools.http.HttpToolFixtureRegressionTest.taskHttp_fixture_duplicateHeadersAndBinaryResponses_areStable'
    Expected: Test passes by asserting duplicate headers are emitted in order and binary bytes are preserved.
    Evidence: .sisyphus/evidence/task-3-http-fixture.xml

  Scenario: Fixture timeout path remains bounded
    Tool: Bash
    Steps: Run ./gradlew --no-daemon --configure-on-demand :base:common-1.20.1:test --tests 'space.controlnet.mineagent.common.tools.http.HttpToolFixtureRegressionTest.taskHttp_fixture_delayedResponse_supportsTimeoutScenarios'
    Expected: Test passes by proving delayed handlers can be awaited and torn down deterministically without hanging the suite.
    Evidence: .sisyphus/evidence/task-3-http-fixture-error.xml
  ```

  **Commit**: YES | Message: `test(http): add loopback fixture harness` | Files: `base/common-1.20.1/src/test/java/space/controlnet/mineagent/common/tools/http/*Fixture*`

- [x] 4. Implement request validation, normalization, and request-body construction

  **What to do**: Implement the request-side logic inside `HttpToolProvider` and dedicated helpers so `http` can parse incoming JSON args, validate method/URL/body combinations, normalize methods to uppercase, append duplicate-preserving query params to the URL, apply ordered headers, and build `HttpRequest.BodyPublisher` instances from either `bodyText` or `bodyBase64`. Enforce all fixed defaults and limits from this plan: body-text/body-base64 mutual exclusion, `GET`/`HEAD` body rejection, timeout clamping to `1000..25000`, redirect bounds to `0..10`, decoded request-body cap `32768` bytes, and stable error-code/message mapping. Return structured error payloads using `ToolResult.error(payloadJson, code, message)` so failures still preserve normalized request context.
  **Must NOT do**: Do not silently ignore conflicting body fields. Do not collapse duplicate query params or headers into a map. Do not allow malformed base64, blank methods, relative URLs, or non-HTTP(S) URLs to drift into execution.

  **Recommended Agent Profile**:
  - Category: `deep` — Reason: This task freezes the exact local contract and is the main source of future compatibility risk.
  - Skills: `[]` — Reason: Existing tool/error patterns are enough.
  - Omitted: [`writing`] — Reason: No docs-only work.

  **Parallelization**: Can Parallel: YES | Wave 2 | Blocks: 5, 6, 7, 8, 9 | Blocked By: 1, 2

  **References**:
  - Pattern: `base/common-1.20.1/src/main/java/space/controlnet/mineagent/common/tools/McToolProvider.java:93-133` — local argument parsing and deterministic `invalid_args` / domain-error style for first-party tools.
  - Pattern: `base/common-1.20.1/src/main/java/space/controlnet/mineagent/common/tools/mcp/McpToolProvider.java` — use stable local validation before delegating remote execution and preserve structured payloads on failure.
  - Pattern: `base/core/src/main/java/space/controlnet/mineagent/core/tools/ToolResult.java:3-15` — payload-preserving error contract to reuse.
  - Constraint: `base/core/src/main/java/space/controlnet/mineagent/core/agent/ToolCallArgsParseBoundary.java:10-20` — overall args JSON cannot exceed 65536 chars.

  **Acceptance Criteria**:
  - [ ] `./gradlew --no-daemon --configure-on-demand :base:common-1.20.1:test --tests 'space.controlnet.mineagent.common.tools.http.HttpToolProviderValidationRegressionTest'` passes with exact assertions for invalid URL, blank method, unsupported scheme, conflicting body fields, invalid base64, body-on-GET/HEAD, and request-body-size overflow.
  - [ ] Validation failures use stable error codes/messages and include structured payloads with normalized request summaries.

  **QA Scenarios**:
  ```
  Scenario: Mutually exclusive request body fields are rejected deterministically
    Tool: Bash
    Steps: Run ./gradlew --no-daemon --configure-on-demand :base:common-1.20.1:test --tests 'space.controlnet.mineagent.common.tools.http.HttpToolProviderValidationRegressionTest.taskHttp_validation_bodyTextAndBodyBase64Conflict_returnsInvalidArgs'
    Expected: Test passes by asserting `success=false`, `error.code=invalid_args`, and payload contains the normalized request summary.
    Evidence: .sisyphus/evidence/task-4-http-validation.xml

  Scenario: Duplicate query params survive normalization and invalid base64 is rejected
    Tool: Bash
    Steps: Run ./gradlew --no-daemon --configure-on-demand :base:common-1.20.1:test --tests 'space.controlnet.mineagent.common.tools.http.HttpToolProviderValidationRegressionTest.taskHttp_validation_duplicateQueryPreservedAndInvalidBase64Rejected'
    Expected: Test passes by asserting duplicate `?a=1&a=2` order is preserved in the normalized request URL and malformed base64 produces `invalid_args`.
    Evidence: .sisyphus/evidence/task-4-http-validation-error.xml
  ```

  **Commit**: YES | Message: `feat(http): validate requests and normalize inputs` | Files: `base/common-1.20.1/src/main/java/space/controlnet/mineagent/common/tools/http/*`, validation tests

- [x] 5. Implement Java 17 `HttpClient` execution, redirect loop, response decoding, and bounded envelope generation

  **What to do**: Build a dedicated executor/helper for actual HTTP execution using Java 17 `HttpClient` in the common layer. Use a stateless client configuration with no cookie manager, explicit connect/request timeouts, and no built-in redirect following; implement the redirect loop yourself so `followRedirects`, `maxRedirects`, `finalUrl`, and `redirectCount` are deterministic and testable. Treat any completed HTTP exchange — including `404`, `500`, and redirect terminal responses when following is disabled — as a successful `ToolResult.ok(...)` payload. Populate the structured `http_result` envelope with normalized request data, status code, final URL, duplicate-preserving response headers, content-type/charset metadata, declared content length if present, text-or-base64 body fields, returned byte count, and truncation markers. Use `ToolResult.error(...)` only for local timeout, connection refusal, DNS failure, oversized response, undecodable forced-text/json bodies, or other runtime failures.
  **Must NOT do**: Do not rely on `HttpClient.Redirect.ALWAYS/NORMAL` because the plan requires explicit redirect counts and caps. Do not treat HTTP status codes as tool failures. Do not buffer unbounded response bodies.

  **Recommended Agent Profile**:
  - Category: `deep` — Reason: Network execution, redirect semantics, response decoding, and truncation are the technical core of the feature.
  - Skills: `[]` — Reason: The repo’s MCP transport and local fixture patterns are sufficient guidance.
  - Omitted: [`frontend-design`] — Reason: No UI work.

  **Parallelization**: Can Parallel: YES | Wave 2 | Blocks: 6, 7, 8, 9 | Blocked By: 1, 2, 3, 4

  **References**:
  - Pattern: `base/common-1.20.1/src/main/java/space/controlnet/mineagent/common/tools/mcp/transport/McpHttpTransport.java:70-189` — Java 17 `HttpClient` construction with explicit timeout and executor.
  - Pattern: `base/common-1.20.1/src/main/java/space/controlnet/mineagent/common/tools/mcp/transport/McpHttpTransport.java:191-255` — bounded response-byte reading and unsupported-content handling.
  - Pattern: `base/common-1.20.1/src/test/java/space/controlnet/mineagent/common/tools/mcp/McpToolProviderInvocationRenderRegressionTest.java:21-176` — normalized envelope testing style and distinction between HTTP-like success vs transport failure.
  - JDK Guidance: Java 17 `java.net.http.HttpClient` is already an accepted repository dependency surface via MCP transport.

  **Acceptance Criteria**:
  - [ ] `./gradlew --no-daemon --configure-on-demand :base:common-1.20.1:test --tests 'space.controlnet.mineagent.common.tools.http.HttpToolExecutionRegressionTest'` passes and covers successful GET/POST, 404/500-as-success, timeout, connection refusal, redirects enabled/disabled, text/json/bytes response modes, and oversized response handling.
  - [ ] Redirects are manually bounded and the payload reports `finalUrl` and `redirectCount` correctly.

  **QA Scenarios**:
  ```
  Scenario: HTTP 404 remains a successful tool result with status and body data
    Tool: Bash
    Steps: Run ./gradlew --no-daemon --configure-on-demand :base:common-1.20.1:test --tests 'space.controlnet.mineagent.common.tools.http.HttpToolExecutionRegressionTest.taskHttp_execution_http404_returnsSuccessfulToolResult'
    Expected: Test passes by asserting `success=true`, `response.status=404`, and response body metadata is preserved.
    Evidence: .sisyphus/evidence/task-5-http-execution.xml

  Scenario: Timeout and redirect-cap failures use stable local error contracts
    Tool: Bash
    Steps: Run ./gradlew --no-daemon --configure-on-demand :base:common-1.20.1:test --tests 'space.controlnet.mineagent.common.tools.http.HttpToolExecutionRegressionTest.taskHttp_execution_timeoutAndRedirectOverflow_returnStableErrors'
    Expected: Test passes by asserting timeout maps to `tool_timeout` and redirect overflow maps to a stable local failure code with structured payload.
    Evidence: .sisyphus/evidence/task-5-http-execution-error.xml
  ```

  **Commit**: YES | Message: `feat(http): execute requests and decode responses` | Files: `base/common-1.20.1/src/main/java/space/controlnet/mineagent/common/tools/http/*`, execution tests

- [x] 6. Add deterministic HTTP rendering and tool-history compatibility

  **What to do**: Add a dedicated HTTP renderer only after the payload envelope from Task 5 is stable. Register it via `ToolOutputRendererRegistry` from `MineAgent.init()`. The renderer must recognize `kind = "http_result"` and produce concise deterministic lines for: method + final URL, status code, content type, truncation marker, and a body preview. If `responseMode=bytes` or the body is binary, render metadata and byte counts rather than dumping unreadable content. If the tool failed locally, render the failure code/message plus the normalized request summary. Add regression tests proving `ToolMessagePayload.wrap(...)` round-trips the structured payload cleanly for both success and failure outputs.
  **Must NOT do**: Do not produce LLM-generated summaries. Do not dump arbitrarily large raw bodies into rendered lines. Do not add a renderer before the envelope contract is finalized.

  **Recommended Agent Profile**:
  - Category: `unspecified-high` — Reason: Crosses provider payloads, formatter hooks, and UI/history readability.
  - Skills: `[]` — Reason: Existing renderer registry patterns are sufficient.
  - Omitted: [`frontend-design`] — Reason: This is deterministic rendering, not visual redesign.

  **Parallelization**: Can Parallel: YES | Wave 2 | Blocks: 7, 8, 9 | Blocked By: 1, 2, 4, 5

  **References**:
  - Pattern: `base/common-1.20.1/src/main/java/space/controlnet/mineagent/common/client/render/ToolOutputRendererRegistry.java:9-39` — renderer registration and dispatch model.
  - Pattern: `base/common-1.20.1/src/main/java/space/controlnet/mineagent/common/tools/ToolOutputFormatter.java:60-106` — formatter fallback path and renderer invocation order.
  - Pattern: `base/core/src/main/java/space/controlnet/mineagent/core/tools/ToolMessagePayload.java:21-39` — tool-message wrapping behavior for structured JSON output.
  - Test: `base/core/src/test/java/space/controlnet/mineagent/core/tools/ToolMessagePayloadRegressionTest.java:12-53` — round-trip expectations for structured tool payloads.
  - Test: `base/common-1.20.1/src/test/java/space/controlnet/mineagent/common/client/render/ToolOutputRendererRegistryRegressionTest.java:11-65` — registry determinism and renderer selection.

  **Acceptance Criteria**:
  - [ ] `./gradlew --no-daemon --configure-on-demand :base:common-1.20.1:test --tests 'space.controlnet.mineagent.common.tools.http.HttpToolRenderingRegressionTest'` passes and proves success/failure outputs render concise deterministic lines.
  - [ ] Tool-message payload wrapping preserves structured HTTP success and failure payloads without collapsing JSON into unreadable strings.

  **QA Scenarios**:
  ```
  Scenario: Successful text response renders summary lines without dumping raw JSON envelope
    Tool: Bash
    Steps: Run ./gradlew --no-daemon --configure-on-demand :base:common-1.20.1:test --tests 'space.controlnet.mineagent.common.tools.http.HttpToolRenderingRegressionTest.taskHttp_rendering_successRendersSummaryAndBodyPreview'
    Expected: Test passes by asserting rendered lines show method/url/status/content-type/body preview rather than raw envelope JSON.
    Evidence: .sisyphus/evidence/task-6-http-rendering.xml

  Scenario: Failure payload round-trips through ToolMessagePayload and renderer deterministically
    Tool: Bash
    Steps: Run ./gradlew --no-daemon --configure-on-demand :base:common-1.20.1:test --tests 'space.controlnet.mineagent.common.tools.http.HttpToolRenderingRegressionTest.taskHttp_rendering_failurePayloadRoundTrips'
    Expected: Test passes by asserting wrapped history payload preserves structured failure JSON and renders stable failure lines.
    Evidence: .sisyphus/evidence/task-6-http-rendering-error.xml
  ```

  **Commit**: YES | Message: `feat(http): add deterministic renderer integration` | Files: `base/common-1.20.1/src/main/java/space/controlnet/mineagent/common/client/render/*Http*`, `MineAgent.java`, rendering tests

- [x] 7. Lock the full HTTP edge-case regression matrix

  **What to do**: Expand the HTTP regression suite so the tool contract is fully protected before broad verification. Cover the exact edge cases frozen in this plan: `204`, `304`, `HEAD` without body, duplicate response headers, duplicate query params, charset fallback, explicit ISO-8859-1 text, binary responses in `auto` and `bytes` modes, forced `text/json` decoding failures on binary content, connection refused, DNS failure, redirect disabled, redirect overflow, request-body size overflow, response truncation, and stateless repeated invocations.
  **Must NOT do**: Do not leave any edge case that materially changes decoding or local error semantics uncovered. Do not merge this task with docs work; keep the regression lock separate and test-first.

  **Recommended Agent Profile**:
  - Category: `unspecified-high` — Reason: This task is the broad behavioral lock for the feature and determines future compatibility.
  - Skills: `[]` — Reason: Existing repo/testing/doc patterns are sufficient.
  - Omitted: [`writing`] — Reason: This is still test/contract work, not primary prose work.

  **Parallelization**: Can Parallel: YES | Wave 3 | Blocks: 8, 9 | Blocked By: 1, 2, 3, 4, 5, 6

  **References**:
  - Test Pattern: `base/common-1.20.1/src/test/java/space/controlnet/mineagent/common/tools/mcp/McpToolProviderInvocationRenderRegressionTest.java:22-220` — broad provider regression style with explicit envelope assertions.
  - Test Pattern: `base/common-1.20.1/src/test/java/space/controlnet/mineagent/common/tools/mcp/ToolExecutionAffinityRegressionTest.java:27-105` — targeted deterministic runtime tests with exact assertion names.

  **Acceptance Criteria**:
  - [ ] `./gradlew --no-daemon --configure-on-demand :base:common-1.20.1:test --tests 'space.controlnet.mineagent.common.tools.http.*'` passes and covers the edge-case matrix frozen by this plan.

  **QA Scenarios**:
  ```
  Scenario: Charset, binary, and no-body edge cases are covered by focused tests
    Tool: Bash
    Steps: Run ./gradlew --no-daemon --configure-on-demand :base:common-1.20.1:test --tests 'space.controlnet.mineagent.common.tools.http.HttpToolExecutionRegressionTest'
    Expected: Test passes by asserting ISO-8859-1 decoding, binary `bytes` mode, `204/304/HEAD` empty-body handling, and response truncation behave exactly as documented.
    Evidence: .sisyphus/evidence/task-7-http-regression.xml

  Scenario: Redirect, DNS, and truncation failures use exact stable contracts
    Tool: Bash
    Steps: Run ./gradlew --no-daemon --configure-on-demand :base:common-1.20.1:test --tests 'space.controlnet.mineagent.common.tools.http.HttpToolExecutionRegressionTest.taskHttp_execution_redirectDnsAndTruncationFailures_areStable'
    Expected: Test passes by asserting redirect-disabled, DNS failure, and response truncation scenarios each emit the expected stable payload and error/result contract.
    Evidence: .sisyphus/evidence/task-7-http-regression-error.xml
  ```

  **Commit**: YES | Message: `test(http): lock edge case regression matrix` | Files: `base/common-1.20.1/src/test/java/space/controlnet/mineagent/common/tools/http/*`

- [x] 8. Update `REPO.md` and add documentation contract checks

  **What to do**: Update `REPO.md` to document the new `http` tool, its request schema, response envelope, defaults/limits, deferred non-goals, and the exact verification commands implementers must run. Add at least one documentation contract regression test that asserts key defaults and command lines remain synchronized with the implementation contract, so future edits cannot silently drift.
  **Must NOT do**: Do not document security restrictions that are explicitly deferred in this iteration. Do not leave command examples stale or approximate.

  **Recommended Agent Profile**:
  - Category: `unspecified-high` — Reason: This task ties runtime behavior documentation directly to executable regression checks.
  - Skills: `[]` — Reason: Existing repo patterns are enough.
  - Omitted: [`writing`] — Reason: The docs are technical contract synchronization, not open-ended prose.

  **Parallelization**: Can Parallel: YES | Wave 3 | Blocks: 9 | Blocked By: 1, 2, 4, 5, 6, 7

  **References**:
  - Rule Source: `AGENTS.md:157` — runtime behavior changes must be recorded in `REPO.md` in the same change.
  - Docs Pattern: `REPO.md` current runtime/test command sections — keep command examples synchronized with actual tasks.
  - Existing Commands: `AGENTS.md` JUnit command guidance — follow the same copy-pastable command style in documentation.

  **Acceptance Criteria**:
  - [ ] `REPO.md` documents the `http` tool request schema, response envelope, defaults, non-goals, and exact verification commands.
  - [ ] `./gradlew --no-daemon --configure-on-demand :base:common-1.20.1:test --tests 'space.controlnet.mineagent.common.tools.http.HttpToolDocumentationContractRegressionTest'` passes.

  **QA Scenarios**:
  ```
  Scenario: REPO.md documents actual commands and defaults
    Tool: Bash
    Steps: Run ./gradlew --no-daemon --configure-on-demand :base:common-1.20.1:test --tests 'space.controlnet.mineagent.common.tools.http.HttpToolDocumentationContractRegressionTest.taskHttp_docs_commandsAndDefaults_matchImplementation'
    Expected: Test passes by asserting documented defaults and command examples remain synchronized with the implementation contract.
    Evidence: .sisyphus/evidence/task-8-http-docs.xml

  Scenario: REPO.md records deferred non-goals explicitly
    Tool: Bash
    Steps: Run ./gradlew --no-daemon --configure-on-demand :base:common-1.20.1:test --tests 'space.controlnet.mineagent.common.tools.http.HttpToolDocumentationContractRegressionTest.taskHttp_docs_nonGoalsMatchCurrentScope'
    Expected: Test passes by asserting deferred items such as SSRF hardening and streaming support are documented as out-of-scope for this iteration.
    Evidence: .sisyphus/evidence/task-8-http-docs-error.xml
  ```

  **Commit**: YES | Message: `docs(repo): document http tool contract and limits` | Files: `REPO.md`, docs contract tests

- [x] 9. Run focused and module-wide verification, fix drift, and collect evidence artifacts

  **What to do**: Execute the narrow regression classes first, then run the full `:base:common-1.20.1:test` module suite and fix any drift caused by registration, rendering, or output-format changes. Collect JUnit XML evidence for all new HTTP regression classes and a consolidated summary for the module-wide run under `.sisyphus/evidence/`. If unrelated common-layer tests fail, only fix regressions directly caused by the `http` tool work; record unrelated pre-existing failures separately and do not silently widen scope. Confirm that the final tool output remains compatible with existing `ToolOutputFormatter` and renderer ordering.
  **Must NOT do**: Do not jump straight to a broad build before narrow tests are green. Do not widen scope into other modules unless the `http` changes provably broke them. Do not finish without evidence files.

  **Recommended Agent Profile**:
  - Category: `quick` — Reason: The work is primarily test execution, drift correction, and evidence collection after implementation is complete.
  - Skills: `[]` — Reason: No special external domain skill is needed.
  - Omitted: [`git-master`] — Reason: This task is verification, not Git manipulation.

  **Parallelization**: Can Parallel: YES | Wave 3 | Blocks: Final Verification Wave | Blocked By: 1, 2, 3, 4, 5, 6, 7, 8

  **References**:
  - Command Guidance: `AGENTS.md` JUnit commands section — prefer narrow `:base:common-1.20.1:test` runs first.
  - Evidence Pattern: existing `.sisyphus/evidence/task-*.md|xml|json` artifacts in this repo — produce equivalent files for the HTTP tool work.
  - Formatter Hook: `base/common-1.20.1/src/main/java/space/controlnet/mineagent/common/tools/ToolOutputFormatter.java:71-106` — verify renderer ordering still behaves as intended after registration.

  **Acceptance Criteria**:
  - [ ] All targeted HTTP regression classes pass individually.
  - [ ] `./gradlew --no-daemon --configure-on-demand :base:common-1.20.1:test` passes.
  - [ ] Evidence artifacts for narrow tests and the full module run are written to `.sisyphus/evidence/`.

  **QA Scenarios**:
  ```
  Scenario: Focused HTTP regression suite passes before broad module run
    Tool: Bash
    Steps: Run ./gradlew --no-daemon --configure-on-demand :base:common-1.20.1:test --tests 'space.controlnet.mineagent.common.tools.http.*'
    Expected: BUILD SUCCESSFUL and XML reports for the HTTP test classes show failures=0 errors=0.
    Evidence: .sisyphus/evidence/task-9-http-focused-suite.xml

  Scenario: Full common module suite remains green after `http` integration
    Tool: Bash
    Steps: Run ./gradlew --no-daemon --configure-on-demand :base:common-1.20.1:test
    Expected: BUILD SUCCESSFUL with no regressions introduced outside the new HTTP package and renderer registration path.
    Evidence: .sisyphus/evidence/task-9-http-common-suite.xml
  ```

  **Commit**: YES | Message: `chore(http): verify module suite and collect evidence` | Files: `.sisyphus/evidence/*` plus any minimal regression fixes required by verification

## Final Verification Wave (MANDATORY — after ALL implementation tasks)
> 4 review agents run in PARALLEL. ALL must APPROVE. Present consolidated results to user and get explicit "okay" before completing.
> **Do NOT auto-proceed after verification. Wait for user's explicit approval before marking work complete.**
> **Never mark F1-F4 as checked before getting user's okay.** Rejection or user feedback -> fix -> re-run -> present again -> wait for okay.
- [x] F1. Plan Compliance Audit — oracle

  **What to do**: Run an oracle review against the completed implementation and compare it directly to this plan. Verify that tool naming, placement, affinity, request/response contract, redirect policy, limits, renderer integration, docs sync, and verification commands all match the plan without silent deviation.
  **Must NOT do**: Do not accept “close enough” behavior if it changes public contract, limits, or execution affinity. Do not skip checking `REPO.md` against implemented defaults.

  **Recommended Agent Profile**:
  - Category: `oracle` — Reason: This is a correctness-against-plan audit, not implementation work.
  - Skills: `[]` — Reason: Repo context plus the plan is sufficient.
  - Omitted: [`writing`] — Reason: Output is an audit result, not prose generation.

  **Parallelization**: Can Parallel: YES | Wave Final | Blocks: none | Blocked By: 9

  **References**:
  - Plan: `.sisyphus/plans/http-tool.md`
  - Registration: `base/common-1.20.1/src/main/java/space/controlnet/mineagent/common/MineAgent.java`
  - Affinity/dispatch: `base/common-1.20.1/src/main/java/space/controlnet/mineagent/common/tools/ToolProvider.java`, `base/common-1.20.1/src/main/java/space/controlnet/mineagent/common/agent/McSessionContext.java`
  - Docs: `REPO.md`

  **Acceptance Criteria**:
  - [ ] Oracle reports no plan-vs-implementation mismatches.
  - [ ] Any mismatch found is fixed before re-running F1.

  **QA Scenarios**:
  ```
  Scenario: Oracle audits implementation against plan
    Tool: task (oracle)
    Steps: Review `.sisyphus/plans/http-tool.md` against the implemented files and produced evidence artifacts.
    Expected: Oracle returns approval with no unresolved contract, file-placement, or verification mismatches.
    Evidence: .sisyphus/evidence/f1-http-plan-compliance.md

  Scenario: Oracle rejects drift in limits or affinity
    Tool: task (oracle)
    Steps: Re-check timeout/redirect/body-size defaults, tool name, and `CALLING_THREAD` affinity against the plan.
    Expected: Any drift is reported explicitly and must be resolved before approval.
    Evidence: .sisyphus/evidence/f1-http-plan-compliance-error.md
  ```

  **Commit**: NO | Message: `n/a` | Files: none

- [x] F2. Code Quality Review — unspecified-high

  **What to do**: Run a high-effort code review focused on readability, cohesion, error taxonomy stability, fixture quality, and regression completeness for the new HTTP package and any touched renderer/docs files.
  **Must NOT do**: Do not broaden into unrelated refactors. Do not approve if validation, envelope generation, or renderer logic is overly coupled or leaves hidden lossy behavior.

  **Recommended Agent Profile**:
  - Category: `unspecified-high` — Reason: Broad code/test quality review across implementation and regression surfaces.
  - Skills: `[]` — Reason: No special domain skill beyond repository standards is required.
  - Omitted: [`frontend-design`] — Reason: Not a visual design review.

  **Parallelization**: Can Parallel: YES | Wave Final | Blocks: none | Blocked By: 9

  **References**:
  - HTTP package: `base/common-1.20.1/src/main/java/space/controlnet/mineagent/common/tools/http/`
  - Renderer package: `base/common-1.20.1/src/main/java/space/controlnet/mineagent/common/client/render/`
  - Tests: `base/common-1.20.1/src/test/java/space/controlnet/mineagent/common/tools/http/`

  **Acceptance Criteria**:
  - [ ] Reviewer reports no unresolved code-quality or regression-quality defects.
  - [ ] Validation logic, execution logic, and renderer logic are clearly separated and test-backed.

  **QA Scenarios**:
  ```
  Scenario: High-effort code and regression quality review passes
    Tool: task (unspecified-high)
    Steps: Review implementation files, renderer integration, and test suite for cohesion, determinism, and stable contracts.
    Expected: Reviewer approves with no unresolved maintainability or regression gaps.
    Evidence: .sisyphus/evidence/f2-http-code-quality.md

  Scenario: Reviewer flags hidden contract ambiguity if present
    Tool: task (unspecified-high)
    Steps: Specifically inspect duplicate header/query handling, redirect loop logic, truncation semantics, and docs/test synchronization.
    Expected: Any ambiguity is surfaced explicitly and must be resolved before approval.
    Evidence: .sisyphus/evidence/f2-http-code-quality-error.md
  ```

  **Commit**: NO | Message: `n/a` | Files: none

- [x] F3. Agent-Executed Scenario Replay — unspecified-high

  **What to do**: Re-run the implemented verification scenarios end-to-end as an independent QA pass using only agent-executed commands and produced evidence. This is the zero-human-intervention replacement for the old manual-QA wording.
  **Must NOT do**: Do not rely on visual/manual inspection. Do not skip failure-path replay.

  **Recommended Agent Profile**:
  - Category: `unspecified-high` — Reason: Independent scenario replay across the focused HTTP suite and the full common module suite.
  - Skills: `[]` — Reason: Command execution and evidence inspection are sufficient.
  - Omitted: [`playwright`] — Reason: No browser/UI path exists here.

  **Parallelization**: Can Parallel: YES | Wave Final | Blocks: none | Blocked By: 9

  **References**:
  - Evidence: `.sisyphus/evidence/task-*-http-*.xml`
  - Commands: `./gradlew --no-daemon --configure-on-demand :base:common-1.20.1:test --tests 'space.controlnet.mineagent.common.tools.http.*'`, `./gradlew --no-daemon --configure-on-demand :base:common-1.20.1:test`

  **Acceptance Criteria**:
  - [ ] Focused HTTP regression suite is replayed successfully.
  - [ ] Full common-module test suite is replayed successfully.
  - [ ] Replay evidence matches the final approved implementation state.

  **QA Scenarios**:
  ```
  Scenario: Focused HTTP suite replay stays green
    Tool: Bash
    Steps: Run ./gradlew --no-daemon --configure-on-demand :base:common-1.20.1:test --tests 'space.controlnet.mineagent.common.tools.http.*'
    Expected: BUILD SUCCESSFUL and replay XML reports remain failures=0 errors=0.
    Evidence: .sisyphus/evidence/f3-http-scenario-replay.xml

  Scenario: Full common-module replay stays green
    Tool: Bash
    Steps: Run ./gradlew --no-daemon --configure-on-demand :base:common-1.20.1:test
    Expected: BUILD SUCCESSFUL with no regressions introduced by the HTTP integration.
    Evidence: .sisyphus/evidence/f3-http-scenario-replay-error.xml
  ```

  **Commit**: NO | Message: `n/a` | Files: none

- [x] F4. Scope Fidelity Check — deep

  **What to do**: Perform a deep review ensuring the delivered work stayed inside the plan’s scope: built-in `http` tool only, no MCP migration, no cookie persistence, no streaming, no proxy/TLS framework, no security-hardening project, and no unrelated module refactors.
  **Must NOT do**: Do not approve if convenience additions silently widened the feature beyond the agreed phase scope.

  **Recommended Agent Profile**:
  - Category: `deep` — Reason: Scope drift can hide in “helpful” extras that are not part of the agreed iteration.
  - Skills: `[]` — Reason: The plan and changed files are sufficient.
  - Omitted: [`writing`] — Reason: This is a scope audit, not documentation work.

  **Parallelization**: Can Parallel: YES | Wave Final | Blocks: none | Blocked By: 9

  **References**:
  - Plan scope sections: `.sisyphus/plans/http-tool.md`
  - Changed implementation areas: `base/common-1.20.1/src/main/java/space/controlnet/mineagent/common/tools/http/`, renderer integration files, `REPO.md`

  **Acceptance Criteria**:
  - [ ] Reviewer reports no unauthorized scope additions.
  - [ ] Deferred items remain deferred and are documented as such.

  **QA Scenarios**:
  ```
  Scenario: Deep scope audit confirms no unauthorized feature expansion
    Tool: task (deep)
    Steps: Compare implemented files and behavior against the plan's IN/OUT scope boundaries.
    Expected: Reviewer approves with no hidden additions such as cookie jars, streaming support, MCP rewrites, or security-framework expansion.
    Evidence: .sisyphus/evidence/f4-http-scope-fidelity.md

  Scenario: Deferred items remain explicitly deferred
    Tool: task (deep)
    Steps: Re-check code and `REPO.md` for accidental implementation of deferred security or transport features.
    Expected: Any unauthorized scope expansion is flagged and must be removed before approval.
    Evidence: .sisyphus/evidence/f4-http-scope-fidelity-error.md
  ```

  **Commit**: NO | Message: `n/a` | Files: none

## Commit Strategy
- Commit 1: `feat(http): add common-layer tool contract and registration`
- Commit 2: `feat(http): implement request execution and response envelope`
- Commit 3: `test(http): cover redirects timeouts rendering and edge cases`
- Commit 4: `docs(repo): document http tool behavior and limits`

## Success Criteria
- `http` appears in tool listings with a complete, stable prompt contract.
- `http` executes entirely on `CALLING_THREAD` and never requires a Minecraft server thread hop.
- Duplicate query params and headers survive request construction and response reporting without silent collapse.
- HTTP statuses are surfaced as data, not tool execution failures.
- Transport/runtime failures return exact stable error codes/messages and structured payloads.
- Output is readable in UI/session history with deterministic rendering.
- `REPO.md` accurately documents the new tool and all agent-executable verification commands.
