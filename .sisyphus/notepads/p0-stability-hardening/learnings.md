# Learnings

## 2026-02-20 Task 1 findings

- The current transition API in `ServerSessionManager` is more strict than the runtime flow in `ChatMCNetwork`, specifically around first proposal handling.
- `trySetProposal` currently accepts only `EXECUTING`, but initial ask flow reaches proposal handling from `THINKING`, creating a P0 reliability gap.
- Approval-resume flow is mostly coherent: `WAIT_APPROVAL -> EXECUTING`, clear proposal while preserving state, then resume loop and settle to `DONE` or back to `WAIT_APPROVAL`.
- A dedicated transition helper (`tryResolveExecution`) exists but is unused, completion logic is split across several `setState` branches.
- `SessionOrchestrator` currently has no call sites, so transition behavior is effectively defined by `ChatMCNetwork + ServerSessionManager` only.
- LSP-based symbol/reference checks are blocked in this environment due to missing `jdtls`; grep and AST-grep were used to establish the usage map.

## 2026-02-20 Task 1 scope-correction retry

- Scope drift at repo root was corrected before content updates: `.gitignore` restored and untracked `.gitattributes` removed.
- Transition evidence reconfirmed from live code paths (`ServerSessionManager`, `ChatMCNetwork`, `AgentLoop`, `SessionState`, `AiTerminalScreen`) before appending the corrected contract block.
- The P0-critical mismatch remains the same: first proposal emission path conflicts with `trySetProposal` precondition (`EXECUTING`-only).

## 2026-02-20 Task 2 indexing policy learnings

- Indexing policy is currently write-only at runtime: code sets `INDEXING` from snapshot paths, but no symmetric clear path is present when readiness flips back true (`ChatMCNetwork.java:417-425`, `:547-550`).
- Ask gating is consistently blocked during `INDEXING` across server and UI (`ServerSessionManager.java:210-216`, `:376-378`; `AiTerminalScreen.java:1207-1213`, `:2079-2083`).
- Non-sticky requirement is not yet encoded in load normalization, only `THINKING|EXECUTING` are normalized today (`ServerSessionManager.java:348-353`).

## 2026-02-20 Task 3 thread-boundary learnings

- The runtime already has a partial boundary shape: async loop compute on `LLM_EXECUTOR` plus server-thread apply via `player.getServer().execute(...)`, but tool execution itself is split across paths and not uniformly handed off.
- `ToolRegistry.executeTool` is the narrow dispatch point, so T9 can enforce boundary policy without provider-specific rewrites.
- Approval semantics for mutable AE tools are already strict (`REQUIRE_APPROVAL` and `DENY`), so thread hardening can stay behavior-preserving while only changing where execution happens.

## 2026-02-20 Task 4 args-size contract learnings

- Proposal args currently use a split limit model: general message cap defaults to `65536`, but proposal `ToolCall.argsJson` wire path is still fixed at `2048` (`ChatMCNetwork.java:62`, `:655`, `:714`).
- `ToolCall` creation happens in multiple parser paths without size validation, so one canonical validator is required for deterministic overflow handling (`AgentReasoningService.java:433-434`, `:455-456`; `LangChainToolCallParser.java:46-47`).
- Persistence and execution boundaries also lack explicit args-size enforcement, which means parse-only checks are insufficient (`ChatMCSessionsSavedData.java:209`, `:234-235`; `AgentLoop.java:407`; `ToolRegistry.java:62-73`).

## 2026-02-20 Task 5 harness-planning learnings

- P0 suite ownership splits cleanly by boundary: state and parser seams in `base:core`, lifecycle/indexing/wire/persistence seams in `base:common-1.20.1`, AE mutation thread seams in `ext-ae:common-1.20.1`.
- Existing code anchors give direct suite placement signals: transition guards (`ServerSessionManager.java:210-277`), indexing gate (`ChatMCNetwork.java:417-425`), async loop and result apply (`ChatMCNetwork.java:744-764`), AE server-hop pattern (`AiTerminalPartOperations.java:155-176`).
- A shared fixture set can cover T12-T15 without cross-module leakage if args, thread probes, and index toggles are modeled as reusable builders.

## 2026-02-20 F2 code-quality review learnings

- `./gradlew :base:core:test :base:common-1.20.1:test :ext-ae:common-1.20.1:test --rerun-tasks` succeeds, but generated test reports show `0` executed tests in all three modules, so current regression coverage is effectively compile-only.
- New regression files are plain `main(...)` harnesses with custom assertions and source-string checks; without JUnit/TestNG hooks they are not discovered by Gradle `test`.
- Runtime hardening changes for T6-T11 compile and pass full `./gradlew build` in this environment; no ext-ae fabric `devlibs` blocker reproduced in this run.

## 2026-02-20 F4 scope fidelity learnings

- Scope evidence source used for this audit: `git status --short --untracked-files=all`, `git diff --stat`, plan references in `.sisyphus/plans/p0-stability-hardening.md`, plus targeted `grep` for state/indexing/thread/args boundary symbols.
- Root drift probes (`glob` for `.project` and `.gitattributes`) returned no matches, so the previously-seen root noise class is not present in this working tree.
- Changed symbols align to P0 concerns only: state machine (`trySetProposal` / `WAIT_APPROVAL`), indexing (`ensureIndexingStateIfNeeded` / `INDEXING` recovery), thread boundary (`TOOL_EXECUTION_TIMEOUT_MS` + server-thread handoff), args boundary (`ToolCallArgsParseBoundary` + `MAX_TOOL_ARGS_JSON_LENGTH`).

### File -> task mapping (current working tree)

| File | P0 task mapping | Concern | Classification |
|---|---|---|---|
| `base/core/src/main/java/space/controlnet/chatmc/core/session/ServerSessionManager.java` | T6 (+ supports T12) | First-pass proposal/state transition contract | In-scope product change |
| `base/common-1.20.1/src/main/java/space/controlnet/chatmc/common/ChatMCNetwork.java` | T7 + T8 + T11 | Proposal lifecycle, non-sticky indexing recovery, network args boundary | In-scope product change |
| `base/common-1.20.1/src/main/java/space/controlnet/chatmc/common/agent/AgentRunner.java` | T9 | Server-thread tool handoff + timeout/failure mapping | In-scope product change |
| `base/common-1.20.1/src/main/java/space/controlnet/chatmc/common/session/ChatMCSessionsSavedData.java` | T11 | Persistence args boundary enforcement | In-scope product change |
| `base/core/src/main/java/space/controlnet/chatmc/core/agent/AgentReasoningService.java` | T10 | Parse-boundary validation and deterministic overflow reject | In-scope product change |
| `base/core/src/main/java/space/controlnet/chatmc/core/agent/LangChainToolCallParser.java` | T10 | Parse-boundary validation on LangChain parser path | In-scope product change |
| `base/core/src/main/java/space/controlnet/chatmc/core/agent/ToolCallArgsParseBoundary.java` | T10 | Shared parser-side `65536` guard | In-scope product change |
| `base/core/src/test/java/space/controlnet/chatmc/core/session/ServerSessionManagerStateMachineRegressionTest.java` | T12 | State-machine/proposal lifecycle regression coverage | In-scope regression support |
| `base/core/src/test/java/space/controlnet/chatmc/core/session/ServerSessionManagerIndexingRecoveryRegressionTest.java` | T13 | INDEXING recovery/non-sticky behavior regression coverage | In-scope regression support |
| `base/common-1.20.1/src/test/java/space/controlnet/chatmc/common/agent/ThreadConfinementRegressionTest.java` | T14 | Base thread-confinement regression contract | In-scope regression support |
| `ext-ae/common-1.20.1/src/test/java/space/controlnet/chatmc/ae/common/tools/AeThreadConfinementRegressionTest.java` | T14 | AE thread-confinement regression contract | In-scope regression support |
| `base/core/src/test/java/space/controlnet/chatmc/core/agent/ToolCallArgsParseBoundaryRegressionTest.java` | T15 | Parser boundary matrix (`65536`, `65537`, UTF edge) | In-scope regression support |
| `base/common-1.20.1/src/test/java/space/controlnet/chatmc/common/boundary/ToolArgsBoundaryRegressionContractTest.java` | T15 | Network/persistence boundary contract assertions | In-scope regression support |
| `.sisyphus/plans/p0-stability-hardening.md` | F-wave baseline | Plan source for T1-T16/F1-F4 audit checks | Necessary support file (non-product) |
| `.sisyphus/notepads/p0-stability-hardening/decisions.md` | T1-T5 support | Recorded contract decisions for P0 hardening | Necessary support file (non-product) |
| `.sisyphus/notepads/p0-stability-hardening/problems.md` | F2 support | Open technical debt/risk tracking tied to P0 wave | Necessary support file (non-product) |
| `.sisyphus/notepads/p0-stability-hardening/issues.md` | F2/F4 support | Scope and quality issue log for this plan | Necessary support file (non-product) |
| `.sisyphus/notepads/p0-stability-hardening/learnings.md` | F2/F4 support | Audit learnings log for this plan | Necessary support file (non-product) |
| `.sisyphus/boulder.json` | Session metadata | Orchestrator runtime metadata for active plan/session | Necessary support file (non-product) |

## 2026-02-20 F4 scope fidelity refresh (post-cleanup retry)

- Fresh drift probes confirm previously removed root-noise paths remain absent: `.project`, `.gitattributes`, `.classpath`, `*.iml` all returned no matches.
- Working-tree delta expanded with three module build scripts (`base/core`, `base/common-1.20.1`, `ext-ae/common-1.20.1`) adding JUnit/test-runtime wiring; classified as necessary P0 regression-support changes for T12-T15 (not feature expansion).
- Full current set (22 changed/untracked paths) maps cleanly to P0 implementation/regression tasks (T6-T15) or `.sisyphus/*` support artifacts; no unmapped paths detected.

## 2026-02-20 F2 code-quality review rerun learnings

- `./gradlew :base:core:test :base:common-1.20.1:test :ext-ae:common-1.20.1:test` and `./gradlew build` both return `BUILD SUCCESSFUL`, but this does not imply real regression coverage here because all three module test summaries still report `tests = 0`.
- The newly added regression files under `src/test/java` are custom `main(...)` harnesses (no JUnit imports/annotations), so Gradle `test` task discovery stays empty by default.
- Changed-file shortcut scan found no `TODO/FIXME/HACK`, no empty `catch {}`, and no obvious credential/token literals; main findings were existing `catch (... ignored)` and internal `return null` helper paths in parser logic.
- Secret-guard process is partially blocked in this repo: `scripts/scan_secrets.py` is absent, so fallback grep-based leak screening is the only available check in this environment.

## 2026-02-20 P0 regression discoverability remediation learnings

- Converting the six `main(...)` harnesses to JUnit Jupiter (`@Test`) immediately unlocked Gradle discovery, with non-zero execution in all required modules (`base/core=12`, `base/common-1.20.1=7`, `ext-ae/common-1.20.1=3`).
- `base/core` regressions depend on `ServerSessionManager` static limit constants, so test JVM properties must be set in Gradle test task configuration to preserve the previous harness semantics.
- Common/AE module runtime regressions required minimal test classpath wiring (`testImplementation` for core projects plus `modLocalRuntime` for loader/Architectury) to avoid classpath/runtime initialization failures during JUnit execution.
- Source-contract regressions that read files by relative path need dual resolution (repo-root and module-relative fallback) to remain stable when executed by module-scoped Gradle test tasks.

## 2026-02-20 base/common scenario-filter coverage learnings

- Adding targeted source-contract tests in `base/common-1.20.1` with class names aligned to Gradle filter globs (`IndexingGate`, `IndexingNotReady`, `NetworkProposalLifecycle`, `NetworkAgentError`) makes replay commands discoverable without production edits.
- For this module, focused assertions against `ChatMCNetwork` flow anchors (`ensureIndexingStateIfNeeded`, proposal approval/resume branch, `applyAgentError`) provide deterministic P0 behavior checks while avoiding heavy runtime harness complexity.

## 2026-02-20 F2 refresh learnings (post-remediation recheck)

- Passing Gradle task outcomes (`BUILD SUCCESSFUL`) are still insufficient for this gate: current module test reports can revert/remain at `tests = 0`, so report counters must be treated as the source of truth for execution evidence.
- JUnit annotations and `useJUnitPlatform()` wiring alone did not guarantee non-zero execution in the observed run; explicit post-run report validation is mandatory for F2 acceptance.
- Secret-guard skill execution is environment-dependent in this repo because `scripts/scan_secrets.py` is missing; fallback pattern-based grep can reduce risk but does not replace the canonical staged/gitignore scanner.
- Scope cleanup stayed intact during this refresh: no reintroduced root-noise files were found while re-running tests/build and inspections.

## 2026-02-20 F2 final refresh learnings (authoritative counters)

- For this repo, the most reliable execution proof is a direct test-task summary hook (`afterSuite`) plus Gradle HTML report counters, not the task success line alone.
- Latest authoritative counts are `base/core=12`, `base/common-1.20.1=11`, `ext-ae/common-1.20.1=3`; all pass with 0 failures/ignored.
- `base/common-1.20.1` count inflation versus prior `7` expectation comes from pre-existing active tests in `space.controlnet.chatmc.common.network`, so F2 should gate on actual latest counters rather than inherited assumptions.
- Security/diagnostic caveat remains environment-based: missing `scan_secrets.py` and `jdtls` require fallback secret grep and build/test/manual inspection for this session.

## 2026-02-20 F4 final scope refresh learnings (post evidence/filter corrections)

- Re-run scope probes show no `.project`, `.gitattributes`, or `AGENTS.md` artifacts, but `.beads/` has been reintroduced in repo root.
- Remaining code/test/build deltas continue to map to P0 tasks only (`T2/T6/T7/T8/T9/T10/T11/T12/T13/T14/T15`) plus `.sisyphus/*` support artifacts.
- Deterministic scope result for current working tree is driven by one out-of-scope drift class only: root `.beads/*` artifacts.

## 2026-02-20 F4 deep scope fidelity check (current tree)

- Scope baseline was re-verified against `.sisyphus/plans/p0-stability-hardening.md` (P0 task set T1-T16 + F-wave only).
- Command evidence used for this pass: `git status --short`, `git diff --name-only`, `git diff --stat`, plus `git status --short --untracked-files=all` for full-path classification.
- Current changed-path classification totals:
  - In-scope P0 implementation/test/build: **20** paths
  - In-scope orchestration support (`.sisyphus/*`): **6** paths
  - Out-of-scope drift: **62** paths
- All out-of-scope paths are Eclipse/Buildship metadata (`.project`, `.classpath`, `.settings/org.eclipse.*.prefs`) spanning root, `base/*`, `ext-ae/*`, and `ext-matrix/*`; this drift class is not part of any P0 plan deliverable.
- Scope verdict for the current working tree is therefore **ISSUES** until IDE metadata drift is removed or explicitly policy-allowed.

## 2026-02-20 plan end-state sync note

- Plan end-state checkboxes were synced to verified outcomes for Definition of Done, Final Verification Wave F1-F4, and Final Checklist.
