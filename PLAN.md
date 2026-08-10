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

| MC version | Fabric | NeoForge | Forge | Forgix merged jar |
|---|---|---|---|---|
| 26.2 (newest stable) | ❌ | ☐ | n/a — program scope (see below) | ☐ |
| 1.21.4 | 🔧 | ☐ | n/a — program scope (see below) | ☐ |
| 1.20.1 | 🔧 | n/a — structural, no NeoForge pre-1.20.2 | ☐ | ☐ |
| 1.19.4 | ☐ | n/a — structural, no NeoForge pre-1.20.2 | ☐ | ☐ |
| 1.18.2 | ☐ | n/a — structural, no NeoForge pre-1.20.2 | ☐ | ☐ |

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
- [ ] 26.2-fabric: **attempted, blocked on a real architecture change, not a
      simple rename.** Confirmed via `javap` against the actual 26.2 merged
      jar: `GuiGraphics` **does not exist at all** in 26.2 — `Gui` no longer
      has a `render(...)` method a mixin can inject into with a graphics
      context. Instead `Gui` has
      `extractRenderState(DeltaTracker, boolean, boolean)` writing into a
      `net.minecraft.client.renderer.state.gui.GuiRenderState` object — a
      render-state-extraction model (state is captured on this thread, then
      consumed by a separate render pass), not the "just inject and draw"
      model every earlier version uses. This is the real "post-1.21.6 render
      pipeline rewrite" flagged in earlier drafts of CLAUDE.md, but the actual
      shape of it (state-extraction object, not just a new draw API) wasn't
      known until this `javap` pass. Needs dedicated research before porting:
      how to append custom draw state to `GuiRenderState`/`GuiTextRenderState`
      (or find the current equivalent mixin injection point), not yet done.
      Also newly confirmed blocked on: `KeyMapping`'s 3-arg constructor now
      takes a `KeyMapping.Category` (not a raw `String`) as its 3rd arg;
      `KeyBindingHelper` and `ClientCommandManager` (Fabric API) were not
      found under their 1.21.4 packages either — Fabric API's client-command
      and keybinding-helper surface has moved/renamed by 26.2 and needs its
      own `javap`/artifact-browsing pass against the 26.2 Fabric API jars.
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
- [ ] PLAN.md status table fully updated
- [ ] Final commit(s) pushed to `origin/main`

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
