# Fabric-vs-Forge GameTest Parity Report (Task 18)

## Summary
- Matched: 6
- Missing: 1
- Wrapper-only: 1
- Runtime-blocked: 7
- Forge runtime blocked in workspace: **true**

## Discovered Fabric testcases
- `chatmcfabricgametestentrypoint.smokebootstrap` (base-fabric, status=passed, time=0.06s)
- `chatmcfabricgametestentrypoint.baseindexinggaterecovery` (base-fabric, status=passed, time=0.098s)
- `chatmcfabricgametestentrypoint.baseviewerchurnconsistency` (base-fabric, status=passed, time=0.084s)
- `chatmcfabricgametestentrypoint.baseserverthreadconfinement` (base-fabric, status=passed, time=0.064s)
- `chatmcfabricgametestentrypoint.basetoolargsboundarye2e` (base-fabric, status=passed, time=0.049s)
- `chatmcfabricgametestentrypoint.baseproposalbindingunavailable` (base-fabric, status=passed, time=0.027s)
- `chatmcaefabricgametestentrypoint.craftlifecycleisolation` (ext-ae-fabric, status=passed, time=0.055s)

## Expected Forge scenario set
- `IndexingGateRecoveryGameTest::indexingGateRecoveryAcrossReload` (base-forge, batch=chatmc)
- `ProposalBindingUnavailableGameTest::proposalBindingUnavailableApprovalFailsDeterministically` (base-forge, batch=chatmc)
- `ServerThreadConfinementGameTest::asyncToolInvocationMarshalsToServerThread` (base-forge, batch=chatmc)
- `ServerThreadConfinementGameTest::timeoutAndFailureContractsRemainStableUnderForcedDelay` (base-forge, batch=chatmc)
- `ToolArgsBoundaryEndToEndGameTest::toolArgsBoundaryEndToEnd_65535_65536_65537_withUtfCorpus` (base-forge, batch=chatmc)
- `ViewerChurnConsistencyGameTest::multiViewerSnapshotConsistencyUnderChurn` (base-forge, batch=chatmc)
- `AeCraftLifecycleIsolationGameTest::craftLifecycleIsolation_beginSuccessFailure_withoutCrossTerminalLeakage` (ext-ae-forge, batch=chatmcae)

## Category: matched
- `ProposalBindingUnavailableGameTest::proposalBindingUnavailableApprovalFailsDeterministically` <- `chatmcfabricgametestentrypoint.baseproposalbindingunavailable` (wrapper `ChatMCFabricGameTestEntrypoint::baseProposalBindingUnavailable`, status=passed, similarity=0.429)
- `IndexingGateRecoveryGameTest::indexingGateRecoveryAcrossReload` <- `chatmcfabricgametestentrypoint.baseindexinggaterecovery` (wrapper `ChatMCFabricGameTestEntrypoint::baseIndexingGateRecovery`, status=passed, similarity=0.5)
- `ViewerChurnConsistencyGameTest::multiViewerSnapshotConsistencyUnderChurn` <- `chatmcfabricgametestentrypoint.baseviewerchurnconsistency` (wrapper `ChatMCFabricGameTestEntrypoint::baseViewerChurnConsistency`, status=passed, similarity=0.429)
- `ServerThreadConfinementGameTest::asyncToolInvocationMarshalsToServerThread` <- `chatmcfabricgametestentrypoint.baseserverthreadconfinement` (wrapper `ChatMCFabricGameTestEntrypoint::baseServerThreadConfinement`, status=passed, similarity=0.222)
- `ToolArgsBoundaryEndToEndGameTest::toolArgsBoundaryEndToEnd_65535_65536_65537_withUtfCorpus` <- `chatmcfabricgametestentrypoint.basetoolargsboundarye2e` (wrapper `ChatMCFabricGameTestEntrypoint::baseToolArgsBoundaryE2e`, status=passed, similarity=0.231)
- `AeCraftLifecycleIsolationGameTest::craftLifecycleIsolation_beginSuccessFailure_withoutCrossTerminalLeakage` <- `chatmcaefabricgametestentrypoint.craftlifecycleisolation` (wrapper `ChatMCAeFabricGameTestEntrypoint::craftLifecycleIsolation`, status=passed, similarity=0.3)

## Category: missing
- `ServerThreadConfinementGameTest::timeoutAndFailureContractsRemainStableUnderForcedDelay`

## Category: wrapper-only
- `ChatMCFabricGameTestEntrypoint::smokeBootstrap` (testcase `chatmcfabricgametestentrypoint.smokebootstrap`, status=passed)

## Category: runtime-blocked
Forge runtime execution is blocked by known signatures in this workspace:
- `InvalidModFileException: Illegal version number specified version \(main\)` in `ci-reports/parity/forge-blocker.log` (excerpt: `InvalidModFileException: Illegal version number specified version (main)`)
- `Failed to find system mod: minecraft` in `ci-reports/parity/forge-blocker.log` (excerpt: `Failed to find system mod: minecraft`)

## Actionable parity gaps
- Add Fabric wrappers (or loader-neutral shared provider wiring) for missing Forge scenarios, especially ServerThreadConfinementGameTest::timeoutAndFailureContractsRemainStableUnderForcedDelay.
- Keep wrapper-only entries explicit in parity dashboards (currently smoke bootstrap), and avoid counting them as Forge parity coverage.
- Unblock Forge runtime startup (`InvalidModFileException ... version (main)` + `Failed to find system mod: minecraft`) so Forge scenario execution status can be compared beyond wrapper-level coverage.
