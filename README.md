# MineAgent (Minecraft 1.20.1)

MineAgent is a Minecraft AI assistant mod built with Architectury (multi-loader). It provides an in-game chat interface to an LLM-powered agent that can search recipes, query inventories, and execute actions.

## Status

- The MineAgent rename and base/extension split are now the current repository state.
- Release artifacts are built via `./scripts/build-dist.sh` and published from `master` by `.github/workflows/release.yml`.
- For detailed runtime verification and CI lane policy, see `REPO.md` and `docs/layered-testing-ci.md`.

## Project Structure

This is a multi-mod project with a base mod and optional extensions:

| Mod | modid | Description |
|-----|-------|-------------|
| **MineAgent** (base) | `mineagent` | Core agent, UI, vanilla `mc.*` tools |
| **MineAgent AE** | `mineagentae` | AE2 integration: `ae.*` tools, AI Terminal part |
| **MineAgent Matrix** | `mineagentmatrix` | Matrix bridge (scaffolded) |

### Module Layout

```
/base                 # MineAgent base mod
  /core               # Pure domain logic (no MC deps)
  /common-1.20.1      # Shared MC 1.20.1 code
  /forge-1.20.1       # Forge loader
  /fabric-1.20.1      # Fabric loader

/ext-ae               # MineAgent AE extension
  /core               # AE2 tool schemas (no MC deps)
  /common-1.20.1      # AE2 tools, terminal part
  /forge-1.20.1       # Forge loader
  /fabric-1.20.1      # Fabric loader

/ext-matrix           # MineAgent Matrix extension
  /core               # Matrix configs (no MC deps)
  /common-1.20.1      # Matrix bridge
  /forge-1.20.1       # Forge loader
  /fabric-1.20.1      # Fabric loader
```

## Requirements

- **Java 17 JDK** (recommended as your `JAVA_HOME`)
- Internet access (Gradle will download Minecraft, mappings, and dependencies)

> Note: Minecraft 1.20.1 (and Forge for 1.20.1) expects **Java 17**.  
> If your system default `java` is newer (e.g. Java 25), this repo configures Gradle **toolchains** to still run compilation and Loom run tasks on Java 17.

## Build

### All modules

```bash
./gradlew build
```

### Specific module

```bash
./gradlew :base:forge-1.20.1:build
./gradlew :ext-ae:fabric-1.20.1:build
```

## Run dev client

### Base mod only

```bash
# Forge
./gradlew :base:forge-1.20.1:runClient

# Fabric
./gradlew :base:fabric-1.20.1:runClient
```

### With AE2 extension

```bash
# Forge (includes AE2 + GuideMe)
./gradlew :ext-ae:forge-1.20.1:runClient

# Fabric (includes AE2)
./gradlew :ext-ae:fabric-1.20.1:runClient
```

### With Matrix extension

```bash
# Forge
./gradlew :ext-matrix:forge-1.20.1:runClient

# Fabric
./gradlew :ext-matrix:fabric-1.20.1:runClient
```

## Output jars

After building, jars are located in each module's `build/libs/` directory:

- `base/forge-1.20.1/build/libs/mineagent-<ver>-forge-1.20.1.jar`
- `base/fabric-1.20.1/build/libs/mineagent-<ver>-fabric-1.20.1.jar`
- `ext-ae/forge-1.20.1/build/libs/mineagentae-<ver>-forge-1.20.1.jar`
- `ext-ae/fabric-1.20.1/build/libs/mineagentae-<ver>-fabric-1.20.1.jar`
- `ext-matrix/forge-1.20.1/build/libs/mineagentmatrix-<ver>-forge-1.20.1.jar`
- `ext-matrix/fabric-1.20.1/build/libs/mineagentmatrix-<ver>-fabric-1.20.1.jar`

## Dependencies

| Extension | Required Mods |
|-----------|---------------|
| mineagent (base) | None |
| mineagentae | mineagent, ae2 |
| mineagentmatrix | mineagent |

## License

AGPL-3.0
