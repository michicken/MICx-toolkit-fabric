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

## Rendering boundaries

- `esp` is the non-player line/shape-outline path and uses `EspRenderTypes`; it does not render textured models.
- `player_outline_esp` uses the native outline phase for player silhouettes.
- `chams` is a separate textured model pass. It replaces only the vanilla body model submit for a target that is blocked by a block ray hit, reusing the vanilla model, texture, animation, lightmap, and overlay. It does not call the entity renderer a second time and does not reuse the ESP line pipeline.
- Armor, cape, held-item, eyes, name-tag, and shadow layers remain on their vanilla layer paths in this first pass.
- The Fabric Chams path is `PORTING/UNVERIFIED` until Prism testing covers a wall-occluded target, an unoccluded target, self-occlusion, layers, world switching, and disable cleanup. `ALWAYS_PASS` with depth writes disabled is not equivalent to Forge's `glDepthRange` behavior.
- ZombiesAssist now hides the vanilla BossBar and sidebar only while its replacement HUD owns the Zombies session; `originalScoreboard=true` or a disabled economy panel preserves the vanilla sidebar. These 26.2 HUD hooks remain `PORTING/UNVERIFIED` until a live Prism session confirms the visual replacement.
- `/micx hs <1-3>` uses the Forge dispatch/queue protocol through a background HTTPS service, with legacy `MICxToolkit_Hs.cfg` fallback and clickable `/p` invites. The Fabric HTTP bearer now matches Forge (`Base64` of the complete signed token JSON), and failures are classified without exposing response bodies or credentials. The path remains `PORTING/UNVERIFIED` until a live `/micx hs 2` request confirms bot assignment.
- `KeyboardClickerModule` now includes the Forge ordinary anti-jam and downed recovery state machines: 150ms stable low-durability detection, native hotbar/left-click recovery, cooldown and cancellation guards, downed hotbar snapshot with per-gun recovery, special-round slot handling, and Alien Arcadium Round 1 reset. This is code/test complete but remains `PORTING/UNVERIFIED` until a real 26.2 session confirms input consumption and inventory timing.
- `HUD Layout` is available from the `/micx` panel. It previews the screen-space HUD blocks, writes dragged positions back to the owning module settings, supports a bottom-right handle for independent horizontal/vertical resizing, and persists layout multipliers in `config/MICxToolkit/hud-layout.properties`. The editor deliberately excludes TeamSync world pings and AimLead 3D prediction geometry. This path remains `PORTING/UNVERIFIED` until Prism verifies hitboxes, runtime placement/scale, persistence, and resize behavior.
- Built-in Fullbright is implemented for 26.2 through `LightmapRenderStateExtractor`: vanilla gamma is kept at its valid maximum (`1.0`), while the final lightmap state is brightened without using the invalid legacy `gamma=200` value. Darkness, night-vision, and boss-overlay state fields are preserved. This remains `PORTING/UNVERIFIED` until a restarted Prism session confirms night-time visuals.
- AimLead line geometry now supplies the 26.2 `LineWidth` vertex element required by `LINES_SNIPPET`, fixing the observed `Missing elements in vertex` crash. The fix is code/build verified but remains `PORTING/UNVERIFIED` until a live Prism session confirms the prediction line path.

## Scope

This repository contains the Fabric 26.2 client. The original Minecraft 1.8.9 Forge implementation remains in the parent `MICx-toolkit` repository.
