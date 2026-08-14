# CLAUDE.md - FlightHud Minecraft Mod

## Project Overview

FlightHud is a client-side Minecraft mod that draws a flight-simulator-style HUD
overlay while the player is flying with an Elytra (and, to a lesser extent,
while walking/riding). There is no screen/GUI, just an overlay drawn every
frame, hooked directly into each loader's own official HUD-render event/
registry via `HudHooks` (no Mixin — see "Cross-version hotspots" #1 for why
that changed).

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
├── FlightHud.java              # Client entrypoint: loader-conditional (Fabric
│                                  ClientModInitializer vs. NeoForge/Forge @Mod),
│                                  loads config, registers keybind + the
│                                  (Fabric-only, >=1.19-only) "/flighthud toggle"
│                                  command, hands off to HudHooks.register()
├── HudHooks.java                 # Loader/version-conditional HUD render-hook
│                                  registration — REPLACES the old Mixin (see
│                                  "Cross-version hotspots" #1). Hooks each
│                                  loader's own official HUD-render event/registry
│                                  directly: HudElementRegistry (fabric>=26.1),
│                                  HudRenderCallback (fabric else), RenderGuiEvent.Post
│                                  (neoforge), OverlayRegistry/RegisterGuiOverlaysEvent
│                                  (forge, imperative <1.19 / event-based >=1.19).
├── FlightCanvas.java              # Loader/version-agnostic drawing interface (fill,
│                                     drawText, pushPose/popPose, translate,
│                                     rotateDegrees, scale) — every rendering-logic
│                                     file below depends ONLY on this, never on
│                                     GuiGraphics/PoseStack/GuiGraphicsExtractor directly.
├── PlatformCanvas.java             # The ONE Stonecutter-branched file implementing
│                                     FlightCanvas: <1.20 (raw PoseStack +
│                                     GuiComponent.fill/Font.draw), >=26.1
│                                     (GuiGraphicsExtractor + Matrix3x2fStack), else
│                                     (classic GuiGraphics). See its class doc for the
│                                     full javap-confirmed shape of each era.
├── HudRenderer.java             # Top-level render dispatcher, applies HUD scale,
│                                  iterates HudComponent[] against FlightCanvas
├── HudComponent.java            # Shared drawing primitives: drawHorizontalLine,
│                                  drawVerticalLine, drawBox, drawFont — all against
│                                  FlightCanvas, no version branching in this file.
├── FlightComputer.java           # Physics/derived-state calculations (speed, pitch, roll...)
├── Dimensions.java               # Screen-space layout math. One Stonecutter branch:
│                                    Options.fov() is an OptionInstance<Integer> from
│                                    1.19.4 on, a plain double field in 1.18.2.
├── components/                   # One class per HUD element, each implements
│   ├── PitchIndicator.java         HudComponent.render(FlightCanvas, ...) — no
│   ├── HeadingIndicator.java       Stonecutter branching in any of these; they're
│   ├── AltitudeIndicator.java      written once against FlightCanvas.
│   ├── SpeedIndicator.java
│   ├── FlightPathIndicator.java
│   ├── LocationIndicator.java
│   └── ElytraHealthIndicator.java
├── config/                       # Gson-backed JSON config (HudConfig, SettingsConfig)
│   └── loader/                     ConfigLoader/FileWatcher/ConfigFolder — ConfigFolder
│                                    is the only config class touching a loader API
│                                    (FabricLoader.getInstance().getConfigDir() vs.
│                                    FMLPaths.CONFIGDIR.get()).
└── SwitchDisplayModeCommand.java  # Brigadier command backing "/flighthud toggle",
                                     typed against FabricClientCommandSource
                                     (Fabric-only API). Guarded fabric && >=1.19 —
                                     the whole file compiles to just the package
                                     statement otherwise (see "Multi-loader scope
                                     decisions" for why 1.18.2 also drops this).
```

There is no `mixin/` package any more — see hotspot #1.

### Cross-version hotspots (in descending order of historical risk; all now resolved)

1. **`Gui` render architecture past MC ~1.21.6/26.x — RESOLVED, no Mixin at
   all any more.** Confirmed via `javap` against the real 26.2 merged jar:
   `GuiGraphics` does not exist at all in 26.2, and `Gui` no longer has any
   `render(...)` method to mixin-inject into — it instead has
   `extractRenderState(DeltaTracker, boolean, boolean)`, populating a
   `net.minecraft.client.renderer.state.gui.GuiRenderState` consumed by a
   separate render pass. **The fix was architectural, not a deeper mixin
   injection point**: that extraction step is internal to the engine's own
   render pass, but every loader still fires its own official HUD-render
   hook *before* it, the same hook every other loader always had. `HudHooks`
   now registers directly against each loader's real hook/registry instead of
   mixin-injecting into `Gui` at all — see `HudHooks.java`'s class doc for the
   full per-loader/per-era mapping. This is *more* portable than the old
   Mixin approach, not a workaround: NeoForge's `RenderGuiEvent.Post` already
   adapts across the 26.x architecture change on NeoForge's own side
   (confirmed via `javap`: `getGuiGraphics()` returns `GuiGraphics` on 1.21.4
   and `GuiGraphicsExtractor` on 26.2, same method, no NeoForge-side branching
   needed at that call site).
2. **`HudComponent.fill()` / drawing primitives — RESOLVED via `FlightCanvas`.**
   Three real shapes exist across the matrix (all confirmed via `javap`):
   `<1.20` (raw `PoseStack` + `GuiComponent.fill(PoseStack,...)` +
   `Font.draw(PoseStack,...)`), `>=26.1` (`GuiGraphicsExtractor` +
   `org.joml.Matrix3x2fStack`, renamed methods like `text(...)` not
   `drawString(...)`), and the "classic" `GuiGraphics` shape in between
   (1.20.1, 1.21.4). All three are implemented once each in `PlatformCanvas`,
   the single Stonecutter-branched file behind `FlightCanvas` — every other
   rendering-logic file (`HudComponent`, `HudRenderer`, every `components/*`
   class) has zero version branching and never will need any, since they
   only ever call `FlightCanvas` methods.
3. **`DrawContext`/`GuiGraphics` existence — RESOLVED.** `GuiGraphics`
   (Mojmap; Yarn `DrawContext`) doesn't exist before MC 1.20 — MC 1.18.2 and
   1.19.4 draw through a raw `PoseStack` instead (`PlatformCanvas`'s `<1.20`
   branch). `fill`/`drawText` signatures are identical between 1.18.2 and
   1.19.4 (confirmed via `javap` on both jars) — the real deltas between
   those two eras are elsewhere (see the 1.18.2 porting notes below).
4. **1.18.2-specific deltas below the 1.19.4 baseline — the last cell
   resolved, three genuine API absences found via `javap` that were not
   shared with 1.19.4** (so not folded into the pre-existing `<1.20` branch):
   - `Options.fov()` is a plain `double fov` field in 1.18.2, not the
     `OptionInstance<Integer>`-returning method form that starts at 1.19.4
     (`Dimensions.java`, `//? if <1.19`).
   - `com.mojang.math.Axis` doesn't exist in 1.18.2 at all — introduced
     alongside `PoseStack.mulPose` switching its overload from the older
     `com.mojang.math.Quaternion` (via `Vector3f.ZP.rotationDegrees(...)`,
     1.18.2) to the JOML `Quaternionf` (via `Axis.ZP.rotationDegrees(...)`,
     1.19.4+) — `Vector3f` is fully *removed* by 1.19.4, not deprecated, so
     this is a real `<1.19`/else split (`PlatformCanvas.rotateDegrees()`).
   - `net.fabricmc.fabric.api.client.command.v2` (the whole Fabric Client
     Command API v2) isn't published for 1.18.2's Fabric API bundle at all —
     confirmed by the absence of any `fabric-command-api-v2-*` artifact for
     1.18.2 in the loom-cache. Rather than fork a second command-API
     generation for one version, the "/flighthud toggle" command (imports,
     `FlightHud.setupCommand()`, its call site, and all of
     `SwitchDisplayModeCommand.java`) is gated `fabric && >=1.19` — see
     "Multi-loader scope decisions" below.
5. **Fabric-only APIs, now fully abstracted per-loader** (previously an open
   item; resolved):
   - `SwitchDisplayModeCommand implements Command<FabricClientCommandSource>` —
     Forge/NeoForge have a differently-typed client command source. Descoped:
     see "Multi-loader scope decisions" below.
   - `ConfigFolder` — one-liner per loader (`FabricLoader.getInstance()
     .getConfigDir()` vs `FMLPaths.CONFIGDIR.get()`), done.
   - `FlightHud` entrypoint — `ClientModInitializer` (Fabric) vs. NeoForge/
     Forge `@Mod` + `FMLClientSetupEvent`/constructor-based registration,
     done; keybind registration via `KeyBindingHelper`/`KeyMappingHelper`
     (Fabric, version-split at 26.1) vs `RegisterKeyMappingsEvent`
     (NeoForge/Forge>=1.19) vs `ClientRegistry.registerKeyBinding`
     (Forge<1.19), done.

### Multi-loader scope decisions

- The `/flighthud toggle` chat command is **Fabric-only, and further gated
  `>=1.19`** across the matrix (1.18.2-fabric drops it too: the whole Fabric
  Client Command API v2 isn't published for 1.18.2's Fabric API bundle — see
  "Cross-version hotspots" #4). The equivalent functionality (toggle HUD
  display mode) is fully available via the keybind (backtick by default) on
  every loader/version, so this is a deliberate scope reduction for
  engineering economy, not a capability gap for end users. Document any
  change to this decision in PLAN.md.
- Rendering itself (the actual point of this mod) is **not** loader-limited:
  every loader (Fabric, NeoForge, Forge) has its own official HUD-render
  hook/registry that `HudHooks` registers against directly — see "Cross-version
  hotspots" #1 for why this replaced the original Mixin-based approach.
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
./gradlew chiseledBuild                       # build every configured version/loader node
./gradlew :1.20.1-fabric:build                 # build a single node
./gradlew test                                 # run JVM unit tests against the ACTIVE node only
./gradlew jacocoTestReport                     # generate the HTML/XML coverage report
./gradlew check                                # test + coverage-verification gate (see below)
./gradlew runClient                            # run the client for the "active" node (not required for CI)
```

Compile/build green (`chiseledBuild`) is the acceptance bar for this program —
launching the game client is not required to consider a cell "done."

### Testing & coverage (JaCoCo)

JUnit 5 + JaCoCo are wired into `build.gradle.kts`, mirroring the canonical
pattern from the sibling `critical-orientation`/`critical-flight-details`
repos (including the NeoForge `junit-fml`/`mainargs.txt` classpath-exclusion
guard, gated `if (mod.isNeoforge)`). Tests run against whichever Stonecutter
node is currently `active` — **never** across the full matrix, and never by
hand-toggling `//? if` markers to reach another node's code.

`tasks.check` depends on `jacocoTestCoverageVerification`, which enforces
`LINE COVEREDRATIO 1.00` (100%) over the classes below. This is a real,
enforced gate, not aspirational — `./gradlew check` fails the build if
coverage regresses.

**Coverage scope** (see `PLAN.md` "Phase 5" for the full class-by-class
breakdown, refactors, and the one dead-code bug this pass found):

| In scope (tested to 100%) | Excluded (documented reason in `PLAN.md`) |
|---|---|
| `config.SettingsConfig` (+ `$DisplayMode`) | `FlightHud` — loader entrypoint, live singletons |
| `config.HudConfig` | `HudHooks` — live per-loader render-hook registration |
| `FlightComputerMath` | `PlatformCanvas` — wraps live rendering primitives |
| `config.loader.ConfigLoader` | `HudComponent`, `HudRenderer` — render straight to `FlightCanvas` |
| `config.loader.FileWatcher` | `Dimensions` — reads live `Window`/`Options` state |
| | `FlightComputer` — thin wrapper over live `Vec3`/`Entity`, delegates to `FlightComputerMath` |
| | `SwitchDisplayModeCommand` — needs a live command-dispatch context |
| | `components/**` (7 classes) — render straight to `FlightCanvas` |
| | `config.loader.ConfigFolder` — one-liner over the live loader singleton |

`ConfigLoader` and `FileWatcher` are pure enough to unit test headless
specifically because they take a plain `java.io.File` and don't touch any
loader singleton themselves — that resolution lives at the `FlightHud.java`
call site instead (excluded, per above). `FileWatcher`'s full `run()`
lifecycle is exercised against a **real** `WatchService`/filesystem write in
addition to hand-rolled fakes for the pure `pollEvents()` branch logic — see
`FileWatcherTest.java`'s class doc for why the fakes alone weren't enough to
reach 100%.

### Loaded-game tests (Tier 1)

`src/test/java/net/torocraft/flighthud/LoadedGameTest.java` is different in kind
from the rest of the suite: it runs against a **real, bootstrapped Minecraft and
a real Fabric loader**, not mocks. `net.fabricmc:fabric-loader-junit` (declared
under `if (mod.isFabric)`) stands the loader up inside the JUnit worker, which
makes `SharedConstants.tryDetectVersion()` + `Bootstrap.bootStrap()` in
`@BeforeAll` legal and the assertions afterwards meaningful.

What it checks that a headless test cannot:

1. **The item registry is really populated** — a guard on the harness itself, so
   nothing else in the class can pass vacuously.
2. **A real loader discovers this mod** from the processed `fabric.mod.json`
   (Stonecraft templating already applied), exactly as the game would.
3. **Every `depends` range in `fabric.mod.json` is satisfiable in this cell.**
   A cell can build perfectly against a Minecraft its own declared range no
   longer admits — a jar that ships and then refuses to load. The loader
   resolves the ranges per cell here.
4. **Heading agrees with vanilla's own compass** (`Direction.fromYRot`) and with
   vanilla's own degree wrapping (`Mth.wrapDegrees`), swept −1080°→1080°.
5. **The elytra health readout tracks a real `ItemStack(Items.ELYTRA)`'s
   durability**, not a number copied into a test. `FlightComputerMath
   .elytraHealthPercent(int, int)` was extracted from
   `FlightComputer.computeElytraHealth` (which now delegates to it) so this is
   drivable without a live player.

Verified green on **all 5 Fabric cells** (1.18.2 → 26.2), 6 tests each, parsed
from `versions/*/build/test-results/test/`. Bootstrap costs ~4–5.5s per cell.

**26.x needs one extra bootstrap step.** Through 1.21.4 an item's data
components were baked into the `Item` at construction; in 26.x they are built
from loaded registry data and bound onto each `Holder.Reference` afterwards, so
constructing any `ItemStack` before that throws `Components not bound yet`. The
`@BeforeAll` therefore runs `BuiltInRegistries.DATA_COMPONENT_INITIALIZERS
.build(VanillaRegistries.createLookup())` and applies the result, guarded
`//? if >=26.1`. Full detail and the `javap` evidence are in `PLAN.md`.

Fabric cells only (`//? if fabric` wraps the whole file). NeoForge's equivalent
bootstrap is `junit-fml`, whose supported harness is ModDevGradle-only and so
unavailable under Architectury Loom — see the exclusion comment in
`build.gradle.kts`.

```bash
./gradlew test --tests "*LoadedGameTest"
```

Note the coverage coupling: `FlightComputerMath.elytraHealthPercent` was
extracted for test 5 above, and because `LoadedGameTest` doesn't compile on
Forge cells, it must also be covered from `FlightComputerMathTest` or the
Forge cells' 100% JaCoCo gate fails. Keep both.

### Client gametests (Tier 3)

`src/gametest/java/net/torocraft/flighthud/gametest/FlightHudClientGameTest.java`
runs a **real Minecraft client** — real window, real GL context, real render
thread, real world — and asserts that the HUD actually draws. This is the only
tier that can catch the failure this mod is most prone to: `HudHooks.register()`
binding to a hook the running Minecraft version doesn't fire, which compiles,
packages, loads, and passes every other test while rendering nothing (exactly
what 26.x did — see hotspot #1).

```bash
./gradlew :26.2-fabric:runClientGameTest      # or :1.21.4-fabric:
```

Four assertions: the client reaches the title screen; the loader really has
`flighthud` loaded (harness guard); `HudComponent.CONFIG` goes null → non-null
after 40 in-world ticks, which can only happen inside `HudRenderer.render(...)`;
and no `net.torocraft.flighthud` stack trace appears on stderr while the world
is open (necessary because `HudRenderer.render` swallows exceptions into
`printStackTrace()`, so a broken component is otherwise a silent pass). Title
and in-world screenshots land in
`versions/<cell>/build/run/clientGameTest/screenshots/`.

**Only `1.21.4-fabric` and `26.2-fabric`.** `fabric-client-gametest-api-v1`
doesn't exist for the older bundles; the gate in `build.gradle.kts` is
`clientGameTestSupported`, and on other cells no gametest source set, run config
or `check` hook is created. The two cells ship v4.1.1 and v6.0.0 respectively
and share one un-branched file that uses only their common API — in particular
avoiding `TestSingleplayerContext.getClientWorld()` (v4-only) and
`getConnection()` (v6-only). Stonecutter *does* preprocess this source set, so
`//? if` markers are available if that ever stops being enough.

**Do not remove the `afterEvaluate` block that fixes the `clientGameTest` run
config.** Stonecraft's `loom.runs.all { }` hook catches this run config and (a)
adds a second `--username`, which kills the launch outright, and (b) repoints
`runDir` at the repo-root `run/` — the developer's own dev directory — while
Loom's `clearRunDirectory` defaults to true and a `Delete` task runs over it.
Both are fixed there, for the `clientGameTest` config only. Full detail, and the
negative-control result proving the assertions aren't vacuous, in PLAN.md
"Tier 3".

### CI

`.github/workflows/build.yml`: a `build` job running `./gradlew chiseledBuild`
(all 10 cells plus every cell's `check`), and a `client-gametest` matrix job
running the two supported cells under `xvfb-run` with software OpenGL. The
single `setup-java` is **JDK 25, and it must be**: this repo's Stonecraft 1.10
/ older Loom line checks the Gradle *daemon* JVM when setting up Minecraft
26.x, so a 21-only runner fails with "Minecraft 26.2 requires Java 25 but
Gradle is using 21" (the first CI run did exactly that; locally it passed only
because the daemon ran on a newer system JDK). Foojay still auto-provisions
the older compile toolchains downward from 25 — one `setup-java`, newest JDK
any cell needs.

## Version matrix

See `PLAN.md` for the live status checklist. Target matrix as specified by the
modernization program:

| MC version | Fabric | NeoForge | Forge |
|---|---|---|---|
| newest stable (see PLAN.md — was `26.2` at time of writing) | ✅ | ✅ | n/a (program scope) |
| 1.21.4 | ✅ | ✅ | n/a (program scope) |
| 1.20.1 | ✅ | n/a (structural) | ✅ |
| 1.19.4 | ✅ | n/a (structural) | ✅ |
| 1.18.2 | ✅ | n/a (structural) | ✅ |

All 10 targeted cells build green (verified via `chiseledBuild` plus per-jar
`unzip -l` content checks — see PLAN.md Phase 4). NeoForge does not exist
before the Forge/NeoForge split at 1.20.2 — that's a structural fact about
the loader ecosystem, not a scope choice. Forge is not targeted for the two
newest MC versions per the program's explicit brief (mirrors
`critical-orientation`'s reasoning) even though Forge itself continues to
exist in parallel with NeoForge past 1.20.2 — that one is a program-level
scope decision, recorded as such rather than implied to be a structural
impossibility. Quilt is not a separate column: it loads `*-fabric` jars
natively. See PLAN.md "Forgix" section for the still-open plan to merge each
version's per-loader jars into one combined jar (deferred, Phase 4).

## Porting notes by MC version

**Mapping scheme: uniform Mojmap across all 5 MC versions**, not a
Yarn(legacy)/Mojmap(modern) split. Yarn mappings stop existing entirely past
1.21.11 (26.x is unobfuscated-only via Mojang's own mappings), so splitting
the tree into two mapping vocabularies for what is otherwise one shared
source tree would only add bookkeeping for no benefit.
`versions/dependencies/{1.18.2,1.19.4,1.20.1}.properties` have `yarn_mappings`
removed accordingly (each carries a comment explaining why).

- **1.20.1 (baseline)**: ✅ builds green under the uniform-Mojmap tree.
  `GuiGraphics` + `GuiGraphics.fill(int,int,int,int,int)` overload used
  directly via `PlatformCanvas`'s "classic" branch.
- **1.21.4**: ✅ builds green. Real delta from 1.20.1, confirmed via `javap`
  against the real per-project jar: the HUD-render hook gained a
  `DeltaTracker` parameter in place of a trailing `float tickDelta` (handled
  in `HudHooks.register()`'s `fabric && <1.21.2` vs `fabric` branches).
  `Entity.hasVehicle()` doesn't exist in Mojmap at all — replaced with
  `player.getVehicle() != null`. Full symbol-rename table (KeyMapping,
  InputConstants, Options.fov(), Window.getGuiScaledWidth(), etc.) applied;
  see git history for the full diff.
- **Newest stable (26.x)**: ✅ builds green (both fabric and neoforge) —
  previously blocked, now resolved architecturally, not via API-churn
  patching. Minecraft moved to year-based versioning (`YY.N`) starting in
  2026. **26.1 was the first fully unobfuscated release** — no more
  Yarn/Intermediary mappings exist or are needed; Loom config for this era
  has **no `mappings` block at all**. Toolchain auto-provisioning (Java 25)
  requires the `org.gradle.toolchains.foojay-resolver-convention` plugin in
  `settings.gradle.kts` (version `1.0.0`; `0.8.0` is incompatible with this
  project's Gradle 9.3.1). The real blocker, confirmed via `javap` against
  the actual 26.2 jar, was that **`GuiGraphics` does not exist** and `Gui`
  has no `render(...)` method at all — replaced by
  `extractRenderState(DeltaTracker, boolean, boolean)` writing into a
  `net.minecraft.client.renderer.state.gui.GuiRenderState`, a genuine
  render-state-extraction architecture change internal to the engine's own
  render pass. The resolution was to stop hooking `Gui` at all and register
  against Fabric's `HudElementRegistry`/NeoForge's `RenderGuiEvent.Post`
  instead (see "Cross-version hotspots" #1 and `HudHooks.java`'s class doc).
  Also handled: `KeyMapping`'s constructor takes a `KeyMapping.Category`
  (registered via a namespaced `Identifier`) from 1.21.9/1.21.11 on instead
  of a raw `String`; Fabric API's keybind/command helpers moved packages
  (`KeyMappingHelper`, `HudElementRegistry`, `ClientCommands`) at 26.1.
- **1.19.4**: ✅ builds green (fabric + forge). Predates `DrawContext`/
  `GuiGraphics` entirely — render methods take a raw `PoseStack` instead
  (`PlatformCanvas`'s `<1.20` branch, shared with 1.18.2 since the
  `fill`/`drawText` signatures are identical between the two, confirmed via
  `javap`).
- **1.18.2**: ✅ builds green (fabric + forge) — the last cell completed.
  Shares the `<1.20` raw-`PoseStack` `PlatformCanvas` branch with 1.19.4, but
  has three further API deltas below the 1.19.4 baseline (all confirmed via
  `javap`, none shared with 1.19.4 so not folded into the `<1.20` branch —
  see "Cross-version hotspots" #4 for the full detail): `Options.fov()` is a
  plain field not a method; `com.mojang.math.Axis` doesn't exist at all
  (uses `Vector3f.ZP.rotationDegrees(...)` returning the pre-JOML
  `Quaternion`); and the Fabric Client Command API v2 isn't published for
  1.18.2 at all, so the `/flighthud toggle` command is dropped for this node
  too (`fabric && >=1.19` gate). Also fixed a latent pre-existing bug found
  while porting this cell: `HudHooks.registerOverlays()`'s guard was
  `forge && <1.20`, which wrongly included 1.18.2-forge even though that
  node uses the imperative `OverlayRegistry` path (already handled in
  `register()`), not the event-based `RegisterGuiOverlaysEvent`/`ForgeGui`
  API at all (neither type resolves against the 1.18.2 Forge jar) — fixed by
  splitting into three branches with a deliberately empty `forge && <1.19`
  first branch.

## Environment notes

- Temurin 21 is the only JVM under `/Library/Java`, but the shell's default
  `java` is a newer Homebrew OpenJDK (26.x) — which is why the Gradle daemon
  satisfies Minecraft 26.x's Java 25 check locally while a 21-only CI runner
  does not. Never install another system JDK — rely on Gradle toolchains /
  the foojay resolver plugin to auto-provision whatever JDK a given node's
  Minecraft version needs (e.g. Java 25 for 26.x) into `~/.gradle`.
- Never touch Homebrew for build tooling.

## Git / fork rules (binding, see Bert's global git standards)

- `origin` = `bshuler/FlightHud` (push here, freely, straight to `main`).
- `upstream` = `end-user/FlightHud` (fetch-only, never push).
- Commit as `Bert Shuler <BertShuler@proton.me>`; never attribute AI in commits,
  PRs, or code comments.
- Default branch is `main` (this repo started on `master` — see PLAN.md for the
  rename step and its status).
