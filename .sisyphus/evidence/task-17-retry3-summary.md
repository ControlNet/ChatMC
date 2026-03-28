## Task 17 Retry #3 Summary

### Culprit proof (`version (main)`)
- Base debug repro (`task-17-forge-base-retry3-debug.log`):
  - `CommonLaunchHandler Got mod coordinates ... /base/forge-1.20.1/bin/main`
  - `InvalidModFileException: Illegal version number specified version (main)`
- Ext-AE debug repro (`task-17-forge-ext-ae-retry3-debug-after-fix.log`):
  - `CommonLaunchHandler Got mod coordinates ... /ext-ae/forge-1.20.1/bin/main`
  - `InvalidModFileException: Illegal version number specified version (main)`

### Applied runtime wiring changes
- `base/forge-1.20.1/build.gradle`
- `ext-ae/forge-1.20.1/build.gradle`

### Post-fix evidence
- Base final run (`task-17-forge-base-final2.log`):
  - No `InvalidModFileException ... version (main)`
  - No `Failed to find system mod: minecraft`
  - `LaunchServiceHandler Launching target 'forgeserveruserdev'`
  - `DedicatedServer: Done (...)`
  - `ForgeGameTestHooks: Enabled Gametest Namespaces: [chatmc]`
- Ext-AE final run (`task-17-forge-ext-ae-final2.log`):
  - No `InvalidModFileException ... version (main)`
  - No `Failed to find system mod: minecraft`
  - `LaunchServiceHandler Launching target 'forgeserveruserdev'`
  - `DedicatedServer: Starting minecraft server version 1.20.1`

### Residual execution constraint
- In this automation shell, `runGameTestServer` leaves lingering `TransformerRuntime` processes across attempts.
- Without explicit cleanup, follow-up runs can fail on `session.lock already locked` or `Address already in use` (25565).
