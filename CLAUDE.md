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

1. **`Gui` render architecture past MC ~1.21.6/26.x** — confirmed via `javap`
   against the real 26.2 merged jar (not assumption): `GuiGraphics` **does not
   exist at all** in 26.2, and `Gui` no longer has any `render(...)` method to
   mixin-inject into with a graphics context. It instead has
   `extractRenderState(DeltaTracker, boolean, boolean)`, which populates a
   `net.minecraft.client.renderer.state.gui.GuiRenderState` object consumed by
   a separate render pass later — a genuine state-extraction architecture
   change, not just an API rename. **Not yet ported** — needs a dedicated
   research pass to find the correct injection point and how to contribute
   custom draw state to `GuiRenderState`/`GuiTextRenderState` (or whatever the
   current equivalent is) before 26.2-fabric can compile. See PLAN.md Phase 2
   for the full finding.
2. **`HudComponent.fill()`** — on 1.21.4 this is a solved, simple case: it now
   calls the built-in pose-aware `GuiGraphics.fill(int,int,int,int,int)`
   directly (confirmed present via `javap`), replacing the old hand-rolled
   immediate-mode quad via `Tessellator.getInstance().getBuffer()` /
   `BufferBuilder` / `RenderSystem` / `BufferRenderer.drawWithGlobalProgram` /
   `GameRenderer::getPositionColorProgram` that 1.20.1 still uses. The real
   risk has moved downstream to 26.x (see hotspot #1 above) — once
   `GuiGraphics` itself is gone, `fill()` will need a second rewrite against
   whatever `GuiRenderState`-based primitive replaces it.
3. **`DrawContext`/`GuiGraphics` existence** — `DrawContext` (renamed
   `GuiGraphics` in Mojmap) was introduced in MC 1.20. MC 1.18.2 and 1.19.4
   predate it: `HudComponent.render(...)`, every component's `render(...)`,
   `HudRenderer`, and `InGameHudMixin` all need to take a `PoseStack`/
   `MatrixStack` directly instead, and text is drawn via
   `client.font.draw(matrices, ...)`, not `context.drawText(...)`. **Not yet
   ported** — the current shared tree only has the `GuiGraphics`-based shape;
   1.19.4/1.18.2 need their own Stonecutter conditional branch for every
   render-signature call site, mirroring the `>=1.21.2` DeltaTracker branch
   already written in `InGameHudMixin.java` (see hotspot #4).
4. **`InGameHudMixin` injection signature** — must match whatever `Gui.render`
   (Mojmap; was `InGameHud.render`) looks like per version. Confirmed via
   `javap`: 1.20.1 is `render(GuiGraphics, float)`; 1.21.4 added a second
   parameter, `render(GuiGraphics, DeltaTracker, CallbackInfo)` — handled with
   one `//? if >=1.21.2 { ... } else { ... }` block, already written. 26.x
   removes this method entirely (see hotspot #1). Pre-1.20 versions will need
   a third branch once ported (hotspot #3).
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
  **Never hand-edit this file.** To switch it, run the Gradle task Stonecutter
  registers per node — its display name (not obviously "stonecutter"-prefixed,
  so case-insensitive grepping for that word misses it in `tasks --all`
  output) is `"Set active project to <mc>-<loader>"`, e.g.
  `./gradlew "Set active project to 1.21.4-fabric"`. This matters because a
  node's `:<node>:build` only compiles correctly when it — or the currently
  `active` node — actually matches the shared tree's current `//? if`
  toggle-state; switching active runs the real generate/prepare step that
  keeps them in sync, which a plain `:<node>:build` does not trigger on its
  own if the active marker is stale.
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

**Mapping scheme (decided this pass): uniform Mojmap across all 5 MC
versions**, not a Yarn(legacy)/Mojmap(modern) split. Yarn mappings stop
existing entirely past 1.21.11 (26.x is unobfuscated-only via Mojang's own
mappings), so splitting the tree into two mapping vocabularies for what is
otherwise one shared source tree would only add bookkeeping for no benefit.
`versions/dependencies/{1.18.2,1.19.4,1.20.1}.properties` have `yarn_mappings`
removed accordingly (each carries a comment explaining why).

- **1.20.1 (baseline)**: ✅ builds green under the uniform-Mojmap tree.
  `GuiGraphics` + `GuiGraphics.fill(int,int,int,int,int)` overload used
  directly, `Gui.render(GuiGraphics, float, CallbackInfo)` mixin signature.
- **1.21.4**: ✅ builds green. Real delta from 1.20.1, all confirmed via
  `javap` against the real per-project jar (not assumption): `Gui.render`
  gained a `DeltaTracker` parameter (`render(GuiGraphics, DeltaTracker,
  CallbackInfo)`) in place of the trailing `float tickDelta`, handled with a
  `//? if >=1.21.2 { ... } else { ... }` block in `InGameHudMixin.java`.
  `Entity.hasVehicle()` doesn't exist in Mojmap at all — replaced with
  `player.getVehicle() != null`. Full symbol-rename table (KeyMapping,
  InputConstants, Options.fov(), Window.getGuiScaledWidth(), etc.) verified
  and applied; see git history for the full diff.
- **Newest stable (26.x)**: ❌ blocked, real architecture change, not API
  churn. Minecraft moved to year-based versioning (`YY.N`) starting in 2026.
  **26.1 was the first fully unobfuscated release** — no more Yarn/Intermediary
  mappings exist or are needed. Loom config for this era has **no `mappings`
  block at all**. Toolchain auto-provisioning (Java 25) requires the
  `org.gradle.toolchains.foojay-resolver-convention` plugin in
  `settings.gradle.kts` — added this pass (version `1.0.0`; `0.8.0` is
  incompatible with this project's Gradle 9.3.1). The real blocker, confirmed
  via `javap` against the actual 26.2 jar: **`GuiGraphics` does not exist**,
  and `Gui` has no `render(...)` method at all — it's been replaced by
  `extractRenderState(DeltaTracker, boolean, boolean)` writing into a
  `net.minecraft.client.renderer.state.gui.GuiRenderState`, a genuine
  render-state-extraction architecture, not a renamed draw API. Also newly
  found blocked: `KeyMapping`'s 3-arg constructor now takes a
  `KeyMapping.Category` instead of a raw `String`; Fabric API's
  `KeyBindingHelper`/`ClientCommandManager` weren't found under their 1.21.4
  packages either. None of this is ported yet — needs its own research pass
  before resuming 26.2-fabric.
- **1.19.4 / 1.18.2**: not yet started. Predate `DrawContext`/`GuiGraphics`
  entirely; render methods take `PoseStack`/`MatrixStack` directly, and
  `Gui`'s render mixin target differs accordingly (see "Cross-version
  hotspots" #3/#4 above). The current shared tree only has the
  `GuiGraphics`-shaped code live; this era needs its own Stonecutter
  conditional branch added to every render call site, the same way the
  `DeltaTracker` mixin branch was done for 1.21.4 vs 1.20.1.

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
