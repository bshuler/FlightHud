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
[Stonecraft](https://stonecraft.meza.gg/) across the following targets. Every
cell is a real build target, not aspirational — see `PLAN.md` for live status
and `CLAUDE.md` for architecture/porting notes.

| MC version | Fabric | NeoForge | Forge |
|---|---|---|---|
| newest stable | target | target | n/a — not targeted for the newest two versions (program scope) |
| 1.21.4 | target | target | n/a — not targeted for the newest two versions (program scope) |
| 1.20.1 | target | n/a — NeoForge doesn't exist before 1.20.2 | target |
| 1.19.4 | target | n/a — NeoForge doesn't exist before 1.20.2 | target |
| 1.18.2 | target | n/a — NeoForge doesn't exist before 1.20.2 | target |

Quilt users: install any `*-fabric` jar for your Minecraft version — no
Quilt-specific build exists or is needed.
