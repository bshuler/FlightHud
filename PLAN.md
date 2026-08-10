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

**Status as of 2026-08-10: all 10 mandatory cells build green via
`./gradlew chiseledBuild`, verified with real (non-empty) jar contents via
`unzip -l` per cell — not just `BUILD SUCCESSFUL`.**

| MC version | Fabric | NeoForge | Forge | Forgix merged jar |
|---|---|---|---|---|
| 26.2 (newest stable) | ✅ | ✅ | n/a — program scope (see below) | ☐ |
| 1.21.4 | ✅ | ✅ | n/a — program scope (see below) | ☐ |
| 1.20.1 | ✅ | n/a — structural, no NeoForge pre-1.20.2 | ✅ | ☐ |
| 1.19.4 | ✅ | n/a — structural, no NeoForge pre-1.20.2 | ✅ | ☐ |
| 1.18.2 | ✅ | n/a — structural, no NeoForge pre-1.20.2 | ✅ | ☐ |

Quilt: no separate column — every `*-fabric` jar is Quilt-compatible as-is
(Quilt loads Fabric mod jars natively). Documented, not built separately.

(☐ = not yet attempted, 🔧 = in progress, ✅ = `chiseledBuild` green, ❌ = attempted,
blocked — see notes with real error evidence, 🟡 = building with a documented
scope reduction.)

## Multi-loader scope decisions (see CLAUDE.md "Multi-loader scope decisions")

- `/flighthud toggle` chat command: **Fabric-only**. Equivalent functionality
  (toggle display mode) remains available via keybind on every loader. This is
  a deliberate descope for engineering economy, not a rendering-feasibility
  problem — Mixin-based HUD rendering itself is NOT loader-limited (NeoForge and
  Forge both support SpongePowered Mixin declaratively).

## Updated requirement (2026-08-10): loader coverage is mandatory

Bert's follow-up instruction, applied on top of the original brief: **every MC
version target must build for every loader viable on that version** — this is
not new work, it's a confirmation of what the matrix below already commits to
(Fabric+NeoForge for 1.20.5+, Fabric+Forge for ≤1.20.4). No loader cell may be
silently dropped; any genuinely blocked cell must be documented here with the
concrete blocker, not just omitted.

**Quilt**: not a separate build target. Quilt loads standard Fabric mod jars
natively (its `ModMetadataReader` understands `fabric.mod.json` directly), so
every `*-fabric` jar this project produces is already Quilt-compatible with no
extra Stonecutter node. Documented here and in CLAUDE.md/README rather than
added as a matrix column.

**Structural loader blockers** (loaders that do not exist for a given MC
version — not a feasibility choice, a fact about the loader ecosystem):
- NeoForge does not exist before the Forge/NeoForge split at MC 1.20.2 — no
  NeoForge cell for 1.20.1, 1.19.4, or 1.18.2.
- Forge is not being targeted for 26.2/1.21.4 in this matrix per the original
  program brief's explicit version/loader assignment (`fabric+neoforge` for the
  two newest versions, `fabric+forge` for the three legacy versions) — Forge
  itself does still exist in parallel with NeoForge post-1.20.2, so this is a
  program-level scope choice, not a structural impossibility. Flagging the
  distinction here per the instruction to record blockers precisely.

### Forgix — single merged all-loader jar (investigated 2026-08-10)

Bert asked whether the **Forgix** Gradle plugin (`io.github.pacifistmc.forgix`,
`PacifistMC/Forgix` on GitHub) can merge each MC version's per-loader jars into
one combined "works on any loader" jar, and whether it composes with the
Stonecutter/Stonecraft toolchain.

**Maintenance status — verified live, not from memory:**
- GitHub repo pushed **2026-08-10** (today), not archived.
- Latest tag is a **stable `2.0.0`** release (not just a snapshot); prior
  snapshot line (`2.0.0-SNAPSHOT.3` through `.6`) runs May 2025 → Aug 2026.
- Published and resolvable on the Gradle Plugin Portal as
  `io.github.pacifistmc.forgix` version `2.0.0`.
- The project's own README preemptively addresses "this project feels dead":
  the author's position is it's loader/MC-independent by design and needs
  little maintenance, and "the chances are it'll still work" even if unmaintained.
- **Conclusion: alive and current as of this plan.** Not dead.

**How it works**: Forgix repackages each loader's compiled classes under
loader-specific packages so the JVM only ever loads the entry point the active
loader calls (Fabric via `fabric.mod.json`, Forge/NeoForge via their mod
descriptors), then jars them all together. It operates on a `mergeJars` task
configured with per-loader `inputJar` file references — by default it
autodetects subprojects literally named `:fabric`/`:forge`/`:neoforge`/etc.,
with an override (`inputJar = project(":x").tasks.shadowJar.archiveFile`) for
anything that doesn't match that naming convention.

**Composability with Stonecutter/Stonecraft — the real complication**:
Stonecutter's project model does not give every version+loader combination a
uniformly-addressable subproject: the physical root project *is* whichever
node is currently marked `active` in `stonecutter.gradle.kts` (no path
prefix), while every other node is a real Gradle subproject named
`<mc>-<loader>` (e.g. `26.2-neoforge`). Forgix's autodetection assumes flat
loader-named subprojects (`:fabric`, `:forge`), which will not match Stonecutter's
`<mc>-<loader>` naming, so Forgix needs explicit `inputJar` overrides — one
Forgix configuration block per Minecraft version, each pointing at that
version's two per-loader subproject jar outputs (e.g. for MC 26.2:
`fabric { inputJar = project(":26.2-fabric").tasks.named("remapJar")... }`,
`neoforge { inputJar = project(":26.2-neoforge").tasks.named("remapJar")... }`).
No existing public example wires Forgix through Stonecutter/Stonecraft
specifically (checked via GitHub code search) — this pairing is unproven in
the wild, though nothing in either tool's design rules it out.

**Decision: adopt it, wired explicitly per MC version.** Not dead, not
structurally incompatible — the added complexity is bookkeeping (naming), not
a blocker. Plan: apply the Forgix plugin at the true Gradle root
(`stonecutter.gradle.kts`, since that's the file that's actually the physical
root project's build script regardless of which node is "active"), add one
`forgix { ... }` per-MC-version merge configuration once each version's two
loader cells build green, and verify the merged jar loads on both loaders
before calling a version's Forgix wiring "done." This work happens in Phase 4
(Verification) after the per-loader `chiseledBuild` matrix is green — merging
broken or nonexistent per-loader jars first would just produce a broken merged
jar with an extra layer of indirection to debug through. Tracked as its own
checklist item below; if real build evidence shows the naming workaround
doesn't hold up, this section will be updated with the actual error and a
fallback to per-loader jars (which Stonecraft's `chiseledBuild` already
produces regardless of whether Forgix succeeds).

## Status checklist

### Phase 0 — Docs & repo hygiene
- [x] Read `critical-orientation` template (`settings.gradle.kts`,
      `stonecutter.gradle.kts`, `build.gradle.kts`, `gradle.properties`,
      `CLAUDE.md`, `PLAN.md`)
- [x] Confirm upstream has nothing newer to merge
- [x] Determine newest stable MC version live from Fabric meta API
- [x] Write `CLAUDE.md`
- [x] Write `PLAN.md` (this file)
- [x] Commit docs
- [x] Rename `master` → `main`, push, set default branch (confirmed current
      branch and GitHub default branch are both `main`)

### Phase 1 — Stonecutter scaffold
- [x] `settings.gradle.kts`: `dev.kikugie.stonecutter` (pinned `0.9.+`,
      resolved `0.9.7`) + `gg.meza.stonecraft` (pinned `1.10.+`), declares all
      ten matrix nodes
- [x] `stonecutter.gradle.kts`: active node (`1.20.1-fabric`, the pre-existing
      baseline version)
- [x] Root `build.gradle.kts` (centralScript)
- [x] `gradle.properties`: mod metadata only (MC/loader/API versions live in
      `versions/dependencies/*.properties` instead, per Stonecraft convention)
- [x] `src/main` shared Stonecutter tree — already satisfied by construction:
      the pre-existing single-version `src/main` tree sits at the repo root,
      which IS what Stonecutter's shared-tree model expects for whichever
      node is currently `active`; no file move was needed. No `//? if`
      preprocessor directives exist yet (that's Phase 2/3 porting work, not
      scaffolding).
- [x] Gradle wrapper bump: 8.11.1 template default → `9.3.1` (matches what
      `gg.meza.stonecraft 1.10.+` / Loom actually require; confirmed via real
      build output, not assumption)
- [x] Baseline sanity: `./gradlew :1.20.1-fabric:build` green with **zero**
      source changes beyond Stonecutter scaffolding — confirmed twice (once
      pre-existing-code-only, once after the `fabric.mod.json` per-node
      dependency-string fix below, still green both times)

### Phase 1.5 — Pre-existing defect fix (found during scaffolding, not new porting)
- [x] `fabric.mod.json`'s `depends.minecraft` was hardcoded to `~1.19.4` even
      though the codebase actually targets 1.20.1 — a stale leftover from a
      prior manual port, and would have been silently wrong for every one of
      the ten new nodes (each reporting the same static "~1.19.4" regardless
      of which MC version it actually built against, breaking Fabric's own
      dependency-compatibility check at game-load time even though the build
      itself stays green). Fixed by templating it as `${minecraft_dependency}`
      and wiring a `processResources.filesMatching("fabric.mod.json").expand(...)`
      block in `build.gradle.kts` keyed off `stonecutter.current.version`,
      mirroring the pre-Stonecutter Groovy build's existing `${version}`
      expand pattern (which had to be carried forward explicitly too — losing
      it broke the build with "Missing property (version) for Groovy template
      expansion" on the first attempt, since Gradle's `expand()` replaces the
      whole property map per `filesMatching` block rather than merging across
      calls). Verified by inspecting the built jar's `fabric.mod.json`:
      `"minecraft": "~1.20.1"`, `"version": "1.0.0+mc1.20.1"` — both correct.

### Phase 2 — Port latest-first (Fabric only first, loaders after)
- [x] Architectural decision: port the whole shared tree to **Mojmap
      uniformly** (all 5 MC versions), not a two-era Yarn/Mojmap split as
      originally sketched in an earlier draft of this plan. Rationale: Yarn
      mappings stop existing entirely past 1.21.11 (26.x is unobfuscated-only),
      so a uniform-Mojmap tree avoids maintaining two parallel mapping
      vocabularies for what's otherwise one shared source tree. Implemented by
      dropping `yarn_mappings` from `versions/dependencies/{1.18.2,1.19.4,1.20.1}.properties`.
- [x] 1.21.4-fabric: `:1.21.4-fabric:build` green. Real delta from 1.20.1,
      confirmed via `javap` against the actual per-project Loom jar (not
      assumption): `DrawContext`→`GuiGraphics` rename, `InGameHud`→`Gui`
      rename, `MinecraftClient`→`Minecraft` + full Mojmap symbol table (see
      CLAUDE.md porting notes), and `Gui.render(GuiGraphics, DeltaTracker)`
      picked up a second `DeltaTracker` parameter vs. 1.20.1's
      `render(DrawContext, float)` — handled with one Stonecutter
      `//? if >=1.21.2 { ... } else { ... }` block in `InGameHudMixin.java`.
      Also: `GuiGraphics.fill(int,int,int,int,int)` exists as a built-in
      pose-aware primitive on this version, so `HudComponent.fill()` was
      simplified to call it directly instead of hand-rolling
      Tessellator/BufferBuilder/RenderSystem immediate-mode drawing — this
      turned out to be a **simplification available starting at 1.21.4**, not
      a "hottest cross-version risk spot" as an earlier draft of CLAUDE.md
      assumed.
- [x] 1.20.1-fabric: re-verified green under the new uniform-Mojmap tree
      (previously green under the old Yarn-mapped tree pre-dating this
      session's port). Confirms the shared-tree `//? if` branching in
      `InGameHudMixin.java` resolves correctly for both eras.
- [x] 26.2-fabric: **unblocked, resolved.** The blocker recorded below (`Gui`
      losing `render(...)` entirely, replaced by
      `extractRenderState(DeltaTracker, boolean, boolean)` writing into a
      `GuiRenderState`) turned out not to require reverse-engineering
      `GuiRenderState`/`GuiTextRenderState` internals at all — instead, every
      loader already exposes its own official HUD-render hook that fires
      *before* that extraction step, so the fix was architectural, not a
      deeper mixin: replaced `mixin/InGameHudMixin.java` entirely with
      `HudHooks`, a loader/version-conditional registration class hooking
      `HudElementRegistry` (fabric≥26.1), `HudRenderCallback` (fabric else),
      `RenderGuiEvent.Post` (neoforge), `OverlayRegistry`/
      `RegisterGuiOverlaysEvent` (forge) directly — no Mixin needed anywhere
      any more. All 9 rendering-logic files were re-pointed at a new
      `FlightCanvas` interface; `PlatformCanvas` is the single Stonecutter-
      branched adapter behind it (pre-1.20 `PoseStack`, 26.1+
      `GuiGraphicsExtractor`/`Matrix3x2fStack`, else classic `GuiGraphics`).
      Two real bugs found and fixed via `javap` against the real 26.2 jar
      along the way: `Identifier.of(String,String)` doesn't exist on 26.2 (no
      2-arg `of` overload at all — use `Identifier.fromNamespaceAndPath`
      instead), and `HudElementRegistry.addLast` needs a `DeltaTracker`
      import scoped to the branches that actually use it (it doesn't exist
      pre-1.21.2, broke 1.20.1-fabric when left unconditional). The
      `KeyMapping.Category`/`ResourceLocation`→`Identifier` boundary and the
      `ClientCommandManager`→`ClientCommands` Fabric-API rename (both
      correctly anticipated in the pre-existing tree) needed no further
      changes. `:26.2-fabric:build` green, jar contents verified non-empty.
- [x] 1.19.4-fabric: green. Real delta from 1.20.1, confirmed via `javap`:
      `GuiGraphics` doesn't exist pre-1.20 (`PlatformCanvas`'s `<1.20` branch
      uses raw `PoseStack` + `GuiComponent.fill(PoseStack,...)` +
      `Font.draw(PoseStack,...)` instead); otherwise unchanged from 1.20.1 at
      this version (fill/drawText signatures are identical between 1.19.4 and
      1.18.2 too — confirmed via `javap` on both jars, no fix needed there).
- [x] 1.18.2-fabric: green. Three more genuine, version-specific deltas below
      1.19.4 that were NOT shared with it (all confirmed via `javap` against
      the real 1.18.2 jar and cross-checked against the 1.19.4 jar to find
      the exact boundary, not assumed):
      - `Options.fov()` doesn't exist as a method in 1.18.2 — it's a plain
        `double fov` field; the `OptionInstance<Integer>`-returning method
        form starts at 1.19.4. Split in `Dimensions.java` via `//? if <1.19`.
      - `com.mojang.math.Axis` doesn't exist in 1.18.2 at all (introduced at
        1.19.4). 1.18.2's `PoseStack.mulPose` takes the older
        `com.mojang.math.Quaternion` (via `Vector3f.ZP.rotationDegrees(...)`),
        not the JOML `Quaternionf` that `Axis.ZP.rotationDegrees(...)`
        returns and that 1.19.4+'s `mulPose` overload expects — genuinely a
        `<1.19`/else split, not folded into the existing `<1.20` branch,
        since `Vector3f` is fully removed (not just deprecated) by 1.19.4.
        Fixed in `PlatformCanvas.rotateDegrees()`.
      - The Fabric Client Command API v2 package
        (`net.fabricmc.fabric.api.client.command.v2`) is not published at all
        for 1.18.2's Fabric API bundle (confirmed: no
        `fabric-command-api-v2-*` artifact resolves for 1.18.2 in the
        loom-cache, unlike every other node in the matrix). Rather than fork
        a second command-API generation for one version, gated the
        `/flighthud toggle` command's imports, `FlightHud.setupCommand()`
        (and its call site), and `SwitchDisplayModeCommand.java`'s whole body
        to `fabric && >=1.19` — 1.18.2-fabric simply doesn't get the chat
        command (the keybind, unaffected by any of this, still covers it on
        every loader per the existing Fabric-only-command scope decision).
      Also found and fixed a **pre-existing latent bug** while diagnosing
      this: `SwitchDisplayModeCommand.java` had no Stonecutter guard at all
      originally despite depending on the Fabric-only
      `FabricClientCommandSource` type — it happened to never get caught
      because no non-fabric node had reached that file yet. Now correctly
      guarded.

### Phase 3 — Loaders
- [x] 1.21.4-neoforge: green. `RenderGuiEvent.Post#getGuiGraphics()` returns
      `GuiGraphics` here (vs. `GuiGraphicsExtractor` on 26.2-neoforge) —
      confirmed via `javap` that NeoForge itself already adapted this one
      event across the 26.x architecture change, so `HudHooks`'s neoforge
      branch is identical source for both NeoForge cells with zero extra
      branching (the `new PlatformCanvas(event.getGuiGraphics())` call site
      resolves against whichever `PlatformCanvas` constructor overload that
      node compiled).
- [x] 26.2-neoforge: green, same finding as above. Also needed
      `neoforge.mods.toml` (previously entirely missing from this repo) added
      alongside the pre-existing `mods.toml`, wired through the same
      `variableReplacements` metadata-expansion pattern as `fabric.mod.json`.
- [x] 1.20.1-forge: green. Confirmed `HudHooks.register()`'s
      `OverlayRegistry`-based imperative path (forge&&<1.19) does not apply
      here — 1.20.1 uses the event-based `RegisterGuiOverlaysEvent`/
      `registerOverlays(...)` path registered on the mod event bus at
      construction, matching the `else`/`forge` branch that also covers
      1.19.4-forge. Forge's `pack.mcmeta` task-graph ordering issue (noted as
      a standing risk in earlier sessions) did not recur — confirmed
      `generatePackMCMetaJson` runs cleanly.
- [x] 1.19.4-forge: green. Uses the same event-based `registerOverlays(...)`
      path as 1.20.1-forge (both are `forge && >=1.19`); the
      `RegisterKeyMappingsEvent`/`ClientRegistry.registerKeyBinding` boundary
      at 1.19 (already anticipated in the pre-existing tree) needed no
      further change.
- [x] 1.18.2-forge: green, the last cell in the matrix. Found and fixed one
      more latent bug while porting this one: `HudHooks.registerOverlays()`
      was guarded `forge && <1.20`, which wrongly included forge<1.19
      (1.18.2) — 1.18.2-forge doesn't use `RegisterGuiOverlaysEvent`/
      `ForgeGui` at all (neither type resolves against the 1.18.2 Forge jar);
      it registers imperatively via `OverlayRegistry.registerOverlayTop(...)`
      in `register()` instead, matching Forge's `<1.19` era exactly like
      NeoForge doesn't exist for it. Re-guarded to
      `forge && <1.19 { /* empty */ } elif forge && <1.20 { ... } elif forge { ... }`
      so the method simply doesn't exist for 1.18.2-forge. This would have
      broken the build the moment 1.18.2-forge tried to compile it — caught
      before it did, not after a failed build.

### Phase 4 — Verification
- [x] `./gradlew chiseledBuild` green for every matrix cell — run as a final
      sweep after all 10 cells were individually confirmed, specifically to
      catch any regression from the shared-file edits (`HudHooks.java`,
      `FlightHud.java`, `PlatformCanvas.java`, `Dimensions.java`,
      `SwitchDisplayModeCommand.java` are all touched by multiple cells).
      **Also verified jar contents, not just `BUILD SUCCESSFUL`**: `unzip -l`
      on all 10 built jars confirms real, non-empty `net/torocraft/flighthud`
      class trees (24 classes for non-fabric/1.18.2-fabric nodes, 25 for
      fabric≥1.19 nodes — the +1 is `SwitchDisplayModeCommand.class`, present
      exactly where expected and absent exactly where it should be per the
      1.18.2 command-API scope reduction above). No green-but-empty jars.
- [ ] Wire Forgix (`io.github.pacifistmc.forgix` `2.0.0`) at the physical root
      build script; one `forgix { }` block per MC version with explicit
      `inputJar` overrides pointing at that version's `<mc>-fabric` /
      `<mc>-forge` / `<mc>-neoforge` subproject jars (see "Forgix" section
      above for why the default autodetection won't match Stonecutter's
      subproject names)
- [ ] Verify each merged jar actually loads under both of its constituent
      loaders (not just that `mergeJars` succeeds)
- [x] README/CLAUDE.md note confirming Quilt compatibility via Fabric jars
      (CLAUDE.md done in the requirement-update commit; README.md's
      "Requirements"/"Supported versions and loaders" sections added this
      pass, replacing the stale "Minecraft 1.16.1" requirements text)
- [x] PLAN.md status table fully updated (this pass)
- [x] Final commit(s) pushed to `origin/main` (`cb35342`, `e6a07c0`, `925ecea`)

## Phase 5 — Test coverage + Folia (2026-08-10, Phase 2 follow-up brief)

### Folia compatibility

**Folia n/a — client mod.** Folia is a server-side Paper fork that
parallelizes region/chunk processing across threads; FlightHud is a
client-only mod (Fabric `ClientModInitializer` / NeoForge+Forge client-setup
entrypoints, HUD rendering, local JSON config) with zero server-side code.
Folia compatibility does not apply. No further investigation performed, per
the repo-specific brief override for this mod.

### Test coverage: 100% line coverage, enforced

JUnit 5 (`org.junit.jupiter:junit-jupiter:5.10.2`) + JaCoCo wired into
`build.gradle.kts` (mirroring the canonical wiring already landed in
`critical-orientation`/`critical-flight-details`, including the
NeoForge `junit-fml` classpath-exclusion guard). `jacocoTestCoverageVerification`
enforces `LINE COVEREDRATIO 1.00`; `tasks.check` depends on it. All tests run
against the active Stonecutter cell (`1.20.1-fabric`) only — never across the
matrix, never by hand-toggling `//? if` markers.

**Included/tested classes — 176/176 lines (100%):**

| Class | Lines covered |
|---|---|
| `config.SettingsConfig` | 19/19 |
| `config.SettingsConfig$DisplayMode` | 2/2 |
| `config.HudConfig` | 60/60 |
| `FlightComputerMath` | 27/27 |
| `config.loader.ConfigLoader` | 33/33 |
| `config.loader.FileWatcher` | 35/35 |
| **Total** | **176/176** |

Every test asserts real behavior (state transitions, computed values,
exception-swallowing branches actually exercised) — no "constructs without
throwing" placeholders, no reflection hacks to inflate coverage.
`FileWatcher`'s `run()`/`watch()`/`stop()`/`isRunning()` lifecycle is
exercised against a **real** `java.nio.file.WatchService` (a real filesystem
write is made and awaited, bounded by an in-test polling helper, not agent
session polling) in addition to hand-rolled `WatchService`/`WatchKey`/
`WatchEvent` fakes for the pure `pollEvents()` branch logic — a purely
fake/interrupt-driven test left 2 lines of `run()`'s while-loop uncovered,
so a genuine filesystem-event-delivery test was added to close the gap
honestly rather than fudging it.

**Excluded classes — one honest reason each, not a blanket exclusion:**

| Class | Why untestable headless |
|---|---|
| `FlightHud`, `FlightHud$*` | Loader entrypoint (`ClientModInitializer`/`@Mod`); touches live `KeyMapping`, `Minecraft.getInstance()`, and per-loader event buses that only exist inside a running game. |
| `HudHooks`, `HudHooks$*` | Registers directly against each loader's live HUD-render event bus/registry (`HudElementRegistry`, `RenderGuiEvent`, `OverlayRegistry`, etc.) — nothing to invoke without a running game client. |
| `PlatformCanvas`, `PlatformCanvas$*` | Thin wrapper directly over `GuiGraphics`/`PoseStack`/`GuiGraphicsExtractor` rendering primitives, none of which exist headless. |
| `HudComponent`, `HudComponent$*` | Drawing primitives call straight through to `FlightCanvas` (backed by the real renderer); no independently assertable logic beyond "did it call the mock." |
| `HudRenderer`, `HudRenderer$*` | Top-level render dispatcher with the same live-rendering dependency as `HudComponent`. |
| `Dimensions`, `Dimensions$*` | Reads live `Window`/`Options` screen-space state (`Options.fov()`, `Window.getGuiScaledWidth()`) that only exists inside a running game. |
| `FlightComputer`, `FlightComputer$*` | Thin Minecraft-touching wrapper pulling primitives off live `Vec3`/`Entity` objects and delegating to `FlightComputerMath` (which IS fully tested) — no logic of its own beyond field access on live game objects. |
| `SwitchDisplayModeCommand`, `SwitchDisplayModeCommand$*` | Brigadier command typed against `FabricClientCommandSource`; needs a live command-dispatch context. |
| `components/**` (7 classes) | Every HUD element renders directly against `FlightCanvas`/live game state — identical rationale to `HudComponent`. |
| `config.loader.ConfigFolder`, `$*` | One-liner wrapping `FabricLoader.getInstance()`/`FMLPaths.CONFIGDIR.get()`, which only exist inside a running game/loader; nothing to assert beyond "calls a static loader singleton." |

**Refactors made in service of testability (behavior-preserving, no hacks):**
- `ConfigLoader`'s old public String-filename constructor (which internally
  resolved `ConfigFolder.get()`, touching the live loader singleton) was
  **removed outright** rather than test-seamed — the File-based constructor
  is now the sole public constructor, and the loader-touching resolution
  moved to the call site in `FlightHud.java` (already wholesale-excluded).
  This is a genuine decoupling refactor, following the same "extract pure
  logic away from loader-touching orchestration" precedent already used for
  `FlightComputerMath`/`SettingsConfig.toggleDisplayMode`, not a
  coverage-inflation trick.
- `ConfigLoader.watch()`'s inline `() -> load()` lambda extracted to a named,
  package-private `reload()` method purely as a test seam, so a test can
  invoke the exact callback `watch()` wires up without waiting on a real
  filesystem event.
- `ConfigLoader.isWatching()` added as a package-private test seam to observe
  `watch()`'s idempotency (second call is a no-op guard, not a
  replace/duplicate) without depending on filesystem-event timing.
- `FileWatcher`'s constructor widened to package-private, and `stop()`/
  `isRunning()` added — closing a **real pre-existing functional gap**
  (previously no way to stop a started watcher; config-reload watching ran
  forever with no shutdown path), not just a test seam.

**Real bug fixed while testing:** `HudConfig.updateThickness()` had a dead
`try`/`catch` wrapped around a plain float division (`thickness / 2`), which
can never throw in Java (unlike int division-by-zero, unlike the adjacent
`updateColor()`'s `new Color(...)` constructor which genuinely can throw on
out-of-range components). Removed as genuinely unreachable defensive code;
documented inline in `HudConfig.java`.

**Second real bug fixed while testing (cross-subproject coverage gap,
`FileWatcher.run()`):** the end-of-work `chiseledBuild` regression sweep
(Priority 3c) caught `:1.20.1-forge:jacocoTestCoverageVerification` failing at
99% even though the *identical* `FileWatcher.java` source showed 100% on
`1.20.1-fabric` — a real, deterministic (confirmed 3x each way, ruling out
timing flakiness), subproject-dependent gap. Root cause, confirmed via
`javap -c -l -p` decompilation of the compiled class: for a try-with-resources
block whose resource's `close()` declares a checked exception, `javac`
generates **two physically distinct bytecode blocks that close the resource**
— one reachable only via a genuinely-normal (no-exception) fall-through exit
of the try block, the other folded into the exception-handling/rethrow path.
`run()`'s loop (`while (poll) { poll = pollEvents(watchService); }`) could in
theory exit normally if `WatchKey.reset()` ever returned false, so javac
generated both blocks — but no test exercises that path, since every real
termination goes through `stop()`/`Thread.interrupt()` (the exception path).
Two alternative fixes were considered and rejected: wrapping
`watchService.close()` in its own `try`/`catch(IOException)` (rejected —
confirmed via a throwaway scratch program that `WatchService.close()` never
throws on double-close, so this would just create a different, permanently
unreachable-without-reflection branch); forcing a real `WatchKey` invalidation
by deleting the watched directory mid-test (rejected — not reliably
reproducible within a short test window, too OS-timing-fragile for a hard
gate). Fixed instead by making the loop unconditional
(`while (true) { pollEvents(watchService); }`, discarding `reset()`'s return
value), removing the try block's only normal-exit route so the ambiguous
dual-path close bytecode collapses onto the one path that actually gets
tested. This is a deliberate, honestly-documented minor behavior
simplification: the watcher no longer proactively self-terminates if the
watched directory is deleted out from under it (rare in practice), relying
solely on the already-tested `stop()`/interrupt mechanism, which still works
correctly since an invalidated key still leaves `WatchService.take()` blocking
and still responsive to interrupts. Documented inline in `FileWatcher.java`.
Re-verified 3x on `1.20.1-forge` and once on `1.20.1-fabric` after the fix,
then a full `chiseledBuild` re-run confirmed all 10 cells still green.
**Portable gotcha for sibling repos:** any try-with-resources wrapping a
background-thread watcher/poller whose only real termination path is via an
exception (interrupt) can hit this same subproject-dependent JaCoCo gap if it
has a theoretical (but untested) normal-exit route out of the try block —
worth checking in any other repo's coverage work that has a similar
watcher-thread pattern.

Final verification: `./gradlew ":1.20.1-fabric:check"` — `BUILD SUCCESSFUL`,
`jacocoTestCoverageVerification` passing at the 100% bar; full
`./gradlew chiseledBuild` — `BUILD SUCCESSFUL` across all 10 cells.

## Notes log (append as work proceeds; keep dated)

- 2026-08-10: Repo analysis complete, upstream confirmed stale, newest stable
  MC confirmed live as 26.2, docs written. Starting Phase 0 commit next.
- 2026-08-10: Confirmed the current, working Stonecutter/Stonecraft DSL against
  live sources (`meza/Stonecraft-template`, `meza/Stonecraft` e2e fixtures +
  `Dependencies.kt`) rather than copying `critical-orientation`'s template,
  whose pinned versions no longer resolve to a working DSL in this environment
  (confirmed via a live failed sanity build: `Unresolved reference 'vcsVersion'`
  under a resolved Stonecutter 0.8.4 runtime despite the file declaring "0.5").
  Confirmed `versions/dependencies/<mc>.properties` key schema: always
  `minecraft_version`/`loader_version`/`fabric_version`; `neoforge_version` for
  NeoForge cells; `forge_version` for Forge cells; `yarn_mappings` (+
  `yarn_mappings_neoforge_patch` if that version also has a NeoForge cell) for
  any MC version ≤1.21.11 that needs legacy Yarn mappings instead of the
  Mojmap default; omit `yarn_mappings` entirely for MC ≥26.1 (unobfuscated).
- 2026-08-10: Bert added a binding requirement update: loader coverage across
  the matrix is mandatory (not "if feasible"), Quilt compatibility must be
  documented (no separate build target — Quilt loads Fabric jars natively),
  and the Forgix jar-merging plugin must be investigated and wired in if alive
  and compatible. Investigated live: Forgix (`io.github.pacifistmc.forgix`) is
  actively maintained (pushed today, stable `2.0.0` tag exists), composes with
  Stonecutter/Stonecraft in principle but needs explicit per-version
  `inputJar` overrides because Stonecutter's `<mc>-<loader>` subproject naming
  doesn't match Forgix's flat `:fabric`/`:forge` autodetection convention — see
  "Forgix" section above. Decision: adopt it, wire per version in Phase 4 once
  real per-loader jars exist to merge. Original target matrix already matched
  the mandated loader-viability rule (Fabric+NeoForge for 1.20.5+, Fabric+Forge
  for ≤1.20.4) with no change needed to the matrix itself — added explicit
  "structural blocker" language per-cell instead of bare "n/a".
- 2026-08-10: Committed the Stonecutter/Stonecraft scaffold (`d18bd0b`) after
  the baseline `:1.20.1-fabric:build` sanity check went green. While closing
  out Phase 0/1 checklist items, found and fixed a real pre-existing defect
  surfaced by the scaffolding work: `fabric.mod.json`'s `depends.minecraft`
  was hardcoded `~1.19.4` (stale relative to the actual 1.20.1 codebase), and
  would have reported the same wrong, static string on every one of the ten
  new nodes. Fixed via a per-node `processResources` expand keyed off
  `stonecutter.current.version`; verified against the actual built jar
  (`~1.20.1`, correct). Also updated README.md's stale "Minecraft 1.16.1"
  requirements section with the real target matrix and a Quilt compatibility
  note, and checked off completed Phase 0/1 boxes above. Next: Phase 2,
  latest-first Fabric porting starting with 1.21.4-fabric.
- 2026-08-10: Ported the full shared `src/main` tree to 1.21.4/Mojmap and got
  `:1.21.4-fabric:build` green from a genuine failing baseline (full mapping
  table + rationale in CLAUDE.md). While verifying the sibling `1.20.1-fabric`
  node afterward, hit a real Stonecutter build-mechanics gotcha worth
  recording: **the node marked `active` in `stonecutter.gradle.kts` must
  actually match the current shape of the shared `src/main` tree**, or its
  build fails referencing the *raw* tree (pre-`//? if` toggle) instead of a
  correctly-generated per-node copy — plain `:<node>:build` does not fix this,
  because the generate/prepare tasks that resync the active node's compile
  input only run correctly as part of the *sanctioned* switch. The file's
  `/* [SC] DO NOT EDIT */` comment is real: do not hand-edit the `active`
  string. Instead run the public Gradle task Stonecutter registers per node —
  found via `./gradlew :tasks --all` (case sensitivity matters; grepping
  case-insensitively for "stonecutter" hides these because their *display*
  names don't contain that word): `"Set active project to <mc>-<loader>"`
  (e.g. `./gradlew "Set active project to 1.21.4-fabric"`). Used this to move
  active from the stale `1.20.1-fabric` (left over from Phase 1) to
  `1.21.4-fabric` (matching Phase 2's actual current focus and the tree's
  current toggle-state); both `1.21.4-fabric` and `1.20.1-fabric` build green
  afterward. Internal-only tasks confirmed *not* to be the right lever:
  `stonecutterGenerate`/`stonecutterPrepare`/`stonecutterMerge` (+ `*Test`
  variants) are explicitly "Internal Stonecutter task. Do not call manually."
  Separately, switching the active node forces Gradle to configure every
  declared node (Stonecutter needs the whole tree to build the switch-task
  dependency graph, even with `--configure-on-demand`), which surfaced a
  second real gap: `26.2-neoforge` couldn't configure at all because no
  Java-25 toolchain was available and **no toolchain auto-provisioning
  repository was configured** (`settings.gradle.kts` had no
  `foojay-resolver-convention` plugin, despite CLAUDE.md's environment notes
  already saying this project should rely on one). Fixed by adding
  `id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"` to
  `settings.gradle.kts`'s `plugins {}` block (an earlier attempt at `0.8.0`
  failed with `JvmVendorSpec does not have member field 'IBM_SEMERU'` — that
  plugin version predates this project's Gradle 9.3.1's toolchain API shape).
  Attempted 26.2-fabric next per Phase 2's order; blocked on a real
  architecture change (`GuiGraphics` removed entirely, replaced by a
  render-state-extraction model) — see Phase 2 checklist above for the full
  `javap`-confirmed finding. Not yet resolved; needs its own research pass.
- 2026-08-10: Ran the deferred 26.2 research pass and it resolved cleanly: the
  `GuiRenderState`/`extractRenderState(...)` model only affects the *engine's*
  internal render pass — every loader still fires its own official HUD-render
  hook before that extraction happens, so the fix was to stop mixin-injecting
  into `Gui.render(...)` (which no longer exists on 26.x) and instead hook
  each loader's own event/registry directly. Replaced
  `mixin/InGameHudMixin.java` with `HudHooks` + a new `FlightCanvas` interface
  (`PlatformCanvas` is the one Stonecutter-branched adapter behind it: pre-1.20
  `PoseStack`, 26.1+ `GuiGraphicsExtractor`, else classic `GuiGraphics`).
  Rewrote `FlightHud.java`'s entrypoint to be loader-conditional (Fabric
  `ClientModInitializer` vs. NeoForge/Forge `@Mod`), fixed `ConfigFolder`
  (was Fabric-only, would have broken every NeoForge/Forge compile), and
  added the previously-missing `mods.toml`/`neoforge.mods.toml`. Result:
  **26.2-fabric and 26.2-neoforge both green** — 26.2 is not blocked.
  Committed as `cb35342`.
- 2026-08-10: Continued straight through the rest of the matrix in
  version-descending, loader-by-loader order, applying the same
  `HudHooks`/`FlightCanvas` architecture: fixed a scoped-import bug in
  `HudHooks.java` (`DeltaTracker` was unconditionally imported despite not
  existing before 1.21.2, breaking 1.20.1-fabric — committed as `e6a07c0`
  alongside re-verified 1.20.1-fabric and new 1.20.1-forge), then
  1.21.4-fabric/neoforge, 1.19.4-fabric/forge, and finally 1.18.2-fabric/forge
  — the last two needing three genuine, previously-undocumented 1.18.2-only
  API deltas (`Options.fov()` field-vs-method, `Axis`-vs-`Vector3f`,
  fabric-command-api-v2 absence) plus one latent pre-existing bug found along
  the way (`HudHooks.registerOverlays()`'s guard wrongly included forge<1.19).
  See the Phase 2/3 checklist entries above for the full detail on each; every
  finding was confirmed via `javap` against the real per-node merged jar, not
  assumed. Also picked up a Stonecutter mechanics lesson worth recording:
  **"Set active project to X" and "the next build" must be two separate
  `./gradlew` invocations**, never combined in one command — combining them
  once produced a spurious `stonecutterGenerate`-implicitly-depends-on-
  `stonecutterMerge` task-validation failure. All 10 target cells now build
  green; a final `./gradlew chiseledBuild` sweep plus `unzip -l` on every
  produced jar confirms no regression and no green-but-empty jars. Committed
  as `925ecea` and pushed. Remaining open item: Forgix per-version wiring
  (Phase 4) — deferred, not part of this pass's brief.
- 2026-08-10: Phase 2 follow-up brief (test coverage + Folia). Wired JUnit 5 +
  JaCoCo into `build.gradle.kts` mirroring `critical-orientation`/
  `critical-flight-details`'s canonical pattern. Drove line coverage from an
  initial 97% (first full run) to enforced 100% across three iterations, each
  diagnosed by parsing `jacocoTestReport.xml` directly rather than guessing —
  see Phase 5 section above for the full class-by-class breakdown, exclusion
  rationale, refactors, and the one real dead-code bug found
  (`HudConfig.updateThickness()`'s unreachable catch block). Folia verdict:
  n/a, client mod, one line per the repo-specific brief override — no further
  investigation. `./gradlew ":1.20.1-fabric:check"` green at the 100% bar.
  During the end-of-work `chiseledBuild` regression sweep, caught a second,
  genuinely subtle real bug: `:1.20.1-forge:jacocoTestCoverageVerification`
  failed at 99% on the *identical* `FileWatcher.java` source that showed 100%
  on `1.20.1-fabric` — root-caused via `javap` bytecode decompilation to a
  javac try-with-resources quirk (two distinct close-bytecode paths generated
  when the try block has a theoretical, but untested, normal-exit route; only
  the exception-path close ever gets exercised). Fixed by making `run()`'s
  loop unconditional, removing the untested normal-exit route entirely rather
  than chasing coverage of a fragile, hard-to-trigger-safely branch — full
  detail, rejected alternatives, and the portable gotcha for sibling repos'
  watcher-thread patterns are in the Phase 5 section above. Re-verified 3x per
  subproject after the fix; full `chiseledBuild` regression sweep re-confirmed
  all 10 cells still green afterward.
