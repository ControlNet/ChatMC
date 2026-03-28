# F2 Code + Test Quality Review

Review date: 2026-02-21

## Build/Lint/Test summary (touched modules)

- Unit test command passed: `./gradlew :base:core:test :base:common-1.20.1:test :ext-ae:common-1.20.1:test` (`.sisyphus/evidence/f2-gradle-unit-tests.log`, `EXIT_CODE=0`).
- Base core unit XML suites are green (3 suites, 12 tests total, 0 failures, 0 errors):
  - `base/core/build/test-results/test/TEST-space.controlnet.chatmc.core.agent.ToolCallArgsParseBoundaryRegressionTest.xml`
  - `base/core/build/test-results/test/TEST-space.controlnet.chatmc.core.session.ServerSessionManagerStateMachineRegressionTest.xml`
  - `base/core/build/test-results/test/TEST-space.controlnet.chatmc.core.session.ServerSessionManagerIndexingRecoveryRegressionTest.xml`
- Base common unit XML suites are green (10 suites, 31 tests total, 0 failures, 0 errors):
  - `base/common-1.20.1/build/test-results/test/TEST-space.controlnet.chatmc.common.agent.ThreadConfinementRegressionTest.xml`
  - `base/common-1.20.1/build/test-results/test/TEST-space.controlnet.chatmc.common.boundary.ToolArgsBoundaryBehaviorTest.xml`
  - `base/common-1.20.1/build/test-results/test/TEST-space.controlnet.chatmc.common.network.IndexingGateRegressionTest.xml`
  - `base/common-1.20.1/build/test-results/test/TEST-space.controlnet.chatmc.common.network.IndexingNotReadyRegressionTest.xml`
  - `base/common-1.20.1/build/test-results/test/TEST-space.controlnet.chatmc.common.network.NetworkAgentErrorBehaviorTest.xml`
  - `base/common-1.20.1/build/test-results/test/TEST-space.controlnet.chatmc.common.network.NetworkProposalLifecycleBehaviorTest.xml`
  - `base/common-1.20.1/build/test-results/test/TEST-space.controlnet.chatmc.common.session.SavedDataReloadBehaviorTest.xml`
  - `base/common-1.20.1/build/test-results/test/TEST-space.controlnet.chatmc.common.testing.DeterministicBarrierTest.xml`
  - `base/common-1.20.1/build/test-results/test/TEST-space.controlnet.chatmc.common.testing.DeterministicTestSyncTest.xml`
  - `base/common-1.20.1/build/test-results/test/TEST-space.controlnet.chatmc.common.testing.TimeoutUtilityTest.xml`
- Ext-AE common unit XML suite is green (1 suite, 3 tests, 0 failures, 0 errors):
  - `ext-ae/common-1.20.1/build/test-results/test/TEST-space.controlnet.chatmc.ae.common.tools.AeThreadConfinementRegressionTest.xml`
- Base Fabric GameTest command passed: `./gradlew :base:fabric-1.20.1:runGametest` (`.sisyphus/evidence/f2-base-fabric-runGametest.log`, `EXIT_CODE=0`) with XML report at `base/fabric-1.20.1/build/reports/gametest/runGametest.xml` (6 testcases listed).
- Ext-AE Fabric GameTest command passed: `./gradlew :ext-ae:fabric-1.20.1:runGametest -Dfabric-api.gametest.filter=ae_smoke` (`.sisyphus/evidence/f2-ext-ae-fabric-ae-smoke.log`, `EXIT_CODE=0`) with XML report at `ext-ae/fabric-1.20.1/build/reports/gametest/runGametest.xml` (1 testcase listed).
- Forge runtime remains blocked: `./gradlew :base:forge-1.20.1:runGameTestServer` reproduces `Illegal version number specified version (main)` and `Failed to find system mod: minecraft` in `.sisyphus/evidence/f2-base-forge-runGameTestServer.log` and `.sisyphus/evidence/f2-base-forge-runGameTestServer-timeboxed.log` (`EXIT_CODE=124` in timeboxed run).
- Lint: **N/A** (no lint task/plugin signals found in touched module build scripts: `base/fabric-1.20.1/build.gradle`, `base/forge-1.20.1/build.gradle`, `ext-ae/fabric-1.20.1/build.gradle`, `ext-ae/forge-1.20.1/build.gradle`).

## Flake risks (changed test dirs)

- Implicit sleeps: **Low risk**. No runtime `Thread.sleep(...)` calls found in changed test/runtime-test dirs; only explanatory mentions in comments in `base/common-1.20.1/src/test/java/space/controlnet/chatmc/common/testing/DeterministicTestSync.java`.
- Nondeterministic waits: **Low-Medium risk**. Wait loops are generally bounded by explicit deadlines/attempt budgets:
  - `base/common-1.20.1/src/test/java/space/controlnet/chatmc/common/testing/DeterministicTestSync.java` (deadline-based `await`)
  - `base/common-1.20.1/src/test/java/space/controlnet/chatmc/common/testing/TimeoutUtility.java` (deadline + `maxAttempts` in `retry`)
  - `base/forge-1.20.1/src/main/java/space/controlnet/chatmc/forge/gametest/IndexingGateRecoveryGameTest.java` (deadline-bounded polling)
  - `base/forge-1.20.1/src/main/java/space/controlnet/chatmc/forge/gametest/ServerThreadConfinementGameTest.java` (`blockAtLeast` loop exits at computed deadline)
- Hidden global state coupling: **Medium risk**. Tests rely on shared static singleton state (`ChatMCNetwork.SESSIONS`, `SESSION_LOCALE`, and in Forge GameTests `ChatMC.RECIPE_INDEX`) and manually reset it between cases, which can couple behavior if parallelized:
  - `base/common-1.20.1/src/test/java/space/controlnet/chatmc/common/network/NetworkAgentErrorBehaviorTest.java`
  - `base/common-1.20.1/src/test/java/space/controlnet/chatmc/common/network/NetworkProposalLifecycleBehaviorTest.java`
  - `base/common-1.20.1/src/test/java/space/controlnet/chatmc/common/session/SavedDataReloadBehaviorTest.java`
  - `base/forge-1.20.1/src/main/java/space/controlnet/chatmc/forge/gametest/IndexingGateRecoveryGameTest.java`

Build/Lint/Test summary: Unit + Fabric suites pass with XML artifacts; Forge GameTest runtime is blocked by `Illegal version number specified version (main)` and `Failed to find system mod: minecraft` (`.sisyphus/evidence/f2-base-forge-runGameTestServer-timeboxed.log`) | Flake risks: no implicit sleeps, bounded wait patterns, residual medium shared-global-state coupling risk | VERDICT: BLOCKED (Forge runtime blocker persists)
