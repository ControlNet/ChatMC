# Decisions — chatmc-test-pyramid-gametest-adoption
- Keep the Forge GameTest bootstrap minimal by maintaining an empty registration list so future GameTest holders can be added without loading scenario logic in the loader itself.
- Drive the new `runGameTestServer` task via a Loom `runs.create("gameTestServer")` block that sets `forge.enableGameTest`/`forge.enabledGameTestNamespaces`, leaving the default run directory in place because `RunConfigSettings` lacked a `workingDirectory` setter.
- Keep the Fabric GameTest wrapper thin by only providing a ModInitializer entrypoint; actual scenario registration belongs in the shared helper layer.
- Register a Loom `runs.register("gametest")` config that flips on `fabric-api.gametest` and writes a dedicated `reports/gametest/runGametest.xml` artifact so CI can collect results.
- Keep the Fabric gametest wrapper loader-thin by registering a minimal smoke case in the entrypoint for now and delegating future shared scenario providers to the common layer without copying their assertions.
- Ensure fabric-gametest entrypoint classes expose a public no-arg constructor so the default language adapter can instantiate them while loading the test harness.

- Keep async/indexing/thread orchestration helpers loader-agnostic and isolated under `space.controlnet.chatmc.common.testing` so downstream tests can reuse them across Forge/Fabric wrappers.
- Use a deterministic `arrive -> awaitArrivals -> release` barrier protocol in consumer tests to assert pre-release and post-release invariants without sleep polling.

- Register `ProposalBindingUnavailableGameTest` explicitly in Forge `ChatMCGameTestBootstrap` so Task 6 stays in loader-thin registration and scenario logic remains in a dedicated class.
- Model binding invalidation with a map-backed `TerminalContextResolver` and invoke the private network handlers reflectively so assertions cover runtime transition behavior rather than source-string contracts.
- Defer Fabric wrappers for Task 13 until the shared scenario providers are extracted into a loader-neutral module; until then the entrypoint should stay limited to the smoke bootstrap so we can keep it loader-thin.
- Replace Task 4 source-string regressions by deleting the proposal-lifecycle/agent-error/boundary contract readers and introducing behavior-oriented tests named with `*Behavior*` filters to satisfy stability gates.
- Keep Task 4 migration test-only by using reflection against existing private methods (`handleAgentLoopResult`, boundary validators, snapshot/session encode paths) instead of refactoring production visibility.
- For Task 11 SavedData reload coverage, assert on normalized metadata (states, proposal IDs) after rehydration so we validate behavior without relying on record equality for persisted objects.

- For Task 8, keep assertions behavior-driven and loader-thin by scripting churn through `onTerminalOpened`/`onTerminalClosed` + reflective `handleOpenSession` and validating deterministic recipient/sequence ledgers instead of UI-level checks.

- For Task 7, drive deterministic pending-indexing state by reflecting `RecipeIndexService.indexManager` and running `RecipeIndexManager.rebuildAsync` behind an explicit latch barrier, avoiding sleep-based timing races.
- Cover the reload trigger path explicitly by invoking `RecipeIndexReloadListener.reload(...)` in the same GameTest and asserting post-reload readiness before the second open/request cycle checks.
- Keep Task 7 assertions runtime-centric (session snapshot state + `tryStartThinking` gating/recovery) and limit direct setter usage to preparing the second-cycle actionable baseline.

- For Task 10, keep boundary validation runtime-centric by driving parser validation through reflected `ToolCallArgsParseBoundary.validate` and network/persistence contracts through reflected snapshot/session encode+decode and write+read paths, instead of adding new production hooks.
- Keep UTF edge coverage deterministic by using a fixed multibyte corpus and length-targeted payload builders (65535/65536/65537) so GameTest outcomes are reproducible across runs.

- For Task 9, execute thread-confinement assertions through reflected `AgentRunner$McSessionContext.executeTool` calls (from worker threads) so coverage stays behavior-driven on the real AgentRunner→ToolRegistry dispatch path.
- Use a controlled ToolProvider mode switch with bounded forced delays (`31_000ms` timeout trigger and delayed throw branch) to verify timeout and execution-failure mapping contracts without source-string checks.
- Register `ServerThreadConfinementGameTest` in `ChatMCGameTestBootstrap` to keep loader wiring explicit and scenario logic isolated in a dedicated Forge GameTest holder.

- For Task 12 (AE), keep assertions behavior-driven by invoking `AiTerminalPartOperations.requestCraft/jobStatus/jobStateChange` directly with deterministic proxy-backed AE crafting services instead of asserting source strings or adding production-only hooks.
- Register AE GameTests through a dedicated Forge bootstrap class (`ChatMCAeGameTestBootstrap`) and an `ext-ae` `gameTestServer` Loom run namespace (`chatmcae`) so loader wiring remains explicit and isolated from base Forge scenarios.
- For Task 12 workspace stability, invoke `requestCraft`/`jobStatus` via reflection wrappers inside the scenario so Java LSP stays clean while behavior assertions still execute against real runtime operations.
- Task 13 Fabric wrappers remain blocked until the shared base GameTest providers can be extracted into a loader-neutral module; wiring cannot proceed while the only available holders live inside the Forge module and drag in Forge-only dependencies.

- Standardize CI layering in a single workflow with three explicit lanes: PR (JUnit matrix only), Main (JUnit + Forge GameTest with blocker surfacing), Nightly (Fabric + AE-heavy GameTests + JUnit).
- Encode flaky/retry/quarantine policy in `ci/layered-testing-policy.json` and enforce it via `scripts/ci_collect_reports.py` so lane outcomes are machine-consumable and fail non-zero when thresholds are exceeded.
- Treat the known Forge runtime startup failure as a first-class `blocked` status (exit code `3`) when signatures match policy, and surface it with warning + artifacts instead of hiding it as a pass.

- For Task 18 parity evidence, classify Forge/Fabric parity at Forge scenario granularity (class + method) while allowing Fabric wrappers to map by explicit `Forge scenario` / `Mirrors` hints plus method-name similarity.
- Keep parity outputs dual-format (`.md` + `.json`) under `.sisyphus/evidence/` so reviewers can read quickly while automation can parse categories (`matched`, `missing`, `wrapper_only`, `runtime_blocked`).
- Do not count Fabric `smokeBootstrap` as Forge parity coverage; keep it explicitly in `wrapper_only` to avoid masking scenario gaps.

- Keep Task 17 in explicit `blocked-runtime` state until Forge startup can resolve `version (main)` / missing `minecraft` system-mod failures; do not claim flake elimination while bootstrap fails before test discovery.
- Preserve deterministic flake-control policy by using bounded run command timeouts and captured full logs as evidence artifacts, instead of adding sleeps/retries that could hide startup failures.
