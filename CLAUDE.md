# CLAUDE.md - FlightHud Minecraft Mod

## Project Overview

FlightHud is a client-side Minecraft mod that draws a flight-simulator-style HUD
overlay while the player is flying with an Elytra (and, to a lesser extent,
while walking/riding). It renders directly into `InGameHud` via a Mixin —
there is no screen/GUI, just an overlay drawn every frame.

## Fork Provenance

This repo (`bshuler/FlightHud`) is a fork of `end-user/FlightHud` (`upstream`
remote). At the time this modernization started:

- `upstream/master` == this repo's `master` (commit `8f2a448`, "merge changes
  for 1.20.1"). Upstream has **not** progressed past Minecraft 1.20.1.
- Upstream's other branches (`config-work`, `forge-1.16.1`, `forge-1.16.2`,
  `forge-1.17.1`) are all old, pre-1.20.1 side branches — none represent
  newer work to merge forward.
- No upstream tags/releases exist.

Conclusion: there is nothing newer upstream to cherry-pick. All multi-version /
multi-loader work below is original porting done in this fork. The `upstream`
remote must stay configured; never push to it.

## Architecture

```
net.torocraft.flighthud/
├── FlightHud.java              # Client entrypoint: loads config, registers
│                                  keybind + "/flighthud toggle" command
├── HudRenderer.java             # Top-level render dispatcher, applies HUD scale,
│                                  iterates HudComponent[]
├── HudComponent.java            # Shared drawing primitives: drawHorizontalLine,
│                                  drawVerticalLine, drawBox, drawFont, and the
│                                  low-level fill() that emits a colored quad.
│                                  ** This fill() is the single hottest spot for
│                                  cross-version rendering-API breakage — it talks
│                                  directly to Tessellator/BufferBuilder/RenderSystem. **
├── FlightComputer.java           # Physics/derived-state calculations (speed, pitch, roll...)
├── Dimensions.java               # Screen-space layout math
├── components/                   # One class per HUD element, each implements
│   ├── PitchIndicator.java         HudComponent.render(DrawContext, float, MinecraftClient)
│   ├── HeadingIndicator.java
│   ├── AltitudeIndicator.java
│   ├── SpeedIndicator.java
│   ├── FlightPathIndicator.java
│   ├── LocationIndicator.java
│   └── ElytraHealthIndicator.java
├── config/                       # Gson-backed JSON config (HudConfig, SettingsConfig)
│   └── loader/                     ConfigLoader/FileWatcher/ConfigFolder — ConfigFolder
│                                    is the only config class touching a loader API
│                                    (FabricLoader.getInstance().getConfigDir()).
├── mixin/InGameHudMixin.java      # @Inject into InGameHud.render(...) at RETURN,
│                                    calls HudRenderer.render(...)
└── SwitchDisplayModeCommand.java  # Brigadier command backing "/flighthud toggle",
                                     typed against FabricClientCommandSource (Fabric-only API)
```

### Cross-version hotspots (in descending order of risk)

1. **`HudComponent.fill()`** — hand-rolled immediate-mode quad via
   `Tessellator.getInstance().getBuffer()` / `BufferBuilder` / `RenderSystem` /
   `BufferRenderer.drawWithGlobalProgram` / `GameRenderer::getPositionColorProgram`.
   This immediate-mode API was **replaced by a new `RenderPipeline`-based renderer**
   starting around MC 1.21.6, and Minecraft 26.1 additionally became fully
   **unobfuscated** with an optional Vulkan backend (26.2 "Chaos Cubed"). Expect this
   method (and only this method) to need a real per-era rewrite, not just an
   import swap.
2. **`DrawContext` existence** — `DrawContext` (and `context.getMatrices()`,
   `context.drawText(...)`) was introduced in MC 1.20. MC 1.18.2 and 1.19.4 predate
   it: `HudComponent.render(...)`, every component's `render(...)`, `HudRenderer`,
   and `InGameHudMixin` all take a `MatrixStack` directly instead of a `DrawContext`,
   and text is drawn via `client.textRenderer.draw(matrices, ...)` /
   `DrawableHelper`, not `context.drawText(...)`.
3. **`InGameHudMixin` injection signature** — must match whatever `InGameHud.render`
   looks like per version (`render(MatrixStack, float)` pre-1.20, `render(DrawContext,
   float)` 1.20+, possible further signature drift in 1.21.6+/26.x pending actual
   compile evidence).
4. **Fabric-only APIs used directly, not yet abstracted per-loader**:
   - `SwitchDisplayModeCommand implements Command<FabricClientCommandSource>` —
     Forge/NeoForge have a differently-typed client command source. Descoped:
     see "Multi-loader scope decisions" below.
   - `ConfigFolder` — trivial one-liner per loader (`FabricLoader.getInstance()
     .getConfigDir()` vs `FMLPaths.CONFIGDIR.get()`).
   - `FlightHud` entrypoint — `ClientModInitializer` (Fabric), needs a NeoForge/
     Forge `@Mod` + `FMLClientSetupEvent` equivalent; keybind registration via
     `KeyBindingHelper` (Fabric) vs `RegisterKeyMappingsEvent` (NeoForge/Forge).
   - Mixins themselves are loader-portable: NeoForge and (modern) Forge both
     support SpongePowered Mixin declared in `mods.toml`/`neoforge.mods.toml`,
     so `InGameHudMixin` does **not** need per-loader forking, only per-MC-version
     signature forking.

### Multi-loader scope decisions

- The `/flighthud toggle` chat command is **Fabric-only** across the matrix.
  The equivalent functionality (toggle HUD display mode) is fully available via
  the keybind (backtick by default) on every loader, so this is a deliberate
  scope reduction for engineering economy, not a capability gap for end users.
  Document any change to this decision in PLAN.md.
- Rendering itself (the actual point of this mod) is **not** loader-limited:
  Mixin-based HUD injection works the same way on Fabric, NeoForge, and Forge.
- Loader coverage is otherwise mandatory, not best-effort: every MC version in
  the matrix builds for every loader that structurally exists for it
  (Fabric+NeoForge for 1.20.5+, Fabric+Forge for ≤1.20.4). See PLAN.md
  "Structural loader blockers" for the two cases where a cell is intentionally
  absent and why.
- **Quilt**: not a separate build target anywhere in this project. Quilt loads
  standard Fabric mod jars natively (it reads `fabric.mod.json` directly), so
  every `*-fabric` output is already Quilt-compatible with zero extra work.
- **Forgix** (`io.github.pacifistmc.forgix`): per-MC-version jar merging, so
  end users get one jar that works on either of that version's loaders
  instead of picking the right one. Verified alive (GitHub `PacifistMC/Forgix`,
  pushed 2026-08-10, stable `2.0.0` tag) and composable with Stonecutter, with
  one wrinkle: Forgix's default subproject autodetection expects flat names
  like `:fabric`/`:forge`, but Stonecutter names its nodes `<mc>-<loader>`
  (e.g. `26.2-neoforge`), so each MC version needs an explicit `inputJar`
  override in the `forgix { }` block rather than relying on autodetection. See
  PLAN.md "Forgix" section for the full investigation and wiring plan; this
  lands in Phase 4, after per-loader jars are confirmed building.

## Build System: Stonecutter + Stonecraft

Mirrors the structure used in the sibling repo `critical-orientation` (the house
template for this program). Read that repo's `CLAUDE.md`/`PLAN.md` if this file
and `settings.gradle.kts` together aren't enough context.

- `settings.gradle.kts` — `dev.kikugie.stonecutter` + `gg.meza.stonecraft` plugins,
  declares every `(version, loader)` node via `stonecutter { mc(version, loaders...) }`.
- `stonecutter.gradle.kts` — records the currently "active" version/loader node
  (what IDE tooling and `runClient` target); auto-managed, marked `DO NOT EDIT`.
- `build.gradle.kts` (root, `centralScript`) — shared build logic applied to every
  node via the `gg.meza.stonecraft` plugin; per-version/per-loader dependency
  coordinates (Minecraft, mappings or lack thereof, loader, Fabric API / NeoForge /
  Forge) are resolved by Stonecraft itself from the node's version+loader id, not
  hand-declared here.
- Source is a **single shared tree** (`src/main`, `src/client`, `src/test` — no
  per-version source forks) using Stonecutter's `//? if <condition> {` /
  `//?}` comment-based preprocessor. At build time, Stonecutter copies+transforms
  this tree into a generated (gitignored) `versions/<mc>-<loader>/` per node.
- `gradle.properties` — mod metadata (`mod.id`, `mod.version`, `mod.group`) shared
  across all nodes; MC/loader/API versions are NOT here (see above).

### Build commands

```bash
./gradlew chiseledBuild           # build every configured version/loader node
./gradlew :1.20.1-fabric:build    # build a single node
./gradlew test                    # run JVM unit tests (version-agnostic logic only)
./gradlew runClient               # run the client for the "active" node (not required for CI)
```

Compile/build green (`chiseledBuild`) is the acceptance bar for this program —
launching the game client is not required to consider a cell "done."

## Version matrix

See `PLAN.md` for the live status checklist. Target matrix as specified by the
modernization program:

| MC version | Fabric | NeoForge | Forge |
|---|---|---|---|
| newest stable (see PLAN.md — was `26.2` at time of writing) | target | target | n/a (program scope) |
| 1.21.4 | target | target | n/a (program scope) |
| 1.20.1 | target | n/a (structural) | target |
| 1.19.4 | target | n/a (structural) | target |
| 1.18.2 | target | n/a (structural) | target |

NeoForge does not exist before the Forge/NeoForge split at 1.20.2 — that's a
structural fact about the loader ecosystem, not a scope choice. Forge is not
targeted for the two newest MC versions per the program's explicit brief
(mirrors `critical-orientation`'s reasoning) even though Forge itself
continues to exist in parallel with NeoForge past 1.20.2 — that one is a
program-level scope decision, recorded as such rather than implied to be a
structural impossibility. Every cell that IS targeted is mandatory — loader
coverage is not "if feasible." Quilt is not a separate column: it loads
`*-fabric` jars natively. See PLAN.md "Forgix" section for the plan to merge
each version's per-loader jars into one combined jar.

## Porting notes by MC version (fill in / correct as work proceeds)

- **1.20.1 (baseline)**: current upstream state. `DrawContext` + old immediate-mode
  `fill()` as described above.
- **1.21.4**: DrawContext-based GUI rendering continues but with API churn
  (`RenderLayer`/vertex-consumer signature changes) between 1.20.1 and 1.21.x.
  Verify exact deltas against real compiler errors, not assumption.
- **Newest stable (26.x)**: Minecraft moved to year-based versioning (`YY.N`)
  starting in 2026. **26.1 was the first fully unobfuscated release** — no more
  Yarn/Intermediary mappings exist or are needed (Fabric stopped publishing Yarn
  after 1.21.11). Loom config for this era has **no `mappings` block at all**;
  see `https://github.com/FabricMC/fabric-example-mod` (default branch tracks the
  newest MC version) for the current reference `build.gradle`/`gradle.properties`
  shape. Also expect: Java 25 (via toolchain auto-provisioning, not a system JDK
  install), and the immediate-mode rendering rewrite mentioned above.
- **1.19.4 / 1.18.2**: predate `DrawContext` entirely; render methods take
  `MatrixStack` directly, and `InGameHud`'s render mixin target differs accordingly.

## Environment notes

- Only JDK installed locally is Temurin 21. Never install another system JDK —
  rely on Gradle toolchains / the foojay resolver plugin to auto-provision
  whatever JDK a given node's Minecraft version needs (e.g. Java 25 for 26.x)
  into `~/.gradle`.
- Never touch Homebrew for build tooling.

## Git / fork rules (binding, see Bert's global git standards)

- `origin` = `bshuler/FlightHud` (push here, freely, straight to `main`).
- `upstream` = `end-user/FlightHud` (fetch-only, never push).
- Commit as `Bert Shuler <BertShuler@proton.me>`; never attribute AI in commits,
  PRs, or code comments.
- Default branch is `main` (this repo started on `master` — see PLAN.md for the
  rename step and its status).
