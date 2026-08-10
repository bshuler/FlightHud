# FlightHud - Modernization Plan

## Goal

Every Bert mod working and compilable for all modern Minecraft versions and
loaders, latest first, then backwards. For FlightHud specifically: convert the
single-version (1.20.1-fabric) codebase to Stonecutter + `gg.meza.stonecraft`
(mirroring the `critical-orientation` house template), then port render code
latest-first until the target matrix builds green via `./gradlew chiseledBuild`.
Running the game client is not required — compile/build green is the bar.

## Upstream check (done before any porting)

- `upstream` (`end-user/FlightHud`) `master` == this fork's `master` at
  `8f2a448`. Upstream has not moved past 1.20.1; its other branches
  (`config-work`, `forge-1.16.1`, `forge-1.16.2`, `forge-1.17.1`) are old
  pre-1.20.1 side branches, not newer work. No tags/releases upstream.
- **Conclusion: nothing to merge/cherry-pick forward. All porting below is
  original work in this fork.**

## Newest stable Minecraft version (checked live, not from memory)

Checked `https://meta.fabricmc.net/v2/versions/game` on 2026-08-10:
top of the `stable: true` list is **`26.2`**.

Minecraft moved to year-based versioning in 2026 (`YY.N` — first release of a
year is `YY.1`, etc.). `26.1` (Dec 2025/early 2026) was the **first fully
unobfuscated** Java Edition release — Mojang ships real names, Fabric stopped
publishing Yarn/Intermediary mappings after `1.21.11`. `26.2` ("Chaos Cubed",
released 2026-06-16) is current stable as of this plan and is what "newest
stable" resolves to for the matrix below.

Verified buildable-in-principle: Fabric Loader `0.19.3`, Fabric API
`0.156.0+26.2` exist on Fabric's own Maven; NeoForge `26.2.0.58` exists on
NeoForge's Maven; `gg.meza.stonecraft` `1.12.6` (latest on the Gradle Plugin
Portal) advertises "first-class Mojmap support" for the post-obfuscation era.
Reference config: `github.com/FabricMC/fabric-example-mod` default branch
(tracks newest MC) — no `mappings {}` block, Java 25 compile target.

## Target matrix

| MC version | Fabric | NeoForge | Forge |
|---|---|---|---|
| 26.2 (newest stable) | ☐ | ☐ | n/a — NeoForge/Forge split, no Forge post-1.20.2 |
| 1.21.4 | ☐ | ☐ | n/a |
| 1.20.1 | ☐ | n/a | ☐ |
| 1.19.4 | ☐ | n/a | ☐ |
| 1.18.2 | ☐ | n/a | ☐ |

(☐ = not yet attempted, 🔧 = in progress, ✅ = `chiseledBuild` green, ❌ = attempted,
blocked — see notes with real error evidence, 🟡 = building with a documented
scope reduction.)

## Multi-loader scope decisions (see CLAUDE.md "Multi-loader scope decisions")

- `/flighthud toggle` chat command: **Fabric-only**. Equivalent functionality
  (toggle display mode) remains available via keybind on every loader. This is
  a deliberate descope for engineering economy, not a rendering-feasibility
  problem — Mixin-based HUD rendering itself is NOT loader-limited (NeoForge and
  Forge both support SpongePowered Mixin declaratively).

## Status checklist

### Phase 0 — Docs & repo hygiene
- [x] Read `critical-orientation` template (`settings.gradle.kts`,
      `stonecutter.gradle.kts`, `build.gradle.kts`, `gradle.properties`,
      `CLAUDE.md`, `PLAN.md`)
- [x] Confirm upstream has nothing newer to merge
- [x] Determine newest stable MC version live from Fabric meta API
- [x] Write `CLAUDE.md`
- [x] Write `PLAN.md` (this file)
- [ ] Commit docs
- [ ] Rename `master` → `main`, push, set default branch

### Phase 1 — Stonecutter scaffold
- [ ] `settings.gradle.kts`: `dev.kikugie.stonecutter` (latest: `0.9.7`) +
      `gg.meza.stonecraft` (latest: `1.12.6`), declare all matrix nodes
- [ ] `stonecutter.gradle.kts`: active node
- [ ] Root `build.gradle.kts` (centralScript)
- [ ] `gradle.properties`: mod metadata only
- [ ] Move `src/main` → shared Stonecutter tree; confirm single-tree (no
      per-version source forks) matches template
- [ ] Gradle wrapper bump (template uses 8.11.1; newest node may need newer —
      confirm against real build output)
- [ ] Baseline sanity: `./gradlew :1.20.1-fabric:build` green with **zero**
      source changes beyond what Stonecutter scaffolding requires

### Phase 2 — Port latest-first (Fabric only first, loaders after)
- [ ] 1.21.4-fabric: DrawContext/RenderLayer delta from 1.20.1 (real compiler
      errors, not assumption)
- [ ] 26.2-fabric: no-mappings Loom config; Java 25 toolchain; rewrite
      `HudComponent.fill()` for the post-1.21.6 render pipeline
- [ ] 1.19.4-fabric: pre-DrawContext (`MatrixStack`-only) signatures
- [ ] 1.18.2-fabric: same era as 1.19.4, confirm no additional delta

### Phase 3 — Loaders
- [ ] 1.21.4-neoforge
- [ ] 26.2-neoforge
- [ ] 1.20.1-forge
- [ ] 1.19.4-forge
- [ ] 1.18.2-forge

### Phase 4 — Verification
- [ ] `./gradlew chiseledBuild` green for every matrix cell (or documented
      exception with real error evidence)
- [ ] PLAN.md status table fully updated
- [ ] Final commit(s) pushed to `origin/main`

## Notes log (append as work proceeds; keep dated)

- 2026-08-10: Repo analysis complete, upstream confirmed stale, newest stable
  MC confirmed live as 26.2, docs written. Starting Phase 0 commit next.
