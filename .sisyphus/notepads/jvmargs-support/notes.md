# JVM Args Support Fix
- Replaced the existing runGameTestServer JVM-arg mutation with an immutable filter `(jvmArgs ?: [])` for both base/forge-1.20.1 and ext-ae/forge-1.20.1 so the arguments no longer mutate.
- Restored the forgeLaunchTargetArgs + JavaExec configuration to ensure runGameTestServer uses the proper launch target and argument providers.
- `./gradlew :base:forge-1.20.1:runGameTestServer` still crashes because `gameteststructures/empty.snbt` is missing in the generated data packs, so the crash isn't related to JVM argument handling.
