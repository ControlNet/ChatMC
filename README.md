# ChatMC (Minecraft 1.20.1)

ChatMC is a Minecraft AI assistant mod built with Architectury (multi-loader). It provides an in-game chat interface to an LLM-powered agent that can search recipes, query inventories, and execute actions.

## Project Structure

This is a multi-mod project with a base mod and optional extensions:

| Mod | modid | Description |
|-----|-------|-------------|
| **ChatMC** (base) | `chatmc` | Core agent, UI, vanilla `mc.*` tools |
| **ChatMC AE** | `chatmcae` | AE2 integration: `ae.*` tools, AI Terminal part |
| **ChatMC Matrix** | `chatmcmatrix` | Matrix bridge (scaffolded) |

### Module Layout

```
/base                 # ChatMC base mod
  /core               # Pure domain logic (no MC deps)
  /common-1.20.1      # Shared MC 1.20.1 code
  /forge-1.20.1       # Forge loader
  /fabric-1.20.1      # Fabric loader

/ext-ae               # ChatMC AE2 extension
  /core               # AE2 tool schemas (no MC deps)
  /common-1.20.1      # AE2 tools, terminal part
  /forge-1.20.1       # Forge loader
  /fabric-1.20.1      # Fabric loader

/ext-matrix           # ChatMC Matrix extension
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

- `base/forge-1.20.1/build/libs/chatmc-<ver>-forge-1.20.1.jar`
- `base/fabric-1.20.1/build/libs/chatmc-<ver>-fabric-1.20.1.jar`
- `ext-ae/forge-1.20.1/build/libs/chatmcae-<ver>-forge-1.20.1.jar`
- `ext-ae/fabric-1.20.1/build/libs/chatmcae-<ver>-fabric-1.20.1.jar`
- `ext-matrix/forge-1.20.1/build/libs/chatmcmatrix-<ver>-forge-1.20.1.jar`
- `ext-matrix/fabric-1.20.1/build/libs/chatmcmatrix-<ver>-fabric-1.20.1.jar`

## Dependencies

| Extension | Required Mods |
|-----------|---------------|
| chatmc (base) | None |
| chatmcae | chatmc, ae2 |
| chatmcmatrix | chatmc |

## License

AGPL-3.0
