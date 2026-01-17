# ChatAE (Minecraft 1.20.1)

ChatAE is an Applied Energistics 2 (AE2) addon mod built with Architectury (multi-loader).

## Requirements

- **Java 17 JDK** (recommended as your `JAVA_HOME`)
- Internet access (Gradle will download Minecraft, mappings, and dependencies)

> Note: Minecraft 1.20.1 (and Forge for 1.20.1) expects **Java 17**.  
> If your system default `java` is newer (e.g. Java 25), this repo configures Gradle **toolchains** to still run compilation and Loom run tasks on Java 17.

## Build (PowerShell)

Run from the repo root:

```powershell
.\gradlew.bat build
```

## Run dev client

### Forge

```powershell
.\gradlew.bat :forge-1.20.1:runClient
```

### Fabric

```powershell
.\gradlew.bat :fabric-1.20.1:runClient
```

## Common Windows pitfall

- Do **not** run `gradlew.bat` via WSL (e.g. `bash .\gradlew.bat ...`).  
  Use PowerShell / CMD as shown above.

