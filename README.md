# Flight HUD

__Flight HUD__ is a client side Fabric Minecraft Mod that adds a flight style HUD like one you would see in a flight simulator.

![HUD Screenshot](images/hud2020-07-07.png?raw=true "HUD Screenshot")

## Features
- pitch ladder
- artificial horizon
- flight path (prograde vector)
- air speed with sliding scale
- heading with sliding scale
- direction ordinals and axes
- altitude with sliding scale (relative to block zero)
- height (distance from ground)
- elevation summary showing
  - void damage mark
  - block zero
  - ground level
  - build limit
  - current altitude
- elytra health percentage
- location in x/z


![HUD Components](images/hud-diagram.png?raw=true "HUD Components")

## Requirements
- Fabric Mod Loader (or **Quilt** — Quilt loads standard Fabric mod jars
  natively, so any `*-fabric` build of this mod works unmodified under Quilt
  with no separate build)
- Fabric API Mod
- One of the Minecraft versions in the build matrix below

## Supported versions and loaders

This mod is built with [Stonecutter](https://stonecutter.kikugie.dev/) +
[Stonecraft](https://stonecraft.meza.gg/) from a single codebase. All ten
cells build and pass their test suites — see `PLAN.md` for per-cell notes
and `CLAUDE.md` for architecture/porting detail.

| MC version | Fabric | NeoForge | Forge |
|---|---|---|---|
| 26.2 | ✅ | ✅ | — (NeoForge only, post-split) |
| 1.21.4 | ✅ | ✅ | — |
| 1.20.1 | ✅ | — (NeoForge doesn't exist before 1.20.2) | ✅ |
| 1.19.4 | ✅ | — | ✅ |
| 1.18.2 | ✅ | — | ✅ |

Quilt users: install any `*-fabric` jar for your Minecraft version — no
Quilt-specific build exists or is needed.

## Testing

Three tiers, all run by CI ([build.yml](.github/workflows/build.yml)):

1. **Unit tests + coverage gate** — JUnit 5 over the HUD's pure math
   (pitch/heading/altitude scales, layout), with a JaCoCo **100%
   line-coverage gate** enforced by `./gradlew check`. Runs in **every one
   of the ten version×loader builds**.
2. **Loaded-game tests** (`fabric-loader-junit`) — a real bootstrapped
   Minecraft and a real Fabric loader validate the processed mod metadata,
   dependency ranges, and agreement with live game values, on every Fabric
   cell.
3. **Client gametests** (`fabric-client-gametest-api-v1`, 1.21.4 + 26.2) —
   a **real Minecraft client** flies a real Elytra and the test asserts on
   the actual screen pixels the HUD produced. This is the only tier that
   can see the rendering code at all. CI runs it headless (xvfb + software
   GL) and uploads the screenshots as artifacts.

Full write-up, including negative controls and the JaCoCo exclusion table,
is in `PLAN.md` and `CLAUDE.md`.

## Building from source

```bash
unset JAVA_HOME            # Gradle's toolchain resolver picks per-cell JDKs
./gradlew chiseledBuild    # build + test every cell
./gradlew :1.21.4-fabric:build          # one cell
```
