# Fabric-vs-Forge GameTest Parity Report (Task 18)

## Summary
- Matched: 6
- Missing: 1
- Wrapper-only: 1
- Runtime-blocked: 7
- Forge runtime blocked in workspace: **true**

## Discovered Fabric testcases
- `mineagentfabricgametestentrypoint.smokebootstrap` (base-fabric, status=passed, time=0.057s)
- `mineagentfabricgametestentrypoint.baseproposalbindingunavailable` (base-fabric, status=passed, time=0.062s)
- `mineagentfabricgametestentrypoint.baseindexinggaterecovery` (base-fabric, status=passed, time=0.049s)
- `mineagentfabricgametestentrypoint.baseviewerchurnconsistency` (base-fabric, status=passed, time=0.035s)
- `mineagentfabricgametestentrypoint.baseserverthreadconfinement` (base-fabric, status=passed, time=0.028s)
- `mineagentfabricgametestentrypoint.basetoolargsboundarye2e` (base-fabric, status=passed, time=0.02s)
- `mineagentaefabricgametestentrypoint.craftlifecycleisolation` (ext-ae-fabric, status=passed, time=0.057s)

## Expected Forge scenario set
- `IndexingGateRecoveryGameTest::indexingGateRecoveryAcrossReload` (base-forge, batch=mineagent)
- `ProposalBindingUnavailableGameTest::proposalBindingUnavailableApprovalFailsDeterministically` (base-forge, batch=mineagent)
- `ServerThreadConfinementGameTest::asyncToolInvocationMarshalsToServerThread` (base-forge, batch=mineagent)
- `ServerThreadConfinementGameTest::timeoutAndFailureContractsRemainStableUnderForcedDelay` (base-forge, batch=mineagent)
- `ToolArgsBoundaryEndToEndGameTest::toolArgsBoundaryEndToEnd_65535_65536_65537_withUtfCorpus` (base-forge, batch=mineagent)
- `ViewerChurnConsistencyGameTest::multiViewerSnapshotConsistencyUnderChurn` (base-forge, batch=mineagent)
- `AeCraftLifecycleIsolationGameTest::craftLifecycleIsolation_beginSuccessFailure_withoutCrossTerminalLeakage` (ext-ae-forge, batch=mineagentae)

## Category: matched
- `ProposalBindingUnavailableGameTest::proposalBindingUnavailableApprovalFailsDeterministically` <- `mineagentfabricgametestentrypoint.baseproposalbindingunavailable` (wrapper `MineAgentFabricGameTestEntrypoint::baseProposalBindingUnavailable`, status=passed, similarity=0.429)
- `IndexingGateRecoveryGameTest::indexingGateRecoveryAcrossReload` <- `mineagentfabricgametestentrypoint.baseindexinggaterecovery` (wrapper `MineAgentFabricGameTestEntrypoint::baseIndexingGateRecovery`, status=passed, similarity=0.5)
- `ViewerChurnConsistencyGameTest::multiViewerSnapshotConsistencyUnderChurn` <- `mineagentfabricgametestentrypoint.baseviewerchurnconsistency` (wrapper `MineAgentFabricGameTestEntrypoint::baseViewerChurnConsistency`, status=passed, similarity=0.429)
- `ServerThreadConfinementGameTest::asyncToolInvocationMarshalsToServerThread` <- `mineagentfabricgametestentrypoint.baseserverthreadconfinement` (wrapper `MineAgentFabricGameTestEntrypoint::baseServerThreadConfinement`, status=passed, similarity=0.222)
- `ToolArgsBoundaryEndToEndGameTest::toolArgsBoundaryEndToEnd_65535_65536_65537_withUtfCorpus` <- `mineagentfabricgametestentrypoint.basetoolargsboundarye2e` (wrapper `MineAgentFabricGameTestEntrypoint::baseToolArgsBoundaryE2e`, status=passed, similarity=0.231)
- `AeCraftLifecycleIsolationGameTest::craftLifecycleIsolation_beginSuccessFailure_withoutCrossTerminalLeakage` <- `mineagentaefabricgametestentrypoint.craftlifecycleisolation` (wrapper `MineAgentAeFabricGameTestEntrypoint::craftLifecycleIsolation`, status=passed, similarity=0.3)

## Category: missing
- `ServerThreadConfinementGameTest::timeoutAndFailureContractsRemainStableUnderForcedDelay`

## Category: wrapper-only
- `MineAgentFabricGameTestEntrypoint::smokeBootstrap` (testcase `mineagentfabricgametestentrypoint.smokebootstrap`, status=passed)

## Category: runtime-blocked
Forge runtime execution is blocked by known signatures in this workspace:
- `InvalidModFileException: Illegal version number specified version \(main\)` in `.sisyphus/evidence/task-17-forge-base.log` (excerpt: `InvalidModFileException: Illegal version number specified version (main)`)
- `Failed to find system mod: minecraft` in `.sisyphus/evidence/task-17-forge-base.log` (excerpt: `Failed to find system mod: minecraft`)

## Actionable parity gaps
- Add Fabric wrappers (or loader-neutral shared provider wiring) for missing Forge scenarios, especially ServerThreadConfinementGameTest::timeoutAndFailureContractsRemainStableUnderForcedDelay.
- Keep wrapper-only entries explicit in parity dashboards (currently smoke bootstrap), and avoid counting them as Forge parity coverage.
- Unblock Forge runtime startup (`InvalidModFileException ... version (main)` + `Failed to find system mod: minecraft`) so Forge scenario execution status can be compared beyond wrapper-level coverage.
