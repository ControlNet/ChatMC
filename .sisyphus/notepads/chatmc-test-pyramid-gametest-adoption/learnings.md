# Learnings — chatmc-test-pyramid-gametest-adoption
- Added ScenarioIds registry with canonical IDs for GameTest migration preparation.
- Added DeterministicTestSync for polling-based waits and tick spins without sleeps.
- Added DeterministicTestSyncTest covering await success/timeout and spinTicks sequencing without external waits.
- Added a `gameTestServer` Loom run configuration that turns on `forge.enableGameTest` for the `chatmc` namespace so the task is discoverable.
- Introduced `ChatMCGameTestBootstrap` to hook `RegisterGameTestsEvent`, keeping registration plumbing ready for future GameTest holders.
- Verified that `RegisterGameTestsEvent` resides in `net.minecraftforge.event` for Forge 1.20.1, so imports must use that package (not `net.minecraftforge.gametest`).
- Wired Fabric loader: new gametest entrypoint and Loom run configuration expose runGametest with XML reporting.
- Verified Fabric runGametest path; it currently aborts with "No test batches were given" until shared scenarios exist.
- Added a `smokeBootstrap` GameTest method annotated with `template=FabricGameTest.EMPTY_STRUCTURE` and `batch="smoke"` so the harness discovers at least one batch before shared scenarios arrive.
- Confirmed the Fabric gametest entrypoint needs a public no-arg constructor; otherwise the loader's language adapter throws IllegalAccessException when instantiating the class.
- Smoke runs now emit `build/reports/gametest/runGametest.xml` with the passing `chatmcfabricgametestentrypoint.smokebootstrap` case, providing CI-friendly evidence for the harness.
- Replacing source-string contracts in `NetworkProposalLifecycle`/`NetworkAgentError` with reflection-driven behavior tests works in common tests if Minecraft bootstrap is executed first (`SharedConstants.tryDetectVersion` + `Bootstrap.bootStrap`).
- Boundary behavior migration can assert real runtime contracts by invoking private network/persistence boundary methods and encode/write/read call paths with oversized payloads, including UTF surrogate-pair length semantics.

- Added `DeterministicBarrier` in shared test helpers to coordinate explicit arrival/release ordering with bounded waits.
- Added `TimeoutUtility` (await/retry/thread-completion) that builds on `DeterministicTestSync` so orchestration tests stay sleep-free and deterministic.
- Migrated `IndexingNotReadyRegressionTest` and `ThreadConfinementRegressionTest` to consume the new orchestration helpers for indexing/thread scenario flow checks.

- Added Forge `ProposalBindingUnavailableGameTest` that drives the runtime approval flow via `ChatMCNetwork.handleAgentLoopResult` + `handleApprovalDecision` and deterministically invalidates a bound terminal before approval.
- The new scenario asserts `FAILED` + `bound terminal unavailable` and verifies no `ChatRole.TOOL` payload is appended when binding resolution fails.
- Added SavedData reload behavior tests that deserialize/serialize persisted snapshots to confirm transient session states normalize to valid states, active-session mappings survive reload, and message/decision history round-trips intact.

- Added Forge `IndexingGateRecoveryGameTest` covering Task 7 with a deterministic rebuild barrier against `RecipeIndexManager.rebuildAsync`, then runtime recovery checks through `ChatMCNetwork.onTerminalOpened`/`sendSessionSnapshot`.
- The scenario now asserts `INDEXING` while rebuild is pending, verifies request gating (`tryStartThinking` rejected), and validates recovery to actionable state after rebuild completion.
- The same scenario also triggers `RecipeIndexReloadListener.reload(...)`, waits for the new indexing future to complete, and verifies a second open/request cycle does not stick in `INDEXING`.

- Added Forge `ViewerChurnConsistencyGameTest` to drive multi-viewer open/close churn against one public session with deterministic per-viewer sequence ledgers.
- The scenario enforces machine-checkable recipient counters and strict per-viewer monotonic sequence checks (`owner=[1..5]`, `viewerA=[1,2,4]`, `viewerB=[2,3]`) to detect duplicate/missing active-view updates.

- Added Forge `ToolArgsBoundaryEndToEndGameTest` for Task 10 to assert 65535/65536 acceptance and 65537 rejection across parser, network snapshot encode/decode, and saved-data persistence write/read runtime paths.
- Added deterministic UTF multibyte corpus coverage (emoji surrogate pair, CJK, combining-mark seed payloads) with explicit UTF-16 length boundary checks and signal assertions at 65537.

- Added Forge `ServerThreadConfinementGameTest` with an async worker-thread invocation path that reflects into `AgentRunner$McSessionContext.executeTool` and asserts provider execution is marshaled onto the server thread.
- Added a forced-delay contract scenario in the same class that verifies `tool_timeout/tool execution timeout` and `tool_execution_failed/tool execution failed` mappings under bounded delayed execution modes.
- Registered `ServerThreadConfinementGameTest` in `ChatMCGameTestBootstrap` so Task 9 scenarios are discoverable by Forge GameTest bootstrap wiring.

- Added Forge `AeCraftLifecycleIsolationGameTest` in `ext-ae` that validates `requestCraft` begin (`calculating`) → submit (`submitted`) → completion (`done`) transitions, plus deterministic failure (`failed`) and post-failure recovery behavior.
- Verified isolation behavior against `AiTerminalPartOperations` by exercising independent operation instances and asserting foreign `jobId`s remain `unknown` (no cross-terminal leakage) across success/failure/recovery phases.
- In this workspace, Java LSP resolves Task 12 AE GameTest cleanly when `requestCraft`/`jobStatus` are invoked through reflection wrappers (while still asserting runtime behavior), avoiding transient nested-type resolution failures in direct calls.
- Attempted to add Fabric wrappers for Task 13, but the shared scenario logic currently resides only in the Forge GameTest holders that depend on Forge-specific helpers (e.g. `FakePlayerFactory`), so no loader-agnostic registration point exists yet.
- Observed that the base scenario classes for Tasks 6-10 still live solely under `base/forge-1.20.1` and require Forge-only helpers, so Fabric cannot wire the promised shared wrappers until a loader-neutral provider layer is available.

- Added `.github/workflows/layered-testing.yml` with explicit PR/Main/Nightly lanes and lane-scoped commands/artifact uploads aligned to the testing pyramid recommendation.
- Added `scripts/ci_collect_reports.py` + `ci/layered-testing-policy.json` to aggregate JUnit XML, Fabric GameTest XML, and Forge logs/reports into machine-consumable JSON summaries with policy-enforced exit codes.
- Local parser simulation verified `status=pass` for PR JUnit input and `status=blocked` (exit code `3`) for the known Forge runtime blocker signatures (`InvalidModFileException ... version (main)` + `Failed to find system mod: minecraft`).

- Task 18 executed both Fabric suites successfully: base `:base:fabric-1.20.1:runGametest` discovered 6 passing wrappers; AE `:ext-ae:fabric-1.20.1:runGametest -Dfabric-api.gametest.filter=ae_smoke` discovered 1 passing wrapper.
- Added `scripts/gametest_parity_report.py` to generate machine-readable JSON + human-readable Markdown parity output from Fabric XML artifacts plus Forge scenario source introspection.
- Current parity report maps 6 matched Forge scenarios, 1 missing Forge scenario (`ServerThreadConfinementGameTest::timeoutAndFailureContractsRemainStableUnderForcedDelay`), 1 wrapper-only Fabric testcase (`smokeBootstrap`), and marks all 7 Forge scenarios as runtime-blocked by known startup signatures.
- Generated `.sisyphus/evidence/f1-plan-compliance-audit.md` to record F1 compliance status with Forge runtime blocker preserved as blocked.

- Task 17 rerun executed both required Forge commands (`:base:forge-1.20.1:runGameTestServer`, `:ext-ae:forge-1.20.1:runGameTestServer`) and reproduced the same pre-discovery bootstrap failure before any GameTest batches execute.
- Switching Forge run mod wiring from string-based `sourceSet("main", project(...))` to typed `sourceSet project(...).sourceSets.main` did not change failure signatures, indicating the blocker is upstream of this DSL form.
- Fresh evidence logs were captured at `.sisyphus/evidence/task-17-forge-base-rerun.log` and `.sisyphus/evidence/task-17-forge-ext-ae-rerun.log`; both include `InvalidModFileException ... version (main)` followed by missing `minecraft` system-mod detection.

- F2 quality verdict is conditional pass on Fabric and BLOCKED on Forge, based on `.sisyphus/evidence/f2-*.log` plus `ci-reports/parity/gametest-parity-report.md` blocker signatures.

- F3 replay validation confirms 2/3 scenario passes with complete evidence coverage (4/4), and keeps Forge explicitly BLOCKED by startup signatures (`EXIT_CODE=124`, `InvalidModFileException ... version (main)`, `Failed to find system mod: minecraft`).

- Retry #3 isolated the `version (main)` startup failure to Forge run mod coordinates injecting `${module}/forge-1.20.1/bin/main` (debug log `CommonLaunchHandler Got mod coordinates ... bin/main`), where `META-INF/mods.toml` can carry unresolved `${version}` placeholders.
- For Task 17 runtime wiring, restricting Forge run mod source sets to module-local `project.sourceSets.main` removed the follow-on JPMS duplicate-package crash (`ResolutionException` against `generated_*` modules) once the invalid version path was neutralized.
- Current Forge retry logs show both commands now pass mod discovery and launch dedicated server startup (`LaunchServiceHandler Launching target`, server `Done (...)`, and Forge GameTest namespace enablement) without `InvalidModFileException ... version (main)` / `Failed to find system mod: minecraft` signatures.

- F4 scope fidelity deep audit found repository changes concentrated in testing infrastructure, scenario wrappers/bootstrap wiring, and CI/reporting assets for `base` + `ext-ae`, with no changed paths under `ext-matrix/**` and no direct runtime feature expansion.

- Forge startup `InvalidModFileException: Illegal version number specified version (main)` came from the MinecraftLocator composite mod roots that included `.../build/resources/main`, `.../build/classes/java/main`, and `.../bin/main`; the `bin/main` copy had unexpanded `mods.toml` placeholders and poisoned version parsing.
- Added `syncBinMainModsToml` in both Forge modules so `runGameTestServer` always refreshes `bin/main/META-INF/mods.toml` from expanded `build/resources/main`, removing the `version (main)` and downstream `Failed to find system mod: minecraft` signatures.
- Added `ensureGameTestEula` in both Forge modules so first-time `runGameTestServer` no longer aborts immediately on missing `eula.txt`.
- Post-fix Forge logs now reach dedicated server startup and namespace enablement (`chatmc` / `chatmcae`) without the previous blocker signatures.

- Added `environment("gameTestServer")` to the Forge Loom `gameTestServer` run configs so the `runGameTestServer` task now matches the `gameTestServer` template and launches with `forgegametestserveruserdev`; verified by running `:base:forge-1.20.1:runGameTestServer` and `:ext-ae:forge-1.20.1:runGameTestServer --args=--port 25566`, both of which log `--launchTarget, forgegametestserveruserdev` before the usual GameTest bootstrap output.

- Synced `REPO.md` Section 16 to the implemented layered-testing reality: JUnit matrix commands, Fabric GameTest commands + XML paths, Forge GameTest command path with explicit workspace blocker caveat, and CI lane mapping with parity/evidence references (`ci-reports/parity/gametest-parity-report.md`, `.sisyphus/evidence/*`).
- Discovered Gradle rejects `args = []`/`jvmArgs = ...` assignments on JavaExec tasks (`runGameTestServer`), raising `No signature of method java.util.ArrayList.set` during evaluation; switching to `setArgs([])` plus `setJvmArgs(...)` keeps the config intact while preserving the launch-target override logic.


## 2026-02-21 — Forge boot: `Illegal version number specified version (main)` pinpoint
- Evidence from `base/forge-1.20.1/run/logs/debug-2.log.gz` shows Forge `MinecraftLocator` consuming the exploded dev mod coordinates and failing while parsing the mod at:
  - candidate: `/home/ubuntu/GitRepos/ChatAE/base/forge-1.20.1/build/resources/main` (logged as “mod file main”)
  - then: `InvalidModFileException: Illegal version number specified version (main)`
- In this repo, `META-INF/mods.toml` uses `version="${version}"` (e.g., `base/forge-1.20.1/src/main/resources/META-INF/mods.toml`). `processResources` expands that placeholder from `project.version` (see `base/forge-1.20.1/build.gradle` `processResources { expand("version": project.version) }`).
- So the failing value originates from Gradle `project.version` being `main` at runtime (likely via a `mod_version` override), producing `version="main"` in the generated `build/resources/main/META-INF/mods.toml`.
- Added `_coerceForgeResourceVersion` helpers in the Forge modules' `build.gradle` scripts so `processResources` now replaces branch-only names with the numeric fallback stored in `gradle.properties` before emitting `META-INF/mods.toml`. `mods.toml` now reports `version="0.0.1"` even when `project.version` becomes `main`.
- Re-running `:base:forge-1.20.1:runGameTestServer` and `:ext-ae:forge-1.20.1:runGameTestServer` produced the same EULA prompt (`You need to agree to the EULA in order to run the server`) but no longer hit the `Illegal version number ... main` startup block, so the mod-discovery blocker is resolved even though GameTest execution still stops before launching due to the dev server needing manual EULA acceptance.
- Removed the `forgeLaunchTargetArgs` argument provider overrides from both Forge `runGameTestServer` tasks so only the `setJvmArgs(... -Dfabric.dli.env=gameTestServer)` override runs, and verified `./gradlew :base:forge-1.20.1:runGameTestServer` still launches `forgegametestserveruserdev` without the duplicate `--gameDir` error (the run later fails because `gameteststructures/empty.snbt` is missing).
- [2026-02-22] Ensured runGameTestServer now forces --launchTarget forgegametestserveruserdev by clearing argument providers after evaluation and overriding args; both :base:forge-1.20.1 and :ext-ae:forge-1.20.1 (with --port 25566) now log the desired launch target.
- [2026-02-22] Added missing Forge test-resource template `base/forge-1.20.1/src/test/resources/gameteststructures/serverthreadconfinementgametest.empty.snbt` using the same valid empty SNBT shape as existing `empty.snbt` to satisfy class-prefixed GameTest template lookup expectations.
- Explicitly annotated each Forge GameTest method with `@PrefixGameTestTemplate(false)` so `template = "empty"` always resolves to the shared empty structure, even if the class-level default were ever ignored.

- Recreated base/forge empty.snbt with the provided single-block payload and captured :base:forge-1.20.1:runGameTestServer output (see .sisyphus/evidence/task-17-empty-snbt-exact-content.log), which still ends early while evaluation runs due to existing syncBinMainGameTestStructures/shadowCommon Gradle configuration errors before GameTest batches start.

- [2026-02-22] This retry confirmed the missing-template signature can be moved off `Could not find structure file gameteststructures/empty.snbt` by providing run-dir SNBT templates (`run/gameteststructures/empty.snbt` and `run/gameteststructures/chatmc.empty.snbt`); the requested command then progressed to `Game test server ... All 0 required tests passed :)`.

- Added a single-block payload to gameteststructures/empty.snbt so the Forge GameTest template now loads without missing structure errors.

- Restored @GameTestHolder("chatmc") annotations on each base Forge GameTest holder so the configured namespace is honored during discovery.
- After updating `gameteststructures/empty.snbt` to the minimal template provided above, the server still crashes with `Failed to load structure chatmc:empty`, so the current blocker lies beyond the SNBT content itself (possibly resource packaging or namespace lookup).
- Rewriting the run/gameteststructures templates to the latest minimal payload eliminated the missing-file error, but the latest `:base:forge-1.20.1:runGameTestServer` run now halts after starting the 6 chatmc tests because `ServerSessionManager` cannot be resolved, indicating the blocker moved back to compile-time dependencies rather than SNBT content.
- Synchronized bin/main with build/classes/java/main and build/resources/main before runGameTestServer so Forge runs against fresh artifacts and avoids stale type references.
- [2026-02-22] `ServerThreadConfinementGameTest` cross-test interference came from a 31s forced-timeout tool execution occupying queued server-thread work while sibling assertions were already ticking in the same batch; delaying timeout worker start and moving timeout assertions later avoided starvation of the normal thread-marshaling checks.
- [2026-02-22] In this GameTest runtime, fake players were not consistently discoverable through `ChatMCNetwork.findPlayer(UUID)`; seeding PlayerList lookup via reflection kept `AgentRunner$McSessionContext.executeTool` on the intended timeout/failure contract path instead of immediate `tool_execution_failed` short-circuit.
- [2026-02-22] Moving `timeoutAndFailureContractsRemainStableUnderForcedDelay` into its own batch (`chatmc_task9_timeout`) yielded deterministic execution order (`chatmc` batch first, timeout batch second), preventing timeout-contract load from destabilizing sibling thread-marshaling assertions.
- [2026-02-22] `ViewerChurnConsistencyGameTest` task8 empty-viewer failure came from `broadcastSessionSnapshot` immediately unsubscribing FakePlayers that were not discoverable via `server.getPlayerList().getPlayer(UUID)`; `handleOpenSession` subscribed, but the same tick cleanup removed all viewers.
- [2026-02-22] Making owner/viewers resolvable in `PlayerList` before `onTerminalOpened`/`handleOpenSession` (plus explicit `ChatMCNetwork.setServer(server)` in test setup) stabilizes the churn scenario; `runGameTestServer` now reports `All 6 required tests passed :)`.
- Isolating `ViewerChurnConsistencyGameTest::multiViewerSnapshotConsistencyUnderChurn` into `batch="chatmc_task8_viewer"` removes shared-static interference from the `chatmc` batch and let `:base:forge-1.20.1:runGameTestServer --configure-on-demand` finish with no `task8/setup/viewer-a-open/viewers-for-session ... actual: []` signature, confirming the previous viewer failure is gone.

- Added `syncRunGameTestStructures` in ext-ae's Forge build so `run/gameteststructures` refreshes SNBT templates from both the extension workspace and the base module before `runGameTestServer`; the AE GameTest server now starts the `chatmcae` batch and only crashes later on the existing Task 12 timeout assertion, showing the missing `gameteststructures/empty.snbt` blocker has been resolved.
- [2026-02-22] Landed the layered GameTest/CI infrastructure and AE lifecycle stability fixes in a single commit, then `git pull --rebase`, `bd sync`, and `git push` so `dev` now contains the finalized scenario wiring, reporting scripts, and documentation updates that drove this delivery.

- [2026-02-22] Task 12 `wait-submitted` stall root cause was a proxy return-type mismatch in `AeCraftLifecycleIsolationGameTest`: mocked `ICraftingService#getCpus()` returned `Set.of()` while AE expects Guava `ImmutableSet`, so submit-path execution never advanced and job state stayed `calculating`.
- [2026-02-22] Updating the controlled crafting attempt cpus to `ImmutableSet<ICraftingCPU>` (and enqueue values to `ImmutableSet.of()`) restored deterministic `calculating -> submitted -> done` transitions without weakening lifecycle/isolation assertions.
- [2026-02-22] Verified with `:ext-ae:forge-1.20.1:runGameTestServer --args='--port 25566'`: `All 1 required tests passed :)`, with no `task12/terminal-a/wait-submitted` timeout and no `gameteststructures/empty.snbt` missing-structure signature.
