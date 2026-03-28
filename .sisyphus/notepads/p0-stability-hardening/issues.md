# Issues

## 2026-02-20 Open risks and ambiguities

- **P0 transition ambiguity:** Should first proposal be modeled as `THINKING -> WAIT_APPROVAL` directly, or must runtime introduce an explicit `THINKING -> EXECUTING` step before proposal emission. Current code does neither consistently, causing fallback to `IDLE` on first proposal failure path.
- **API drift risk:** `tryResolveExecution` is unused. Either adopt it as canonical completion path in T6/T7 or remove it later after transition contract tests are in place.
- **Dual orchestrator risk:** `SessionOrchestrator` is currently unreferenced while `ChatMCNetwork` duplicates viewer and indexing-state orchestration. This can diverge silently during P0 fixes.
- **Tooling gap:** Java LSP (`jdtls`) is unavailable in current environment, so reference mapping relies on grep/AST evidence only.

## 2026-02-20 Task 1 scope-correction retry

- Unresolved ambiguity remains P0-blocking: whether first proposal should transition directly `THINKING -> WAIT_APPROVAL` or via explicit `THINKING -> EXECUTING` pre-step; current code still falls back to `IDLE` when `trySetProposal` rejects initial proposal path.

## 2026-02-20 Task 2 indexing contract issues

- **Sticky INDEXING risk:** transition into `INDEXING` exists, transition out is missing in current snapshot flow, so sessions can remain blocked after index readiness returns (`ChatMCNetwork.java:417-425`, `:547-550`; `RecipeIndexManager.java:22-24`).
- **Durable lock risk:** `INDEXING` state is persisted (`ChatMCNetwork.java:420-422`) but not normalized on load (`ServerSessionManager.java:348-353`).
- **Parity risk remains active:** duplicated helper in unreferenced `SessionOrchestrator` can diverge from `ChatMCNetwork` during T8/T13 hardening (`SessionOrchestrator.java:162-166`).

## 2026-02-20 Task 3 thread-boundary issues

- **Async execution breach:** `AgentLoop.executeNode` executes tools directly on async graph path (`AgentLoop.java:385-408`), but session mutation and final state apply are server-thread patterns elsewhere (`ChatMCNetwork.java:467-471`, `:591-597`).
- **Ordering race risk:** tool output append is re-queued to server (`AgentRunner.java:141-143`, `ChatMCNetwork.java:467-471`) while loop progress continues asynchronously, so history visibility can lag behind decision flow.
- **Timeout gap:** no loop-level timeout envelope on `runAgentLoopAsync` (`ChatMCNetwork.java:744-749`), so T9 must define deterministic timeout to avoid long-lived transient states.

## 2026-02-20 Task 4 args-size contract issues

- **Wire-cap mismatch (P0):** proposal args are serialized with `2048` cap instead of unified `65536` (`ChatMCNetwork.java:655`, `:714`).
- **Validation gap:** no explicit size guard exists before parser-created `ToolCall` instances enter execution (`AgentReasoningService.java:433-434`, `:455-456`; `LangChainToolCallParser.java:46-47`; `AgentLoop.java:407`).
- **Persistence ambiguity:** proposal args are saved/loaded without boundary checks, so oversize payload behavior is undefined on reload (`ChatMCSessionsSavedData.java:209`, `:234-235`).
- **Char-vs-byte risk:** 65536-char policy does not guarantee transport byte fit for multi-byte Unicode payloads, so overflow mapping must be explicit and deterministic.

## 2026-02-20 Task 5 harness-planning issues

- **Harness baseline gap:** no module currently exposes committed `*Test.java` scaffolding for the planned T12-T15 suites, so fixture bootstrap is a hard prerequisite.
- **Tooling blocker:** Java LSP (`jdtls`) is still missing, ownership mapping and seam validation remain grep and AST based.
- **Build blocker:** cross-loader verification remains constrained by Gradle configure failure tied to missing Fabric `devlibs` jar, so T16 cannot be treated as fully green until dependency resolution is fixed.

## 2026-02-20 F2 code-quality review issues

- **Critical test-protection gap:** all newly added Task 12-15 regression files are `main(...)` programs (no `@Test`), and Gradle module test reports show `0` tests executed (`base/core`, `base/common-1.20.1`, `ext-ae/common-1.20.1`).
- **Tooling blocker remains:** `lsp_diagnostics` cannot run because `jdtls` is missing from `$PATH`; static correctness relied on Gradle compile/build checks plus manual source review.

## 2026-02-20 F4 scope fidelity check issues

- **P1/P2 feature creep:** none detected in the current changed-file set; all product code deltas map to P0 tasks T6-T15 (state machine, indexing recovery, thread boundary, args boundary).
- **Root drift watch:** targeted root scans found no `.project` or `.gitattributes` artifacts, so no new root-noise drift class is present.
- **Support-only artifact caution:** `.sisyphus/boulder.json` and `.sisyphus/*` plan/notepad files are orchestration/support artifacts (not runtime product features); keep them treated as non-product scope in release-focused commit slicing.

## 2026-02-20 F1 plan compliance audit findings

- **Evidence artifacts missing:** Plan requires `.sisyphus/evidence/task-*.{log|json|png}` artifacts, but repo has no `.sisyphus/evidence/` directory (directory listing shows only `.sisyphus/{boulder.json,drafts/,notepads/,plans/}`). This prevents evidence-based confirmation of task QA scenarios.
- **"Test-verified" gap for args boundary:** New regression classes under `src/test/java/**` are `public static void main(String[] args)` harnesses (no JUnit `@Test`). Gradle test filter confirms they are not discovered by `:base:core:test`:
  - Command: `./gradlew :base:core:test --tests "*ToolCallArgsParseBoundaryRegressionTest*"`
  - Output: `No tests found for given includes ...` (BUILD FAILED)
- **Compliance impact:** Must Have "Strict and test-verified boundary policy for tool args using high cap 65536" cannot be marked PASS under plan's "Gradle test tasks (JUnit-based)" verification strategy.

## 2026-02-20 F2 code-quality review rerun (this session)

- **Test execution blocker (still critical):** module reports confirm `0` executed tests in all required modules after running `./gradlew :base:core:test :base:common-1.20.1:test :ext-ae:common-1.20.1:test` (`base/core/build/reports/tests/test/index.html`, `base/common-1.20.1/build/reports/tests/test/index.html`, `ext-ae/common-1.20.1/build/reports/tests/test/index.html`).
- **Secret-guard tooling blocker:** required command `python scripts/scan_secrets.py staged` fails because `scripts/scan_secrets.py` is missing in repo (`Errno 2`), so standard staged/gitignore secret audit is not executable in this workspace.
- **Static diagnostics tooling blocker:** `lsp_diagnostics` remains unavailable for changed Java files because `jdtls` is not present in `$PATH`.
- **Shortcut hygiene watch:** changed files contain `catch (... ignored)` patterns (`AgentReasoningService`, `ServerSessionManager`), which are intentional in current code but still reduce observability if overused.

## 2026-02-20 F4 scope fidelity refresh (post-cleanup retry)

- **Scope drift status:** no new out-of-scope artifacts found; root-noise checks for `.project`, `.gitattributes`, `.classpath`, and `*.iml` are clean.
- **Mapping status:** all currently changed/untracked paths are mapped to P0 tasks/support only; no unmapped path requiring a scope-issue flag.
- **Support artifact reminder:** `.sisyphus/*` entries remain orchestration/support files and are excluded from product-feature scope judgment.

## 2026-02-20 F3 QA scenario replay issues (T1-T16)

- **Scenario replay command matrix is mostly blocked by test-discovery mismatch:** targeted Gradle filters for task-level scenarios returned `No tests found for given includes` on C01-C08, C10-C28 (24 command replays blocked).
- **Evidence bundle is missing at source:** `.sisyphus/evidence/` does not exist; `ls ".sisyphus/evidence/task-*"` failed (`C30`, rc=2), so plan-required evidence artifacts for task scenarios cannot be validated.
- **Only broad module-level verification commands passed:**
  - `C09` (`:base:core:test :base:common-1.20.1:test :ext-ae:common-1.20.1:test`) -> PASS
  - `C29` (`:base:forge-1.20.1:build :base:fabric-1.20.1:build`) -> PASS
- **Hook-name mismatch is structural:** discovered regression harnesses are `main(...)` suites (e.g., `ServerSessionManagerStateMachineRegressionTest`, `ThreadConfinementRegressionTest`, `AeThreadConfinementRegressionTest`, `ToolCallArgsParseBoundaryRegressionTest`, `ToolArgsBoundaryRegressionContractTest`), but task QA filters expect names like `*Network*ProposalLifecycle*`, `*ReasoningService*ArgsOverflow*`, `*SavedData*ArgsOverflow*`.
- **Evidence-without-replay check:** no `task-*` evidence files exist, so there are no cases where evidence existed but replay command was skipped.

## 2026-02-20 P0 regression discoverability remediation issues

- **Tooling limitation (non-blocking for this task):** Java LSP diagnostics remain unavailable in this environment (`jdtls` missing from `$PATH`), so regression validation relied on Gradle compile/test outputs.
- **Classpath sensitivity:** common-module runtime regressions are sensitive to test runtime dependency shape; using plain JUnit conversion without mod-runtime wiring causes initialization/linkage failures (`DeferredRegister` / method mismatch). Current fix is minimal but should be kept under watch during dependency upgrades.

## 2026-02-20 scope-cleanup retry note

- Removed out-of-scope generated artifacts (`.gitattributes`, `AGENTS.md`, and three module `.project` files) while preserving in-scope JUnit remediation and P0 deltas.

## 2026-02-20 base/common scenario-filter coverage issues

- Java LSP diagnostics remain unavailable in this environment (`jdtls` missing), so changed-test validation depended on Gradle targeted test executions.

## 2026-02-20 F3 QA replay rerun (post-JUnit conversion)

- **Discovery improved but pattern mismatch remains:** direct replay `C01-C31` now has multiple PASS commands, but filters tied to old naming still return `No tests found for given includes` on `C03,C04,C08,C11-C25,C28`.
- **Deterministic equivalents executed for blocked filters:** `E01-E06` all PASS against discovered JUnit suites (`ServerSessionManager*RegressionTest`, `ThreadConfinementRegressionTest`, `AeThreadConfinementRegressionTest`, `ToolCallArgsParseBoundaryRegressionTest`, `ToolArgsBoundaryRegressionContractTest`).
- **Non-zero execution confirmed in module reports:** `base/core=12`, `base/common-1.20.1=3`, `ext-ae/common-1.20.1=3` (`*/build/reports/tests/test/index.html`).
- **Remaining scenario blockers (coverage gaps):** no direct/equivalent suite found for indexing gate matrix semantics (`T2-S2`), network proposal lifecycle + network agent error (`T7-S1/S2`), and not-ready communication path (`T8-S2`).
- **Evidence completeness still blocked:** `.sisyphus/evidence/task-*` absent (`C30` failed, repo `.sisyphus/` contains no `evidence/` directory), so evidence-completeness scenario remains blocked even where commands pass.

## 2026-02-20 F2 refresh after JUnit remediation

- **Critical gate failure remains:** rerun reports still show `0` executed tests in all required modules (`base/core`, `base/common-1.20.1`, `ext-ae/common-1.20.1`) after `./gradlew :base:core:test :base:common-1.20.1:test :ext-ae:common-1.20.1:test --rerun-tasks`.
- **Expected-vs-actual mismatch:** inherited expected counters (`12 / 7 / 3`) were not observed on the current workspace state; generated HTML summaries remain zero-test.
- **Tooling blockers unchanged:** Java LSP diagnostics cannot run (`jdtls` missing); secret-guard script commands fail because `scripts/scan_secrets.py` is absent.
- **Scope-cleanup status:** root-noise probes for `.project`/`.gitattributes` remain clean; current delta is limited to P0 code/test/build-script paths plus `.sisyphus/*` support artifacts.

## 2026-02-20 F2 final refresh (current-state verification)

- **Stale-data correction:** latest rerun shows non-zero execution; prior `0 tests` conclusion is not current for this workspace state.
- **Current counters (report + task hook):** `base/core=12`, `base/common-1.20.1=11`, `ext-ae/common-1.20.1=3`, all passing.
- **Counter expectation note:** inherited `12/7/3` baseline is outdated versus current module reality because base-common now includes additional active regression classes (`IndexingGateRegressionTest`, `NetworkAgentErrorRegressionTest`) beyond the two JUnit-remediated files.
- **Residual tooling limitations:** `jdtls` still unavailable for LSP diagnostics; `scripts/scan_secrets.py` still missing so secret-guard runs require fallback pattern scan.

## 2026-02-20 F1 rerun after JUnit remediation

- **JUnit discovery fixed:** `@Test` annotations now present in Task 12-15 regression suites (e.g. `base/core/src/test/java/.../ToolCallArgsParseBoundaryRegressionTest.java`, `base/common-1.20.1/src/test/java/.../ThreadConfinementRegressionTest.java`, `ext-ae/common-1.20.1/src/test/java/.../AeThreadConfinementRegressionTest.java`).
- **Non-zero test execution confirmed (Gradle HTML reports):** `base/core=12`, `base/common-1.20.1=7`, `ext-ae/common-1.20.1=3` tests; all show `0` failures/ignored on report indices.
- **Evidence artifacts still missing:** `.sisyphus/evidence/task-*` not present (no `.sisyphus/evidence/` directory), so plan QA-policy evidence bundle remains incomplete.
- **Repo cleanliness check:** `git status --short` still shows modified build scripts and untracked test sources; scope-cleanup claim should be revalidated before final gate.

## 2026-02-20 Evidence bundle assembly status update

- Created `.sisyphus/evidence/` and added all 32 plan-referenced `task-*.log` artifacts.
- Preserved existing blocked scenario status from prior verified replay outcomes: `task-2-indexing-gate`, `task-7-lifecycle`, `task-7-error`, `task-8-notready`, `task-16-evidence-scope`.
- Tooling blocker unchanged: Java LSP diagnostics remain unavailable in this workspace because `jdtls` is missing from `$PATH`; evidence notes stay anchored to Gradle and report outcomes.

## 2026-02-20 Evidence/scope correction follow-up

- Corrected stale evidence statuses to current verified outcomes: `task-2-indexing-gate`, `task-7-lifecycle`, `task-7-error`, `task-8-notready`, and `task-16-evidence-scope` are now marked `PASS`.
- Re-ran the four required targeted Gradle filters; all ended `BUILD SUCCESSFUL` (one initial parallel run had test task-state contention for `*Indexing*NotReady*`, then passed on immediate sequential retry).
- Removed out-of-scope Eclipse metadata drift at `base/common-1.20.1/.project`.

## 2026-02-20 F3 finalization refresh

- No residual blocked scenario remains in current evidence bundle: all 32 `task-*.log` artifacts now carry `status: PASS`.
- Quick rerun of previously missing filters all passed: `*Indexing*Gate*`, `*Network*ProposalLifecycle*`, `*Network*AgentError*`, `*Indexing*NotReady*`.
- Required test matrix command revalidated as PASS (`:base:core:test :base:common-1.20.1:test :ext-ae:common-1.20.1:test`).
- Evidence completeness blocker is resolved in current state (`.sisyphus/evidence/task-*.log` count = 32, matching plan `Evidence:` entries = 32).

## 2026-02-20 F1 final refresh (post evidence + filter fixes)

- **QA evidence now present:** `glob .sisyphus/evidence/task-*.log` resolves **32** artifacts (plan QA policy satisfied).
- **Previously-blocked items corrected to PASS in evidence headers:**
  - `task-2-indexing-gate.log` status PASS (scenario filter command recorded)
  - `task-7-lifecycle.log` status PASS
  - `task-8-recovery.log` status PASS
  - `task-16-full-matrix.log` + `task-16-evidence-scope.log` status PASS
- **Module test execution (HTML report counters):** `base/core=12`, `base/common-1.20.1=11`, `ext-ae/common-1.20.1=3`, all with `0` failures/ignored.

## 2026-02-20 F4 final scope refresh issues (post evidence/filter corrections)

- **Root drift reintroduced:** generated support files exist again in the working tree (`README.md`, `config.yaml`, `interactions.jsonl`, `metadata.json`; plus local DB files present on disk).
- **Scope impact:** despite P0-aligned code/test/build changes, current scope gate remains in ISSUES state until the generated support-file drift is removed or explicitly classified/ignored by orchestrator policy.

## 2026-02-20 scope-cleanup follow-up

- Removed root generated support artifacts plus every module-level `.project`, `.classpath`, `.factorypath`, and `.settings` artifact so that `git status --short` only lists P0 deltas and support files, keeping `.sisyphus/` and P0 tests intact.

## 2026-02-20 scope-drift cleanup note
- Removed generated support files, module .project files, and stray .gitignore entries to restore the clean baseline

## 2026-02-20 root artifact cleanup

- Removed root `.gitattributes` and `AGENTS.md` drift artifacts to keep the workspace scoped for F4 verification
 
## 2026-02-20 .gitignore reconciliation note

- Removed the `.settings`, `.sisyphus`, `.project`, `.classpath`, and `.factorypath` entries because they were masking scope-drift artifacts introduced by prior delegated runs; dropping them reveals the same root drift directories so they can now be cleaned intentionally in a follow-up step.
- Verification: `git diff -- .gitignore` is clean (baseline restored) and `git status --short` now lists the untracked root artifacts that the entries had been hiding, which will be handled by the orchestrator separately.

## 2026-02-20 F4 deep scope fidelity check issues (current tree)

- **Scope gate result: ISSUES** due out-of-scope IDE metadata drift currently visible in `git status --short --untracked-files=all`.
- **Out-of-scope drift paths (exact):**
  - `.project`
  - `.settings/org.eclipse.buildship.core.prefs`
  - `base/.classpath`
  - `base/.project`
  - `base/.settings/org.eclipse.buildship.core.prefs`
  - `base/.settings/org.eclipse.jdt.core.prefs`
  - `base/common-1.20.1/.classpath`
  - `base/common-1.20.1/.project`
  - `base/common-1.20.1/.settings/org.eclipse.buildship.core.prefs`
  - `base/common-1.20.1/.settings/org.eclipse.jdt.core.prefs`
  - `base/core/.classpath`
  - `base/core/.project`
  - `base/core/.settings/org.eclipse.buildship.core.prefs`
  - `base/core/.settings/org.eclipse.jdt.core.prefs`
  - `base/fabric-1.20.1/.classpath`
  - `base/fabric-1.20.1/.project`
  - `base/fabric-1.20.1/.settings/org.eclipse.buildship.core.prefs`
  - `base/fabric-1.20.1/.settings/org.eclipse.jdt.core.prefs`
  - `base/forge-1.20.1/.classpath`
  - `base/forge-1.20.1/.project`
  - `base/forge-1.20.1/.settings/org.eclipse.buildship.core.prefs`
  - `base/forge-1.20.1/.settings/org.eclipse.jdt.core.prefs`
  - `ext-ae/.classpath`
  - `ext-ae/.project`
  - `ext-ae/.settings/org.eclipse.buildship.core.prefs`
  - `ext-ae/.settings/org.eclipse.jdt.core.prefs`
  - `ext-ae/common-1.20.1/.classpath`
  - `ext-ae/common-1.20.1/.project`
  - `ext-ae/common-1.20.1/.settings/org.eclipse.buildship.core.prefs`
  - `ext-ae/common-1.20.1/.settings/org.eclipse.jdt.core.prefs`
  - `ext-ae/core/.classpath`
  - `ext-ae/core/.project`
  - `ext-ae/core/.settings/org.eclipse.buildship.core.prefs`
  - `ext-ae/core/.settings/org.eclipse.jdt.core.prefs`
  - `ext-ae/fabric-1.20.1/.classpath`
  - `ext-ae/fabric-1.20.1/.project`
  - `ext-ae/fabric-1.20.1/.settings/org.eclipse.buildship.core.prefs`
  - `ext-ae/fabric-1.20.1/.settings/org.eclipse.jdt.core.prefs`
  - `ext-ae/forge-1.20.1/.classpath`
  - `ext-ae/forge-1.20.1/.project`
  - `ext-ae/forge-1.20.1/.settings/org.eclipse.buildship.core.prefs`
  - `ext-ae/forge-1.20.1/.settings/org.eclipse.jdt.core.prefs`
  - `ext-matrix/.classpath`
  - `ext-matrix/.project`
  - `ext-matrix/.settings/org.eclipse.buildship.core.prefs`
  - `ext-matrix/.settings/org.eclipse.jdt.core.prefs`
  - `ext-matrix/common-1.20.1/.classpath`
  - `ext-matrix/common-1.20.1/.project`
  - `ext-matrix/common-1.20.1/.settings/org.eclipse.buildship.core.prefs`
  - `ext-matrix/common-1.20.1/.settings/org.eclipse.jdt.core.prefs`
  - `ext-matrix/core/.classpath`
  - `ext-matrix/core/.project`
  - `ext-matrix/core/.settings/org.eclipse.buildship.core.prefs`
  - `ext-matrix/core/.settings/org.eclipse.jdt.core.prefs`
  - `ext-matrix/fabric-1.20.1/.classpath`
  - `ext-matrix/fabric-1.20.1/.project`
  - `ext-matrix/fabric-1.20.1/.settings/org.eclipse.buildship.core.prefs`
  - `ext-matrix/fabric-1.20.1/.settings/org.eclipse.jdt.core.prefs`
  - `ext-matrix/forge-1.20.1/.classpath`
  - `ext-matrix/forge-1.20.1/.project`
  - `ext-matrix/forge-1.20.1/.settings/org.eclipse.buildship.core.prefs`
- `ext-matrix/forge-1.20.1/.settings/org.eclipse.jdt.core.prefs`

## 2026-02-20 IDE metadata cleanup follow-up

- Removed the remaining root/module Eclipse metadata artifacts (.project, .classpath, .settings) so the workspace now retains only intended P0 deltas, `.sisyphus/`, and the designated `src/test/` folders in `git status --short`.
