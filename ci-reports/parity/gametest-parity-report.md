# Fabric-vs-Forge GameTest Parity Report (Task 18)

## Summary
- Matched: 19
- Missing: 0
- Wrapper-only: 0
- Runtime-blocked: 0
- Forge runtime blocked in workspace: **false**

## Registered Fabric wrapper testcases
- `mineagentfabricgametestentrypoint.commandmenuopencloselifecyclecleanup` (base-fabric, batch=mineagent_runtime, runtime_status=passed, reported_in_xml=true)
- `mineagentfabricgametestentrypoint.basereloadcommandsmoke` (base-fabric, batch=mineagent_runtime, runtime_status=passed, reported_in_xml=true)
- `mineagentfabricgametestentrypoint.basedeletelastactivesessionfallback` (base-fabric, batch=mineagent_runtime, runtime_status=passed, reported_in_xml=true)
- `mineagentfabricgametestentrypoint.basemenuvalidityruntime` (base-fabric, batch=mineagent_runtime, runtime_status=passed, reported_in_xml=true)
- `mineagentfabricgametestentrypoint.basesessionvisibilitysessionlistcycle` (base-fabric, batch=mineagent_runtime, runtime_status=passed, reported_in_xml=true)
- `mineagentfabricgametestentrypoint.baseproposalbindingunavailable` (base-fabric, batch=mineagent, runtime_status=passed, reported_in_xml=true)
- `mineagentfabricgametestentrypoint.baseindexinggaterecovery` (base-fabric, batch=mineagent, runtime_status=passed, reported_in_xml=true)
- `mineagentfabricgametestentrypoint.baseviewerchurnconsistency` (base-fabric, batch=mineagent_task8_viewer, runtime_status=passed, reported_in_xml=true)
- `mineagentfabricgametestentrypoint.baseserverthreadconfinement` (base-fabric, batch=mineagent, runtime_status=passed, reported_in_xml=true)
- `mineagentfabricgametestentrypoint.basetimeoutfailurecontractunderforceddelay` (base-fabric, batch=mineagent_task9_timeout, runtime_status=passed, reported_in_xml=true)
- `mineagentfabricgametestentrypoint.basetoolargsboundarye2e` (base-fabric, batch=mineagent, runtime_status=xml_missing, reported_in_xml=false)
- `mineagentfabricgametestentrypoint.basesessionvisibilitydeleterebindruntime` (base-fabric, batch=mineagent_runtime, runtime_status=passed, reported_in_xml=true)
- `mineagentfabricgametestentrypoint.deletedsessionqueuedappenddoesnotrecreate` (base-fabric, batch=mineagent_runtime, runtime_status=passed, reported_in_xml=true)
- `mineagentfabricgametestentrypoint.baseagentsystemreliability` (base-fabric, batch=mineagent_agent_runtime, runtime_status=passed, reported_in_xml=true)
- `mineagentaefabricgametestentrypoint.craftlifecycleisolation` (ext-ae-fabric, batch=ae_smoke_craft, runtime_status=passed, reported_in_xml=true)
- `mineagentaefabricgametestentrypoint.aeboundterminalapprovalsuccesshandoff` (ext-ae-fabric, batch=ae_smoke_approval_success, runtime_status=passed, reported_in_xml=true)
- `mineagentaefabricgametestentrypoint.aeterminalteardownclearslivejobs` (ext-ae-fabric, batch=ae_smoke_teardown, runtime_status=passed, reported_in_xml=true)
- `mineagentaefabricgametestentrypoint.aebindinginvalidationafterterminalremovalorwrongside` (ext-ae-fabric, batch=ae_smoke_invalidation, runtime_status=passed, reported_in_xml=true)
- `mineagentaefabricgametestentrypoint.aecputargetedunavailablecpubranch` (ext-ae-fabric, batch=ae_smoke_cpu, runtime_status=passed, reported_in_xml=true)

## Fabric XML reported testcases
- `mineagentfabricgametestentrypoint.commandmenuopencloselifecyclecleanup` (base-fabric, status=passed, time=0.254s)
- `mineagentfabricgametestentrypoint.basedeletelastactivesessionfallback` (base-fabric, status=passed, time=0.26s)
- `mineagentfabricgametestentrypoint.basereloadcommandsmoke` (base-fabric, status=passed, time=0.291s)
- `mineagentfabricgametestentrypoint.basemenuvalidityruntime` (base-fabric, status=passed, time=0.289s)
- `mineagentfabricgametestentrypoint.basesessionvisibilitysessionlistcycle` (base-fabric, status=passed, time=0.279s)
- `mineagentfabricgametestentrypoint.basesessionvisibilitydeleterebindruntime` (base-fabric, status=passed, time=0.282s)
- `mineagentfabricgametestentrypoint.deletedsessionqueuedappenddoesnotrecreate` (base-fabric, status=passed, time=0.287s)
- `mineagentfabricgametestentrypoint.baseagentsystemreliability` (base-fabric, status=passed, time=0.454s)
- `mineagentfabricgametestentrypoint.basetimeoutfailurecontractunderforceddelay` (base-fabric, status=passed, time=31.563s)
- `mineagentfabricgametestentrypoint.baseviewerchurnconsistency` (base-fabric, status=passed, time=0.023s)
- `mineagentfabricgametestentrypoint.baseproposalbindingunavailable` (base-fabric, status=passed, time=0.045s)
- `mineagentfabricgametestentrypoint.baseindexinggaterecovery` (base-fabric, status=passed, time=0.099s)
- `mineagentfabricgametestentrypoint.baseserverthreadconfinement` (base-fabric, status=passed, time=0.107s)
- `mineagentaefabricgametestentrypoint.aecputargetedunavailablecpubranch` (ext-ae-fabric, status=passed, time=0.177s)
- `mineagentaefabricgametestentrypoint.aeterminalteardownclearslivejobs` (ext-ae-fabric, status=passed, time=0.024s)
- `mineagentaefabricgametestentrypoint.craftlifecycleisolation` (ext-ae-fabric, status=passed, time=0.026s)
- `mineagentaefabricgametestentrypoint.aebindinginvalidationafterterminalremovalorwrongside` (ext-ae-fabric, status=passed, time=0.023s)
- `mineagentaefabricgametestentrypoint.aeboundterminalapprovalsuccesshandoff` (ext-ae-fabric, status=passed, time=0.041s)

## Expected Forge scenario set
- `AgentSystemReliabilityGameTest::agentSystemReliability` (base-forge, batch=mineagent_agent_runtime)
- `CommandMenuLifecycleGameTest::commandMenuOpenCloseLifecycleCleanup` (base-forge, batch=mineagent_runtime)
- `DeleteLastActiveSessionFallbackGameTest::deleteLastActiveSessionFallbackCreatesNewSession` (base-forge, batch=mineagent_runtime)
- `DeletedSessionQueuedAppendGameTest::deletedSessionQueuedAppendDoesNotRecreate` (base-forge, batch=mineagent_runtime)
- `IndexingGateRecoveryGameTest::indexingGateRecoveryAcrossReload` (base-forge, batch=mineagent)
- `MenuValidityRuntimeGameTest::menuValidityTracksRealHostLivenessConditions` (base-forge, batch=mineagent_runtime)
- `ProposalBindingUnavailableGameTest::proposalBindingUnavailableApprovalFailsDeterministically` (base-forge, batch=mineagent)
- `ReloadCommandSmokeGameTest::reloadCommandSmokeRebuildsRecipeIndex` (base-forge, batch=mineagent_runtime)
- `ServerThreadConfinementGameTest::asyncToolInvocationMarshalsToServerThread` (base-forge, batch=mineagent)
- `ServerThreadConfinementGameTest::timeoutAndFailureContractsRemainStableUnderForcedDelay` (base-forge, batch=mineagent_task9_timeout)
- `SessionVisibilityDeleteRebindGameTest::sessionVisibilityDeleteRebindUnderRuntimeConditions` (base-forge, batch=mineagent_runtime)
- `SessionVisibilitySessionListCycleGameTest::sessionVisibilitySessionListUpdateCycle` (base-forge, batch=mineagent_runtime)
- `ToolArgsBoundaryEndToEndGameTest::toolArgsBoundaryEndToEnd_65535_65536_65537_withUtfCorpus` (base-forge, batch=mineagent)
- `ViewerChurnConsistencyGameTest::multiViewerSnapshotConsistencyUnderChurn` (base-forge, batch=mineagent_task8_viewer)
- `AeBindingInvalidationGameTest::aeBindingInvalidationAfterTerminalRemovalOrWrongSide` (ext-ae-forge, batch=mineagentae)
- `AeBoundTerminalApprovalSuccessGameTest::aeBoundTerminalApprovalSuccessHandoff` (ext-ae-forge, batch=mineagentae)
- `AeCpuUnavailableGameTest::aeCpuTargetedUnavailableCpuBranch` (ext-ae-forge, batch=mineagentae)
- `AeCraftLifecycleIsolationGameTest::craftLifecycleIsolation_beginSuccessFailure_withoutCrossTerminalLeakage` (ext-ae-forge, batch=mineagentae)
- `AeTerminalTeardownLiveJobsGameTest::aeTerminalTeardownClearsLiveJobs` (ext-ae-forge, batch=mineagentae)

## Category: matched
- `CommandMenuLifecycleGameTest::commandMenuOpenCloseLifecycleCleanup` <- `mineagentfabricgametestentrypoint.commandmenuopencloselifecyclecleanup` (wrapper `MineAgentFabricGameTestEntrypoint::commandMenuOpenCloseLifecycleCleanup`, status=passed, similarity=1.0)
- `ReloadCommandSmokeGameTest::reloadCommandSmokeRebuildsRecipeIndex` <- `mineagentfabricgametestentrypoint.basereloadcommandsmoke` (wrapper `MineAgentFabricGameTestEntrypoint::baseReloadCommandSmoke`, status=passed, similarity=0.429)
- `DeleteLastActiveSessionFallbackGameTest::deleteLastActiveSessionFallbackCreatesNewSession` <- `mineagentfabricgametestentrypoint.basedeletelastactivesessionfallback` (wrapper `MineAgentFabricGameTestEntrypoint::baseDeleteLastActiveSessionFallback`, status=passed, similarity=0.625)
- `MenuValidityRuntimeGameTest::menuValidityTracksRealHostLivenessConditions` <- `mineagentfabricgametestentrypoint.basemenuvalidityruntime` (wrapper `MineAgentFabricGameTestEntrypoint::baseMenuValidityRuntime`, status=passed, similarity=0.222)
- `SessionVisibilitySessionListCycleGameTest::sessionVisibilitySessionListUpdateCycle` <- `mineagentfabricgametestentrypoint.basesessionvisibilitysessionlistcycle` (wrapper `MineAgentFabricGameTestEntrypoint::baseSessionVisibilitySessionListCycle`, status=passed, similarity=0.667)
- `ProposalBindingUnavailableGameTest::proposalBindingUnavailableApprovalFailsDeterministically` <- `mineagentfabricgametestentrypoint.baseproposalbindingunavailable` (wrapper `MineAgentFabricGameTestEntrypoint::baseProposalBindingUnavailable`, status=passed, similarity=0.429)
- `IndexingGateRecoveryGameTest::indexingGateRecoveryAcrossReload` <- `mineagentfabricgametestentrypoint.baseindexinggaterecovery` (wrapper `MineAgentFabricGameTestEntrypoint::baseIndexingGateRecovery`, status=passed, similarity=0.5)
- `ViewerChurnConsistencyGameTest::multiViewerSnapshotConsistencyUnderChurn` <- `mineagentfabricgametestentrypoint.baseviewerchurnconsistency` (wrapper `MineAgentFabricGameTestEntrypoint::baseViewerChurnConsistency`, status=passed, similarity=0.429)
- `ServerThreadConfinementGameTest::asyncToolInvocationMarshalsToServerThread` <- `mineagentfabricgametestentrypoint.baseserverthreadconfinement` (wrapper `MineAgentFabricGameTestEntrypoint::baseServerThreadConfinement`, status=passed, similarity=0.222)
- `ServerThreadConfinementGameTest::timeoutAndFailureContractsRemainStableUnderForcedDelay` <- `mineagentfabricgametestentrypoint.basetimeoutfailurecontractunderforceddelay` (wrapper `MineAgentFabricGameTestEntrypoint::baseTimeoutFailureContractUnderForcedDelay`, status=passed, similarity=0.455)
- `ToolArgsBoundaryEndToEndGameTest::toolArgsBoundaryEndToEnd_65535_65536_65537_withUtfCorpus` <- `mineagentfabricgametestentrypoint.basetoolargsboundarye2e` (wrapper `MineAgentFabricGameTestEntrypoint::baseToolArgsBoundaryE2e`, status=xml_missing, similarity=0.231)
- `SessionVisibilityDeleteRebindGameTest::sessionVisibilityDeleteRebindUnderRuntimeConditions` <- `mineagentfabricgametestentrypoint.basesessionvisibilitydeleterebindruntime` (wrapper `MineAgentFabricGameTestEntrypoint::baseSessionVisibilityDeleteRebindRuntime`, status=passed, similarity=0.625)
- `DeletedSessionQueuedAppendGameTest::deletedSessionQueuedAppendDoesNotRecreate` <- `mineagentfabricgametestentrypoint.deletedsessionqueuedappenddoesnotrecreate` (wrapper `MineAgentFabricGameTestEntrypoint::deletedSessionQueuedAppendDoesNotRecreate`, status=passed, similarity=1.0)
- `AgentSystemReliabilityGameTest::agentSystemReliability` <- `mineagentfabricgametestentrypoint.baseagentsystemreliability` (wrapper `MineAgentFabricGameTestEntrypoint::baseAgentSystemReliability`, status=passed, similarity=0.75)
- `AeCraftLifecycleIsolationGameTest::craftLifecycleIsolation_beginSuccessFailure_withoutCrossTerminalLeakage` <- `mineagentaefabricgametestentrypoint.craftlifecycleisolation` (wrapper `MineAgentAeFabricGameTestEntrypoint::craftLifecycleIsolation`, status=passed, similarity=0.3)
- `AeBoundTerminalApprovalSuccessGameTest::aeBoundTerminalApprovalSuccessHandoff` <- `mineagentaefabricgametestentrypoint.aeboundterminalapprovalsuccesshandoff` (wrapper `MineAgentAeFabricGameTestEntrypoint::aeBoundTerminalApprovalSuccessHandoff`, status=passed, similarity=1.0)
- `AeTerminalTeardownLiveJobsGameTest::aeTerminalTeardownClearsLiveJobs` <- `mineagentaefabricgametestentrypoint.aeterminalteardownclearslivejobs` (wrapper `MineAgentAeFabricGameTestEntrypoint::aeTerminalTeardownClearsLiveJobs`, status=passed, similarity=1.0)
- `AeBindingInvalidationGameTest::aeBindingInvalidationAfterTerminalRemovalOrWrongSide` <- `mineagentaefabricgametestentrypoint.aebindinginvalidationafterterminalremovalorwrongside` (wrapper `MineAgentAeFabricGameTestEntrypoint::aeBindingInvalidationAfterTerminalRemovalOrWrongSide`, status=passed, similarity=1.0)
- `AeCpuUnavailableGameTest::aeCpuTargetedUnavailableCpuBranch` <- `mineagentaefabricgametestentrypoint.aecputargetedunavailablecpubranch` (wrapper `MineAgentAeFabricGameTestEntrypoint::aeCpuTargetedUnavailableCpuBranch`, status=passed, similarity=1.0)

## Category: missing

## Category: wrapper-only

## Category: runtime-blocked
- No known Forge runtime blocker signatures detected.

## Actionable parity gaps
- Fabric GameTest XML did not report every registered wrapper testcase in this workspace, so parity inventory uses Fabric wrapper registration as the authoritative testcase set and XML only as supplemental runtime status.
