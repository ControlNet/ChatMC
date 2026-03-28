# P0 Stability Hardening Plan (ChatMC)

## TL;DR

> **Quick Summary**: This plan resolves four P0 reliability risks in ChatMC: state-machine mismatch, sticky INDEXING sessions, unsafe tool-thread execution, and oversized tool-arg serialization failures.
>
> **Deliverables**:
> - Deterministic request/proposal state transitions
> - Non-sticky indexing behavior (no permanent INDEXING lock)
> - Server-thread confinement for MC/AE2 tool mutations
> - Unified high tool-arg limit policy with safe boundary handling (`65536`)
>
> **Estimated Effort**: Medium
> **Parallel Execution**: YES — 3 implementation waves + final verification wave
> **Critical Path**: T1 -> T6 -> T7 -> T10 -> T14

---

## Context

### Original Request
User requested a focused P0 plan first and explicitly asked for a high args limit (suggested `65536`) for tool-call payload handling.

### Interview Summary
**Key Discussions**:
- Scope is constrained to P0 only (no P1/P2 feature work in this iteration).
- Priority is reliability and deterministic behavior under multiplayer/server conditions.
- User preference for item #4: limit should be high; target value set to `65536`.

**Research Findings**:
- `ServerSessionManager.trySetProposal(...)` currently requires `EXECUTING`.
- `ChatMCNetwork` starts request flow with `tryStartThinking(...)`.
- `ensureIndexingStateIfNeeded(...)` persists `INDEXING` into session snapshots.
- `writeSnapshot(...)` serializes proposal args with `writeUtf(..., 2048)`.
- `AgentLoop.executeNode(...)` calls tool execution while loop is run in async executor path.

### Metis Review
**Identified Gaps** (addressed in this plan):
- Missing explicit transition contract for first-pass proposal lifecycle.
- Missing recovery path from INDEXING after index readiness flips true.
- Missing explicit thread-boundary enforcement for tool execution.
- Missing end-to-end arg-size policy consistency across parse/store/serialize boundaries.

---

## Work Objectives

### Core Objective
Stabilize ChatMC’s P0 execution path so proposal workflows, session availability, tool execution safety, and serialization boundaries are deterministic and resilient under multiplayer/server runtime conditions.

### Concrete Deliverables
- Unified transition contract covering initial request and approval-resume flows.
- INDEXING behavior changed to non-sticky runtime status.
- Tool execution path constrained to safe server-thread execution for MC/AE2 mutations.
- Shared arg-size policy with `65536` limit and deterministic overflow handling.

### Definition of Done
- [x] Proposal path succeeds from first request without silent fallback to IDLE.
- [x] Sessions do not remain stuck in INDEXING once recipe index is ready.
- [x] Tool execution path touching MC/AE2 runs in server-safe context only.
- [x] Oversized args cannot crash snapshot persistence/network serialization.

### Must Have
- Deterministic state transitions for THINKING/WAIT_APPROVAL/EXECUTING/DONE/FAILED.
- Strict and test-verified boundary policy for tool args using high cap `65536`.
- No regressions to approval security semantics.

### Must NOT Have (Guardrails)
- No scope expansion into P1/P2 UX redesign or new tool features.
- No broad rewrite of extension architecture in this P0 pass.
- No human-only validation steps in acceptance criteria.

---

## Verification Strategy (MANDATORY)

> **ZERO HUMAN INTERVENTION** — all verification is command/tool executed.

### Test Decision
- **Infrastructure exists**: YES (Gradle multi-module Java tests)
- **Automated tests**: YES (Tests-after, default)
- **Framework**: Gradle test tasks (JUnit-based module tests)
- **Policy default applied**: `maxToolArgs = 65536` (character-based contract unless overridden later)

### QA Policy
Every task includes agent-executed scenarios with evidence artifacts:
- `.sisyphus/evidence/task-{N}-{scenario}.log|json|png`

---

## Execution Strategy

### Parallel Execution Waves

Wave 1 (Foundation contracts — start immediately):
- T1 State transition contract + invariants
- T2 Indexing-state policy contract
- T3 Tool-thread confinement contract
- T4 Arg-size policy contract (`65536`)
- T5 Test harness map for affected modules

Wave 2 (Core implementation — after Wave 1):
- T6 Implement transition fixes in session manager
- T7 Align network proposal lifecycle with new transitions
- T8 Implement INDEXING recovery/non-sticky behavior
- T9 Enforce server-thread tool execution path
- T10 Apply args-size validation at parse boundary
- T11 Apply args-size validation at persistence/network boundaries

Wave 3 (Regression-proofing — after Wave 2):
- T12 State-machine regression tests
- T13 INDEXING recovery tests
- T14 Tool-thread confinement tests (base + AE)
- T15 Args boundary tests (max, max+1, UTF-8 edge)
- T16 Cross-loader/module build verification

Wave FINAL (Independent review, parallel):
- F1 Plan compliance audit
- F2 Code quality review
- F3 Real QA scenario replay
- F4 Scope fidelity check

### Dependency Matrix
- T1: none -> T6, T7
- T2: none -> T8, T13
- T3: none -> T9, T14
- T4: none -> T10, T11, T15
- T5: none -> T12-T16
- T6: T1 -> T7, T12
- T7: T1, T6 -> T12
- T8: T2 -> T13
- T9: T3 -> T14
- T10: T4 -> T15
- T11: T4 -> T15
- T12: T5, T6, T7 -> F1-F4
- T13: T5, T8 -> F1-F4
- T14: T5, T9 -> F1-F4
- T15: T5, T10, T11 -> F1-F4
- T16: T5, T12-T15 -> F1-F4

### Agent Dispatch Summary
- Wave 1: T1-T5 -> `deep`/`quick` mixed
- Wave 2: T6-T11 -> `deep`/`unspecified-high`
- Wave 3: T12-T16 -> `deep`/`quick`/`unspecified-high`
- Final: F1-F4 -> `oracle`/`unspecified-high`/`deep`

---

## TODOs

- [x] 1. Define canonical session transition contract (P0)

  **What to do**:
  - Document legal transitions for request lifecycle: `IDLE/DONE/FAILED -> THINKING -> WAIT_APPROVAL -> EXECUTING -> DONE/FAILED`.
  - Specify illegal/reentrant transitions and expected no-op behavior.
  - Define canonical behavior for first-pass proposal path (no silent drop).

  **Must NOT do**:
  - Do not introduce queueing/multi-inflight behavior.
  - Do not change session visibility or ownership semantics.

  **Recommended Agent Profile**:
  - **Category**: `deep`
    - Reason: state-machine correctness and invariant design.
  - **Skills**: [`beads`]
    - `beads`: track sub-decisions and ensure full invariant coverage.
  - **Skills Evaluated but Omitted**:
    - `playwright`: no browser work required for this task.

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1 (T1-T5)
  - **Blocks**: T6, T7
  - **Blocked By**: None

  **References**:
  - `base/core/src/main/java/space/controlnet/chatmc/core/session/ServerSessionManager.java:210-330` - current transition entry points (`tryStartThinking`, `trySetProposal`, `tryStartExecuting`, `tryResolveExecution`).
  - `base/common-1.20.1/src/main/java/space/controlnet/chatmc/common/ChatMCNetwork.java:554-597,751-780` - request start and result application path.
  - `base/core/src/main/java/space/controlnet/chatmc/core/session/SessionState.java` - state enum contract.

  **Acceptance Criteria**:
  - [ ] Transition table exists and maps every current P0 path.
  - [ ] First-pass proposal flow is explicitly represented and unambiguous.
  - [ ] Illegal transitions define deterministic no-op/error outcomes.

  **QA Scenarios**:
  ```
  Scenario: Transition table covers all lifecycle states
    Tool: Bash
    Preconditions: Plan contract artifact committed in code comments/tests/docs as defined by implementation task
    Steps:
      1. Run ./gradlew :base:core:test --tests "*Session*"
      2. Verify tests exercising legal transitions are present and passing
    Expected Result: Transition tests pass with explicit legal path assertions
    Failure Indicators: Missing transition assertions, failing tests, undefined path
    Evidence: .sisyphus/evidence/task-1-transition-contract.log

  Scenario: Illegal transition rejected deterministically
    Tool: Bash
    Preconditions: Same test suite
    Steps:
      1. Run ./gradlew :base:core:test --tests "*Session*Illegal*"
      2. Confirm illegal transitions return false/no-op as specified
    Expected Result: Illegal path tests pass; no state mutation on invalid transition
    Evidence: .sisyphus/evidence/task-1-transition-illegal.log
  ```

- [x] 2. Define INDEXING state policy as non-sticky runtime behavior

  **What to do**:
  - Define that INDEXING is derived from index readiness, not a persistent terminal dead-end.
  - Specify explicit recovery behavior once index becomes ready.
  - Specify whether non-recipe interactions are blocked or allowed while indexing.

  **Must NOT do**:
  - Do not keep sessions permanently in `INDEXING` after readiness flips true.
  - Do not alter recipe search semantics beyond readiness gating.

  **Recommended Agent Profile**:
  - **Category**: `deep`
    - Reason: cross-boundary behavior between session state and recipe subsystem.
  - **Skills**: [`beads`]
    - `beads`: maintain traceability from policy to implementation tests.
  - **Skills Evaluated but Omitted**:
    - `frontend-ui-ux`: no UI redesign required.

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1
  - **Blocks**: T8, T13
  - **Blocked By**: None

  **References**:
  - `base/common-1.20.1/src/main/java/space/controlnet/chatmc/common/ChatMCNetwork.java:417-425,547-550` - current INDEXING assignment points.
  - `base/core/src/main/java/space/controlnet/chatmc/core/recipes/RecipeIndexManager.java:22-24,50-60` - readiness and rebuild behavior.
  - `base/core/src/main/java/space/controlnet/chatmc/core/session/ServerSessionManager.java:376-378` - idle-like gating currently excludes INDEXING.

  **Acceptance Criteria**:
  - [ ] INDEXING policy includes deterministic recovery path.
  - [ ] Session availability after index-ready is explicitly defined.
  - [ ] Behavior is covered by automated tests.

  **QA Scenarios**:
  ```
  Scenario: Session recovers after index ready
    Tool: Bash
    Preconditions: Tests for index readiness transitions implemented
    Steps:
      1. Run ./gradlew :base:common-1.20.1:test --tests "*Indexing*"
      2. Verify INDEXING -> IDLE/DONE recovery assertion exists and passes
    Expected Result: Recovery tests pass; no sticky INDEXING state
    Failure Indicators: Session remains INDEXING after readiness true
    Evidence: .sisyphus/evidence/task-2-indexing-recovery.log

  Scenario: Indexing gate enforces intended block scope
    Tool: Bash
    Preconditions: Policy tests for blocked vs allowed interactions
    Steps:
      1. Run ./gradlew :base:common-1.20.1:test --tests "*Indexing*Gate*"
      2. Validate expected allow/block matrix
    Expected Result: Gate behavior matches documented policy
    Evidence: .sisyphus/evidence/task-2-indexing-gate.log
  ```

- [x] 3. Define strict thread-boundary contract for tool execution

  **What to do**:
  - Specify that LLM reasoning may run async, but MC/AE2 mutations must execute in server-safe context.
  - Define execution handoff contract for `executeTool` path.
  - Define timeout/error behavior when server-thread handoff fails.

  **Must NOT do**:
  - Do not permit direct world/AE2 mutation from background LLM executor threads.
  - Do not change tool risk policy semantics.

  **Recommended Agent Profile**:
  - **Category**: `deep`
    - Reason: concurrency correctness under game-server constraints.
  - **Skills**: [`beads`]
    - `beads`: track thread-safety acceptance checks.
  - **Skills Evaluated but Omitted**:
    - `git-master`: no git operation focus in this task.

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1
  - **Blocks**: T9, T14
  - **Blocked By**: None

  **References**:
  - `base/common-1.20.1/src/main/java/space/controlnet/chatmc/common/ChatMCNetwork.java:744-749` - async loop execution entry.
  - `base/core/src/main/java/space/controlnet/chatmc/core/agent/AgentLoop.java:385-427` - tool execution node.
  - `base/common-1.20.1/src/main/java/space/controlnet/chatmc/common/agent/AgentRunner.java:152-154` - current direct executeTool bridge.
  - `ext-ae/common-1.20.1/src/main/java/space/controlnet/chatmc/ae/common/part/AiTerminalPartOperations.java:155-176` - server execute usage pattern to reuse.

  **Acceptance Criteria**:
  - [ ] Thread-boundary rules documented and testable.
  - [ ] Execution handoff path defined for all mutable tool calls.
  - [ ] Failure behavior for handoff timeout/rejection is deterministic.

  **QA Scenarios**:
  ```
  Scenario: Mutable tool executes on server thread
    Tool: Bash
    Preconditions: Thread-assertion tests implemented
    Steps:
      1. Run ./gradlew :base:common-1.20.1:test --tests "*Thread*Tool*"
      2. Verify assertions confirm server-thread execution for mutable calls
    Expected Result: Thread confinement tests pass
    Failure Indicators: Test detects execution on LLM/background thread
    Evidence: .sisyphus/evidence/task-3-thread-happy.log

  Scenario: Handoff failure handled gracefully
    Tool: Bash
    Preconditions: Fault-injection test for handoff failure
    Steps:
      1. Run ./gradlew :base:common-1.20.1:test --tests "*Thread*Failure*"
      2. Validate stable error result without deadlock
    Expected Result: Graceful failure message and stable session state
    Evidence: .sisyphus/evidence/task-3-thread-failure.log
  ```

- [x] 4. Define unified high arg-size policy (`65536`) and overflow semantics

  **What to do**:
  - Set `maxToolArgs` policy to `65536` for P0 scope.
  - Apply policy coherently across parse/store/serialize boundaries.
  - Define deterministic overflow handling (default: reject with explicit error).

  **Must NOT do**:
  - Do not leave boundary limits inconsistent across layers.
  - Do not silently truncate without explicit policy and telemetry.

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
    - Reason: boundary-hardening across multiple layers.
  - **Skills**: [`beads`]
    - `beads`: ensure all boundaries share one policy constant.
  - **Skills Evaluated but Omitted**:
    - `secret-guard`: unrelated to args-size boundary logic.

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1
  - **Blocks**: T10, T11, T15
  - **Blocked By**: None

  **References**:
  - `base/core/src/main/java/space/controlnet/chatmc/core/agent/AgentReasoningService.java:433-456` - ToolCall args ingestion points.
  - `base/common-1.20.1/src/main/java/space/controlnet/chatmc/common/ChatMCNetwork.java:655,714` - proposal args serialization/deserialization cap points.
  - `base/common-1.20.1/src/main/java/space/controlnet/chatmc/common/session/ChatMCSessionsSavedData.java` - persisted snapshot/NBT boundary to align.
  - External: `https://docs.langchain4j.dev/tutorials/tools/` - structured tool-call practices supporting explicit arg contracts.

  **Acceptance Criteria**:
  - [ ] `65536` is used consistently where policy applies.
  - [ ] Overflow behavior is deterministic and test-covered.
  - [ ] No serialization crash on boundary test payloads.

  **QA Scenarios**:
  ```
  Scenario: Max-size args accepted
    Tool: Bash
    Preconditions: Boundary tests implemented with exactly 65536-size payload
    Steps:
      1. Run ./gradlew :base:common-1.20.1:test --tests "*ToolArgs*Boundary*"
      2. Verify max payload passes parse/store/serialize checks
    Expected Result: 65536 payload processed without exceptions
    Failure Indicators: FriendlyByteBuf/NBT exceptions, failed assertions
    Evidence: .sisyphus/evidence/task-4-args-max.log

  Scenario: Max+1 payload rejected deterministically
    Tool: Bash
    Preconditions: Overflow tests for 65537 payload
    Steps:
      1. Run ./gradlew :base:common-1.20.1:test --tests "*ToolArgs*Overflow*"
      2. Verify explicit error path and stable session behavior
    Expected Result: Overflow rejected with stable error code/message
    Evidence: .sisyphus/evidence/task-4-args-overflow.log
  ```

- [x] 5. Build P0 test harness map and fixture plan

  **What to do**:
  - Map which modules host each P0 test category (`base:core`, `base:common-1.20.1`, `ext-ae:common-1.20.1`).
  - Create fixture strategy for session snapshots, indexing readiness toggles, and oversized payload generation.
  - Define evidence naming conventions for all P0 scenarios.

  **Must NOT do**:
  - Do not defer test scaffolding until after implementation completion.
  - Do not create tests that require manual in-game validation.

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: test scaffolding and wiring definition.
  - **Skills**: [`beads`]
    - `beads`: track fixture coverage and avoid missing branches.
  - **Skills Evaluated but Omitted**:
    - `playwright`: UI browser automation not required here.

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1
  - **Blocks**: T12-T16
  - **Blocked By**: None

  **References**:
  - `base/core/src/main/java/space/controlnet/chatmc/core/session/ServerSessionManager.java` - state transition fixture inputs.
  - `base/common-1.20.1/src/main/java/space/controlnet/chatmc/common/ChatMCNetwork.java` - network flow fixtures and boundary paths.
  - `ext-ae/common-1.20.1/src/main/java/space/controlnet/chatmc/ae/common/tools/AeToolProvider.java` - AE tool path coverage.

  **Acceptance Criteria**:
  - [ ] Test placement matrix completed and mapped to tasks T12-T15.
  - [ ] Fixture strategy covers both happy and failure branches for each P0 item.
  - [ ] Evidence artifact schema defined and reused across tasks.

  **QA Scenarios**:
  ```
  Scenario: Test matrix completeness check
    Tool: Bash
    Preconditions: Test matrix document/checklist created in repository
    Steps:
      1. Run ./gradlew :base:core:test :base:common-1.20.1:test :ext-ae:common-1.20.1:test
      2. Verify targeted test suites exist for P0-1..P0-4
    Expected Result: All mapped suites execute; no missing P0 category
    Failure Indicators: Missing suite names or empty test classes
    Evidence: .sisyphus/evidence/task-5-matrix.log

  Scenario: Fixture failure path coverage
    Tool: Bash
    Preconditions: Fault fixtures included
    Steps:
      1. Run ./gradlew :base:common-1.20.1:test --tests "*Failure*"
      2. Confirm failure fixtures run and assert expected error paths
    Expected Result: Failure fixtures pass with deterministic outcomes
    Evidence: .sisyphus/evidence/task-5-fixture-failure.log
  ```

- [x] 6. Implement session transition fixes in `ServerSessionManager`

  **What to do**:
  - Update transition guards to allow canonical first-pass proposal lifecycle.
  - Ensure invalid transition attempts remain deterministic no-op/false.
  - Preserve load-time normalization behavior while aligning with new contract.

  **Must NOT do**:
  - Do not alter unrelated metadata/message retention behavior.
  - Do not weaken ownership/visibility access logic.

  **Recommended Agent Profile**:
  - **Category**: `deep`
    - Reason: central state-machine mutation logic.
  - **Skills**: [`beads`]
    - `beads`: maintain transition checklist and regression tracking.
  - **Skills Evaluated but Omitted**:
    - `frontend-design`: no UI work.

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 2
  - **Blocks**: T7, T12
  - **Blocked By**: T1

  **References**:
  - `base/core/src/main/java/space/controlnet/chatmc/core/session/ServerSessionManager.java:210-330` - transition mutation functions.
  - `base/core/src/main/java/space/controlnet/chatmc/core/session/ServerSessionManager.java:348-373` - load normalization constraints.

  **Acceptance Criteria**:
  - [ ] First proposal path can be persisted through legal transition chain.
  - [ ] Illegal transition behavior remains deterministic and testable.
  - [ ] Existing load-normalization semantics remain valid.

  **QA Scenarios**:
  ```
  Scenario: First proposal path succeeds
    Tool: Bash
    Preconditions: Transition tests implemented
    Steps:
      1. Run ./gradlew :base:core:test --tests "*StateMachine*FirstProposal*"
      2. Verify THINKING -> WAIT_APPROVAL path passes
    Expected Result: Proposal transition test passes
    Failure Indicators: path rejected or state reverts unexpectedly
    Evidence: .sisyphus/evidence/task-6-first-proposal.log

  Scenario: Invalid transition no-op
    Tool: Bash
    Preconditions: invalid-state tests present
    Steps:
      1. Run ./gradlew :base:core:test --tests "*StateMachine*Invalid*"
      2. Confirm return false and unchanged snapshot
    Expected Result: No-op behavior asserted and passing
    Evidence: .sisyphus/evidence/task-6-invalid.log
  ```

- [x] 7. Align `ChatMCNetwork` proposal lifecycle with updated transition contract

  **What to do**:
  - Ensure request start/result handling uses legal transition sequence from T1/T6.
  - Remove fallback behavior that silently drops proposal path into IDLE.
  - Preserve locale and viewer broadcast semantics while fixing transition order.

  **Must NOT do**:
  - Do not change packet protocol schema in this task.
  - Do not change approval authorization semantics.

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
    - Reason: high-impact orchestration logic with multiple side effects.
  - **Skills**: [`beads`]
    - `beads`: track side effects (persist/broadcast/locale) in one checklist.
  - **Skills Evaluated but Omitted**:
    - `github-cli`: no remote GitHub inspection required.

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 2
  - **Blocks**: T12
  - **Blocked By**: T1, T6

  **References**:
  - `base/common-1.20.1/src/main/java/space/controlnet/chatmc/common/ChatMCNetwork.java:554-597` - request start flow.
  - `base/common-1.20.1/src/main/java/space/controlnet/chatmc/common/ChatMCNetwork.java:751-780` - result-state application.
  - `base/common-1.20.1/src/main/java/space/controlnet/chatmc/common/ChatMCNetwork.java:1089-1197` - approval resume flow.

  **Acceptance Criteria**:
  - [ ] Proposal no longer silently drops due to mismatched transition state.
  - [ ] Persist/broadcast behavior remains consistent on success and failure.
  - [ ] Approval resume path still works with locale continuity.

  **QA Scenarios**:
  ```
  Scenario: Proposal survives end-to-end network lifecycle
    Tool: Bash
    Preconditions: network lifecycle tests implemented
    Steps:
      1. Run ./gradlew :base:common-1.20.1:test --tests "*Network*ProposalLifecycle*"
      2. Verify proposal appears in snapshot and transitions to WAIT_APPROVAL
    Expected Result: Lifecycle test passes without fallback to IDLE
    Failure Indicators: proposal missing, unexpected state rollback
    Evidence: .sisyphus/evidence/task-7-lifecycle.log

  Scenario: Error path remains stable
    Tool: Bash
    Preconditions: injected agent error test
    Steps:
      1. Run ./gradlew :base:common-1.20.1:test --tests "*Network*AgentError*"
      2. Verify error message, state, and broadcast consistency
    Expected Result: Deterministic FAILED/error handling
    Evidence: .sisyphus/evidence/task-7-error.log
  ```

- [x] 8. Implement non-sticky INDEXING behavior and readiness recovery

  **What to do**:
  - Modify INDEXING handling to avoid durable lock conditions.
  - Ensure readiness flip triggers usable session state (`IDLE`/policy-defined equivalent).
  - Keep recipe-not-ready feedback visible without blocking forever.

  **Must NOT do**:
  - Do not remove indexing feedback entirely.
  - Do not alter recipe query correctness.

  **Recommended Agent Profile**:
  - **Category**: `deep`
    - Reason: cross-module interplay (network + recipe index + session state).
  - **Skills**: [`beads`]
    - `beads`: verify every readiness transition branch.
  - **Skills Evaluated but Omitted**:
    - `frontend-ui-ux`: this task is behavior/state, not visual redesign.

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 2
  - **Blocks**: T13
  - **Blocked By**: T2

  **References**:
  - `base/common-1.20.1/src/main/java/space/controlnet/chatmc/common/ChatMCNetwork.java:417-425,547-550` - current sticky assignment points.
  - `base/core/src/main/java/space/controlnet/chatmc/core/recipes/RecipeIndexManager.java:22-60` - readiness semantics.
  - `base/core/src/main/java/space/controlnet/chatmc/core/session/SessionOrchestrator.java:162-168` - parallel indexing-state helper to keep behavior aligned.

  **Acceptance Criteria**:
  - [ ] No persisted session remains stuck in INDEXING after readiness true.
  - [ ] Session can accept new request post-recovery.
  - [ ] Recipe-not-ready user feedback remains intact.

  **QA Scenarios**:
  ```
  Scenario: Readiness flip recovers session availability
    Tool: Bash
    Preconditions: indexing recovery tests implemented
    Steps:
      1. Run ./gradlew :base:common-1.20.1:test --tests "*Indexing*Recovery*"
      2. Assert session transitions out of INDEXING and accepts next request
    Expected Result: Recovery path passes
    Failure Indicators: session remains blocked
    Evidence: .sisyphus/evidence/task-8-recovery.log

  Scenario: Recipe-not-ready still communicated
    Tool: Bash
    Preconditions: not-ready UX/response tests implemented
    Steps:
      1. Run ./gradlew :base:common-1.20.1:test --tests "*Indexing*NotReady*"
      2. Verify not-ready status is surfaced without deadlock
    Expected Result: Clear not-ready signal and stable state
    Evidence: .sisyphus/evidence/task-8-notready.log
  ```

- [x] 9. Enforce server-thread tool execution path for MC/AE mutations

  **What to do**:
  - Implement execution handoff so mutable tool calls execute in server-safe context.
  - Keep reasoning loop async but isolate mutation path.
  - Ensure timeout/error propagation is deterministic when handoff fails.

  **Must NOT do**:
  - Do not run MC/AE mutation calls directly on LLM executor threads.
  - Do not weaken tool approval checks.

  **Recommended Agent Profile**:
  - **Category**: `deep`
    - Reason: concurrency correctness with safety-critical side effects.
  - **Skills**: [`beads`]
    - `beads`: track thread-boundary guarantees and failure paths.
  - **Skills Evaluated but Omitted**:
    - `playwright`: not a browser/UI automation task.

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 2
  - **Blocks**: T14
  - **Blocked By**: T3

  **References**:
  - `base/common-1.20.1/src/main/java/space/controlnet/chatmc/common/ChatMCNetwork.java:744-749` - async entry where loop is launched.
  - `base/core/src/main/java/space/controlnet/chatmc/core/agent/AgentLoop.java:385-427` - direct tool execution node.
  - `base/common-1.20.1/src/main/java/space/controlnet/chatmc/common/agent/AgentRunner.java:152-154` - current bridge to ToolRegistry.
  - `ext-ae/common-1.20.1/src/main/java/space/controlnet/chatmc/ae/common/part/AiTerminalPartOperations.java:155-176` - known server-execute pattern for safe mutation.

  **Acceptance Criteria**:
  - [ ] Mutable tool execution occurs only in server-safe context.
  - [ ] Async reasoning path remains non-blocking for server tick thread.
  - [ ] Handoff timeout/failure produces controlled errors, no deadlock.

  **QA Scenarios**:
  ```
  Scenario: AE mutation call respects server-thread confinement
    Tool: Bash
    Preconditions: thread assertions and mocks added
    Steps:
      1. Run ./gradlew :ext-ae:common-1.20.1:test --tests "*AeTool*Thread*"
      2. Verify mutation path asserts server-thread execution
    Expected Result: Thread-confinement assertions pass
    Failure Indicators: assertion indicates background-thread mutation
    Evidence: .sisyphus/evidence/task-9-ae-thread.log

  Scenario: Handoff timeout handled gracefully
    Tool: Bash
    Preconditions: timeout injection test available
    Steps:
      1. Run ./gradlew :base:common-1.20.1:test --tests "*ToolExecution*Timeout*"
      2. Confirm deterministic error and stable session state
    Expected Result: Timeout path returns controlled error, no hang
    Evidence: .sisyphus/evidence/task-9-timeout.log
  ```

- [x] 10. Apply `65536` args validation at LLM parse boundary

  **What to do**:
  - Validate tool-call args size when parsing model output.
  - Reject overflow payloads with explicit error code/message.
  - Ensure model-output logging/audit still records boundary outcome.

  **Must NOT do**:
  - Do not accept unlimited args into ToolCall objects.
  - Do not silently discard malformed/oversized calls.

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
    - Reason: parsing robustness and failure-mode determinism.
  - **Skills**: [`beads`]
    - `beads`: track edge-case coverage for parser branches.
  - **Skills Evaluated but Omitted**:
    - `frontend-design`: no UI design scope.

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 2
  - **Blocks**: T15
  - **Blocked By**: T4

  **References**:
  - `base/core/src/main/java/space/controlnet/chatmc/core/agent/AgentReasoningService.java:407-456` - tool call extraction and args capture.
  - `base/core/src/main/java/space/controlnet/chatmc/core/tools/ToolCall.java` - target object receiving args payload.
  - `base/core/src/main/java/space/controlnet/chatmc/core/audit/LlmAuditEvent.java` - audit path to annotate boundary outcomes.

  **Acceptance Criteria**:
  - [ ] Payloads up to 65536 are accepted at parse boundary.
  - [ ] Payloads >65536 are rejected deterministically.
  - [ ] Rejection path is visible in audit/log output.

  **QA Scenarios**:
  ```
  Scenario: Parser accepts boundary payload
    Tool: Bash
    Preconditions: parser boundary tests implemented
    Steps:
      1. Run ./gradlew :base:core:test --tests "*ReasoningService*ArgsBoundary*"
      2. Assert 65536 payload yields valid ToolCall
    Expected Result: Boundary acceptance test passes
    Failure Indicators: parse failure for valid max payload
    Evidence: .sisyphus/evidence/task-10-boundary.log

  Scenario: Parser rejects overflow payload with explicit reason
    Tool: Bash
    Preconditions: overflow parser tests implemented
    Steps:
      1. Run ./gradlew :base:core:test --tests "*ReasoningService*ArgsOverflow*"
      2. Verify explicit overflow error code/message
    Expected Result: Deterministic overflow rejection path
    Evidence: .sisyphus/evidence/task-10-overflow.log
  ```

- [x] 11. Apply `65536` args validation at persistence/network boundaries

  **What to do**:
  - Align snapshot serialization/deserialization and SavedData persistence with the shared args policy.
  - Ensure proposal payload encoding cannot crash due to oversized args.
  - Preserve backward-safe behavior for existing normal-sized sessions.

  **Must NOT do**:
  - Do not leave network and persistence limits mismatched.
  - Do not break protocol compatibility for valid payloads.

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
    - Reason: cross-boundary serialization hardening.
  - **Skills**: [`beads`]
    - `beads`: verify all serialization entry/exit points are covered.
  - **Skills Evaluated but Omitted**:
    - `dev-browser`: no browser workflow.

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 2
  - **Blocks**: T15
  - **Blocked By**: T4

  **References**:
  - `base/common-1.20.1/src/main/java/space/controlnet/chatmc/common/ChatMCNetwork.java:619-742` - snapshot read/write boundaries including proposal args.
  - `base/common-1.20.1/src/main/java/space/controlnet/chatmc/common/session/ChatMCSessionsSavedData.java` - NBT persistence path.
  - `base/core/src/main/java/space/controlnet/chatmc/core/session/PersistedSessions.java` - stored session payload structure.

  **Acceptance Criteria**:
  - [ ] No serialization exception on max-valid payload.
  - [ ] Overflow payload handled deterministically without session corruption.
  - [ ] Existing valid sessions remain readable.

  **QA Scenarios**:
  ```
  Scenario: Snapshot encode/decode with max-valid args
    Tool: Bash
    Preconditions: serialization boundary tests implemented
    Steps:
      1. Run ./gradlew :base:common-1.20.1:test --tests "*Snapshot*ArgsBoundary*"
      2. Verify roundtrip with 65536 args payload succeeds
    Expected Result: Roundtrip success, no exceptions
    Failure Indicators: FriendlyByteBuf write/read failure
    Evidence: .sisyphus/evidence/task-11-roundtrip.log

  Scenario: Overflow payload does not crash persistence path
    Tool: Bash
    Preconditions: overflow persistence tests implemented
    Steps:
      1. Run ./gradlew :base:common-1.20.1:test --tests "*SavedData*ArgsOverflow*"
      2. Confirm overflow rejected/handled with stable session state
    Expected Result: Controlled rejection; no corrupted saved snapshot
    Evidence: .sisyphus/evidence/task-11-persist-overflow.log
  ```

- [x] 12. Add regression suite for session state machine and proposal lifecycle

  **What to do**:
  - Add focused tests for legal/illegal transitions and first-pass proposal flow.
  - Cover approval resume interaction with updated transitions.
  - Assert no regression on load normalization behavior.

  **Must NOT do**:
  - Do not leave critical transition branches untested.
  - Do not rely on manual in-game assertions.

  **Recommended Agent Profile**:
  - **Category**: `deep`
    - Reason: high-coverage logic testing around state machine correctness.
  - **Skills**: [`beads`]
    - `beads`: maintain explicit branch coverage checklist.
  - **Skills Evaluated but Omitted**:
    - `visual-engineering`: not relevant to backend logic tests.

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 3
  - **Blocks**: T16, F1-F4
  - **Blocked By**: T5, T6, T7

  **References**:
  - `base/core/src/main/java/space/controlnet/chatmc/core/session/ServerSessionManager.java` - target transition logic.
  - `base/common-1.20.1/src/main/java/space/controlnet/chatmc/common/ChatMCNetwork.java:751-780,1089-1197` - proposal + approval-resume path.

  **Acceptance Criteria**:
  - [ ] Transition branch tests pass for legal and illegal paths.
  - [ ] Proposal lifecycle path is regression-protected.
  - [ ] Approval resume transition behavior remains valid.

  **QA Scenarios**:
  ```
  Scenario: Full proposal lifecycle regression suite
    Tool: Bash
    Preconditions: lifecycle tests present
    Steps:
      1. Run ./gradlew :base:core:test --tests "*StateMachine*" :base:common-1.20.1:test --tests "*ProposalLifecycle*"
      2. Verify all lifecycle suites pass
    Expected Result: No transition regression failures
    Failure Indicators: proposal path or approval-resume test failures
    Evidence: .sisyphus/evidence/task-12-lifecycle-regression.log

  Scenario: Load-normalization regression
    Tool: Bash
    Preconditions: normalization test fixture with THINKING/EXECUTING snapshots
    Steps:
      1. Run ./gradlew :base:core:test --tests "*NormalizeOnLoad*"
      2. Verify normalization results remain expected
    Expected Result: Normalization tests pass
    Evidence: .sisyphus/evidence/task-12-normalize.log
  ```

- [x] 13. Add INDEXING recovery regression tests

  **What to do**:
  - Add tests covering not-ready entry state and ready-flip recovery behavior.
  - Verify chat acceptance resumes after recovery.
  - Verify INDEXING does not become a persistent lock in saved sessions.

  **Must NOT do**:
  - Do not test only happy path; include stale/index-failure branches.
  - Do not rely on external manual verification.

  **Recommended Agent Profile**:
  - **Category**: `deep`
    - Reason: state + readiness integration tests.
  - **Skills**: [`beads`]
    - `beads`: track readiness transition permutations.
  - **Skills Evaluated but Omitted**:
    - `playwright`: not required for server-side behavior validation.

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 3
  - **Blocks**: T16, F1-F4
  - **Blocked By**: T5, T8

  **References**:
  - `base/common-1.20.1/src/main/java/space/controlnet/chatmc/common/ChatMCNetwork.java:417-425,547-550` - entry points to test.
  - `base/core/src/main/java/space/controlnet/chatmc/core/recipes/RecipeIndexManager.java` - readiness semantics for fixtures.
  - `base/core/src/main/java/space/controlnet/chatmc/core/session/ServerSessionManager.java:376-378` - idle-like gating interaction.

  **Acceptance Criteria**:
  - [ ] Ready flip exits indexing lock condition.
  - [ ] Subsequent request passes `tryStartThinking` gate.
  - [ ] Persistence/regression tests pass.

  **QA Scenarios**:
  ```
  Scenario: Indexing recovery enables new requests
    Tool: Bash
    Preconditions: readiness toggle tests available
    Steps:
      1. Run ./gradlew :base:common-1.20.1:test --tests "*Indexing*Recovery*"
      2. Verify post-recovery request gate passes
    Expected Result: Recovery + request acceptance assertions pass
    Failure Indicators: request still rejected after ready state
    Evidence: .sisyphus/evidence/task-13-recovery-request.log

  Scenario: Persisted snapshot does not retain sticky indexing lock
    Tool: Bash
    Preconditions: save/load fixture tests implemented
    Steps:
      1. Run ./gradlew :base:common-1.20.1:test --tests "*Indexing*Persist*"
      2. Verify loaded snapshot can resume interaction
    Expected Result: No sticky INDEXING on reload
    Evidence: .sisyphus/evidence/task-13-persist.log
  ```

- [x] 14. Add thread-confinement regression tests (base + AE)

  **What to do**:
  - Add assertions that mutable tool paths execute in server-safe context.
  - Cover both base network-triggered flow and AE tool path.
  - Include failure test for handoff timeout/exception handling.

  **Must NOT do**:
  - Do not leave thread identity unasserted.
  - Do not assume AE path inherits safety without explicit test.

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
    - Reason: cross-module concurrency validation.
  - **Skills**: [`beads`]
    - `beads`: ensure both base and extension paths are covered.
  - **Skills Evaluated but Omitted**:
    - `frontend-design`: unrelated domain.

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 3
  - **Blocks**: T16, F1-F4
  - **Blocked By**: T5, T9

  **References**:
  - `base/core/src/main/java/space/controlnet/chatmc/core/agent/AgentLoop.java:385-427` - execution node under test.
  - `base/common-1.20.1/src/main/java/space/controlnet/chatmc/common/agent/AgentRunner.java:152-154` - execution bridge.
  - `ext-ae/common-1.20.1/src/main/java/space/controlnet/chatmc/ae/common/tools/AeToolProvider.java:146-176` - AE dispatch path.
  - `ext-ae/common-1.20.1/src/main/java/space/controlnet/chatmc/ae/common/part/AiTerminalPartOperations.java:155-176` - server execute mutation pattern.

  **Acceptance Criteria**:
  - [ ] Base mutable tool calls assert server-thread execution.
  - [ ] AE mutable tool calls assert server-thread execution.
  - [ ] Failure-injection path returns controlled error.

  **QA Scenarios**:
  ```
  Scenario: Base tool thread confinement suite
    Tool: Bash
    Preconditions: base thread tests available
    Steps:
      1. Run ./gradlew :base:common-1.20.1:test --tests "*Thread*Tool*"
      2. Confirm all confinement assertions pass
    Expected Result: No background-thread mutation path
    Failure Indicators: thread assertion failure
    Evidence: .sisyphus/evidence/task-14-base-thread.log

  Scenario: AE tool thread confinement suite
    Tool: Bash
    Preconditions: AE thread tests available
    Steps:
      1. Run ./gradlew :ext-ae:common-1.20.1:test --tests "*Ae*Thread*"
      2. Confirm AE mutation paths are server-thread constrained
    Expected Result: AE thread tests pass
    Evidence: .sisyphus/evidence/task-14-ae-thread.log
  ```

- [x] 15. Add args-size boundary regression tests (`65536`, `65537`, UTF edge)

  **What to do**:
  - Add boundary tests at parser, snapshot serialization, and persistence points.
  - Include multi-byte/UTF-heavy payload case to validate character-count policy behavior.
  - Verify explicit overflow error semantics.

  **Must NOT do**:
  - Do not validate only ASCII payloads.
  - Do not leave overflow behavior implicit.

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
    - Reason: boundary fuzzing and serialization correctness.
  - **Skills**: [`beads`]
    - `beads`: maintain complete matrix of boundary cases.
  - **Skills Evaluated but Omitted**:
    - `dev-browser`: not relevant.

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 3
  - **Blocks**: T16, F1-F4
  - **Blocked By**: T5, T10, T11

  **References**:
  - `base/core/src/main/java/space/controlnet/chatmc/core/agent/AgentReasoningService.java:433-456` - parse boundary.
  - `base/common-1.20.1/src/main/java/space/controlnet/chatmc/common/ChatMCNetwork.java:655,714` - network boundary.
  - `base/common-1.20.1/src/main/java/space/controlnet/chatmc/common/session/ChatMCSessionsSavedData.java` - persistence boundary.

  **Acceptance Criteria**:
  - [ ] Exactly-65536 payload path passes across all boundaries.
  - [ ] 65537 payload path fails deterministically with explicit error.
  - [ ] UTF-heavy boundary case behaves according to policy.

  **QA Scenarios**:
  ```
  Scenario: Max and UTF boundary matrix pass
    Tool: Bash
    Preconditions: boundary matrix tests implemented
    Steps:
      1. Run ./gradlew :base:core:test --tests "*Args*Boundary*" :base:common-1.20.1:test --tests "*Args*Boundary*"
      2. Verify max and UTF boundary cases pass
    Expected Result: Boundary matrix success
    Failure Indicators: serializer/parser boundary mismatch
    Evidence: .sisyphus/evidence/task-15-boundary-matrix.log

  Scenario: Overflow matrix rejects safely
    Tool: Bash
    Preconditions: overflow tests implemented
    Steps:
      1. Run ./gradlew :base:core:test --tests "*Args*Overflow*" :base:common-1.20.1:test --tests "*Args*Overflow*"
      2. Verify 65537+ payload rejected with explicit error
    Expected Result: Stable overflow rejection across layers
    Evidence: .sisyphus/evidence/task-15-overflow-matrix.log
  ```

- [x] 16. Run cross-module verification and evidence bundle assembly

  **What to do**:
  - Execute full targeted test/build suite after P0 changes.
  - Assemble all task evidence files into final verification index.
  - Confirm no accidental P1/P2 scope drift in changed files.

  **Must NOT do**:
  - Do not skip extension module verification.
  - Do not mark complete without evidence paths present.

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: deterministic execution of final command matrix and evidence collation.
  - **Skills**: [`beads`]
    - `beads`: completion checklist and evidence audit.
  - **Skills Evaluated but Omitted**:
    - `git-master`: commit strategy is separate and optional at this stage.

  **Parallelization**:
  - **Can Run In Parallel**: NO
  - **Parallel Group**: Wave 3 (terminal task)
  - **Blocks**: F1-F4
  - **Blocked By**: T5, T12, T13, T14, T15

  **References**:
  - `build.gradle`, `settings.gradle` - module graph and task names.
  - `base/forge-1.20.1/src/main/java/space/controlnet/chatmc/forge/ChatMCForge.java` - loader integration target.
  - `base/fabric-1.20.1/src/main/java/space/controlnet/chatmc/fabric/ChatMCFabric.java` - loader integration target.

  **Acceptance Criteria**:
  - [ ] Full command matrix passes for touched modules.
  - [ ] Evidence files exist for T1-T15.
  - [ ] No out-of-scope files changed.

  **QA Scenarios**:
  ```
  Scenario: Full verification command matrix passes
    Tool: Bash
    Preconditions: all implementation and test tasks completed
    Steps:
      1. Run ./gradlew :base:core:test :base:common-1.20.1:test :ext-ae:common-1.20.1:test
      2. Run ./gradlew :base:forge-1.20.1:build :base:fabric-1.20.1:build
    Expected Result: All tasks BUILD SUCCESSFUL
    Failure Indicators: any module test/build failure
    Evidence: .sisyphus/evidence/task-16-full-matrix.log

  Scenario: Evidence completeness and scope check
    Tool: Bash
    Preconditions: evidence files generated
    Steps:
      1. Validate evidence file presence for task-1 through task-16
      2. Inspect changed file list for out-of-scope modules/features
    Expected Result: Complete evidence set and clean P0-only scope
    Evidence: .sisyphus/evidence/task-16-evidence-scope.log
  ```

---

## Final Verification Wave (MANDATORY)

- [x] F1. **Plan Compliance Audit** — `oracle`
  - Validate each Must Have / Must NOT Have against resulting changes and evidence files.
  - Output: `Must Have [N/N] | Must NOT Have [N/N] | VERDICT`

- [x] F2. **Code Quality Review** — `unspecified-high`
  - Run static checks/tests and inspect touched files for unsafe shortcuts and regressions.
  - Output: `Build [PASS/FAIL] | Tests [PASS/FAIL] | VERDICT`

- [x] F3. **QA Scenario Replay** — `unspecified-high`
  - Execute all task-level QA scenarios and verify evidence completeness.
  - Output: `Scenarios [N/N] | Edge Cases [N/N] | VERDICT`

- [x] F4. **Scope Fidelity Check** — `deep`
  - Ensure all P0 scope is covered and no P1/P2 creep entered this plan.
  - Output: `Scope [CLEAN/ISSUES] | VERDICT`

---

## Commit Strategy

- Commit group A: T1-T5 (contracts/tests scaffolding)
- Commit group B: T6-T11 (core implementation)
- Commit group C: T12-T16 + verification artifacts

---

## Success Criteria

### Verification Commands
```bash
./gradlew :base:core:test :base:common-1.20.1:test :ext-ae:common-1.20.1:test
./gradlew :base:forge-1.20.1:build :base:fabric-1.20.1:build
```

### Final Checklist
- [x] Proposal lifecycle deterministic from first request
- [x] No sticky INDEXING sessions after index ready
- [x] Tool execution thread-boundary safety enforced
- [x] `65536` args policy enforced with deterministic overflow behavior
- [x] All automated checks pass
