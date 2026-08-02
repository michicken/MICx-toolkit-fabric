# MICx Toolkit Fabric

MICx Toolkit for Minecraft 26.2 Fabric. This repository is maintained separately from the Minecraft 1.8.9 Forge project so both versions can evolve independently.

## Requirements

- Minecraft 26.2
- Fabric Loader 0.19.3 or newer
- Fabric API 0.155.2+26.2 or newer
- Java 25

## Build

```bash
GRADLE_USER_HOME=/tmp/micx-gradle-home ./gradlew clean build --no-daemon
```

The output JAR is written to `build/libs/`.

## Local configuration

The ASR and translation integrations read credentials from environment variables. Credentials are not stored in this repository:

```bash
export MICX_STEP_API_KEY='...'
export MICX_DEEPSEEK_API_KEY='...'
```

Do not commit credentials, runtime logs, generated files, or Prism instance data.

## Deployment

Copy the built JAR to the Minecraft 26.2 Fabric instance `mods/` directory after closing Minecraft and PrismLauncher. Keep only one active `micx-fabric-*.jar` for the instance.

## Scope

This repository contains the Fabric 26.2 client. The original Minecraft 1.8.9 Forge implementation remains in the parent `MICx-toolkit` repository.
