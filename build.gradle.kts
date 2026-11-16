import gg.meza.stonecraft.mod

plugins {
    id("gg.meza.stonecraft")
    jacoco
}

// Stonecraft's built-in ProcessResources expansion only provides
// id/name/group/description/version/minecraftVersion/packVersion/fabricVersion/
// forgeVersion/neoforgeVersion (see Stonecraft's ProcessResources.kt) -- note
// these are bare keys, NOT ${mod_id}-style. Loader/version *ranges* and the
// Java requirement aren't provided out of the box, so they're computed here
// and exposed via variableReplacements, mirroring the sibling repo
// critical-orientation's already-working pattern.
val minecraftVersionRange = "[${stonecutter.current.version},)"
// Fabric Loader's own dependency matcher does NOT understand Maven/OSGi bracket
// ranges like "[26.1.2,)" -- it uses its own SemVer-style comparator strings
// (">=", "<", "~", "^": see Fabric Loader's VersionPredicate). Fabric gets its
// own equivalent open-ended range in Fabric's own syntax.
val fabricMinecraftVersionRange = ">=${stonecutter.current.version}"
val forgeVersionRange = "[${mod.prop("forge_version", "0")},)"
val neoforgeVersionRange = "[${mod.prop("neoforge_version", "0")},)"
// Any FML/NeoForge loader release works for this mod; we don't gate on a specific loader build.
val loaderVersionRange = "[0,)"
val javaVersion = if (stonecutter.eval(stonecutter.current.version, ">=21.6")) {
    25
} else if (stonecutter.eval(stonecutter.current.version, ">=1.20.6")) {
    21
} else {
    17
}

modSettings {
    clientOptions {
        narrator = false
    }

    variableReplacements.put("minecraftVersionRange", minecraftVersionRange)
    variableReplacements.put("fabricMinecraftVersionRange", fabricMinecraftVersionRange)
    variableReplacements.put("forgeVersionRange", forgeVersionRange)
    variableReplacements.put("neoforgeVersionRange", neoforgeVersionRange)
    variableReplacements.put("loaderVersionRange", loaderVersionRange)
    variableReplacements.put("javaVersion", javaVersion)
}

tasks.named<Jar>("jar") {
    from("LICENSE")
}

dependencies {
    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // Tier 1 "loaded game" testing (Fabric cells only). fabric-loader-junit
    // stands a real Fabric loader up inside the JUnit worker, which is what
    // makes it legal for LoadedGameTest to call Bootstrap.bootStrap() and then
    // assert against genuinely loaded game data - a real ItemStack(ELYTRA),
    // the real item registry, vanilla's own Direction/Mth angle helpers.
    //
    // Fabric-only on purpose: NeoForge's equivalent bootstrap is junit-fml,
    // and its supported loaded-test harness is ModDevGradle-only, unavailable
    // under Architectury Loom - see the junit-fml exclusion comment below.
    if (mod.isFabric) {
        testImplementation("net.fabricmc:fabric-loader-junit:0.19.3")
    }
}

// ---------------------------------------------------------------------------
// Tier 3 "real client" testing: Fabric client gametests.
//
// Tier 1 (fabric-loader-junit, above) stands a loader and a bootstrapped
// Minecraft up inside a JUnit worker, but there is no window, no render
// thread, and no game loop - so it cannot answer the one question that
// matters most for a HUD mod: does the render hook this cell registers
// actually fire, in a real running client, against that MC version's real
// rendering architecture? Tier 3 launches an actual Minecraft client,
// creates a world, and asserts against it.
//
// Gated `>=1.21.4` because fabric-client-gametest-api-v1 does not exist in
// the Fabric API bundles for 1.20.1/1.19.4/1.18.2 (checked against the real
// fabric-api POMs on maven.fabricmc.net, not assumed): the module first
// appears around fabric-api 0.106 / MC 1.21.2. The two supported cells ship
// v4.1.1 (1.21.4) and v6.0.0 (26.2); the test source deliberately uses only
// the API surface those two versions have in common, verified by javap
// against both jars, so one un-preprocessed source file compiles on both.
val clientGameTestSupported = mod.isFabric &&
    stonecutter.eval(stonecutter.current.version, ">=1.21.4")

if (clientGameTestSupported) {
    // Loom (Architectury 1.14.476/1.17.491 here - both carry the same
    // FabricApiExtension API, confirmed via javap) is not applied through this
    // script's `plugins {}` block, so there is no generated `fabricApi { }`
    // accessor; configure the extension by type instead.
    extensions.configure<net.fabricmc.loom.api.fabricapi.FabricApiExtension>("fabricApi") {
        configureTests {
            createSourceSet.set(true)
            modId.set("flighthud-gametest")
            // enableGameTests wires `check` to dependsOn(runGameTest), i.e. it
            // drags a real *server* launch into every `./gradlew check`. This
            // mod is client-only and has no server gametests, so it stays off.
            enableGameTests.set(false)
            enableClientGameTests.set(true)
            // eula is deliberately left at its default (false). Loom's
            // acceptGameTestEula task writes `eula=true` into the run
            // directory on your behalf; that is Mojang's EULA, so it is not
            // accepted automatically here. The client gametest run does not
            // need it (only the dedicated-server variant does) - if a run ever
            // fails asking for it, accept it deliberately rather than by
            // flipping this flag in passing.
        }
    }

    // Loom's createSourceSet leaves the new source set on Gradle's default
    // convention, and Stonecutter picks it up from there automatically: the
    // `gametest` source set ends up with srcDirs
    // [versions/<cell>/src/gametest/java, versions/<cell>/build/generated/
    // stonecutter/gametest/java] and gets its own stonecutterPrepare/Generate/
    // MergeGametest tasks, exactly like `main` and `test` (verified by running
    // stonecutterGenerateGametest and reading the generated tree, not assumed).
    // So the shared root `src/gametest` tree IS run through the preprocessor and
    // `//? if` markers work in it. Nothing to override here.
    //
    // The client gametest still avoids version branching where it can, because
    // the 1.21.4 and 26.2 cells ship different major versions of the
    // client-gametest API (v4.1.1 vs v6.0.0) and the subset they share - checked
    // with javap against both jars - covers everything this test needs.

    // Loom's `mods` container starts empty, and configureTests' createSourceSet
    // adds an entry for the gametest mod. That single entry would otherwise be
    // the *only* classpath group, leaving the main mod's classes and resources
    // ungrouped in the dev run. Register it explicitly so `flighthud` is one mod
    // again; the test asserts FabricLoader can see it, so a regression here fails
    // loudly rather than silently running the game without the mod under test.
    extensions.configure<net.fabricmc.loom.api.LoomGradleExtensionAPI>("loom") {
        mods.create(mod.prop("id", "flighthud")).sourceSet(sourceSets["main"])
    }

    // Two plugins both want to name the player, and Minecraft's argument parser
    // (joptsimple) treats `username` as a single-valued option, so the run dies
    // with MultipleArgumentsForOptionException before the client even starts:
    //
    //   PROBE run=clientGameTest args=[--username, Player0, --username=developer]
    //
    // Stonecraft appends `--username=developer` from a `loom.runs.all { }` block
    // to every run config whose environment is `client` (Stonecraft's Loom.kt,
    // read from its bytecode), and that `all` hook catches `clientGameTest` too
    // because Loom creates it later. Loom's own configureTests passes
    // `--username Player0` (GameTestSettings.getUsername()'s convention).
    //
    // Drop Stonecraft's, keep Loom's: the gametest run is not a human's dev
    // session, and the client-gametest screenshot comparisons key off the
    // harness's own default username. Only the `clientGameTest` config is
    // touched, so `runClient` still logs in as `developer`. This runs in
    // afterEvaluate because Loom creates the run config while this script's
    // body is still executing, and Stonecraft's `all` action fires at creation.
    //
    // That same `runs.all { }` block also calls setRunDir on every run config,
    // which overwrites the `build/run/clientGameTest` that Loom had just set and
    // repoints the gametest at `../../run` - the repo-root run directory a human
    // uses for `runClient`. That is not merely untidy: GameTestSettings'
    // clearRunDirectory conventions to `true`, and Loom registers a
    // `deleteGameTestRunDir` Delete task over `runConfig.getRunDir()`, so
    // launching the gametest would wipe the developer's own dev world, options
    // and screenshots. Put it back where Loom wanted it, which is also per-cell
    // (versions/<cell>/build/run/clientGameTest) so 1.21.4 and 26.2 do not share
    // a run directory or overwrite each other's screenshots. The Delete task
    // reads getRunDir() lazily when it is realized, so correcting it here is
    // enough to redirect the deletion too.
    afterEvaluate {
        extensions.configure<net.fabricmc.loom.api.LoomGradleExtensionAPI>("loom") {
            runs.named("clientGameTest") {
                programArguments.set(
                    programArguments.get().filterNot { it.startsWith("--username=") }
                )
                runDirectory.set(layout.buildDirectory.dir("run/clientGameTest"))
            }
        }
    }

    // No dependency declaration for fabric-client-gametest-api-v1 here on
    // purpose. Loom's configureTests adds none of its own (checked in
    // FabricApiTesting's bytecode), but Stonecraft already puts the whole
    // `fabric-api` bundle artifact on the compile classpath - modApi on 1.21.4,
    // plain api on 26.2, which is unobfuscated and needs no remapping - and that
    // bundle depends on every module including this one. Both cells resolve it
    // transitively (1.21.4 -> 4.1.1+6816ccd404, 26.2 -> 6.0.0+515ac5339e), and
    // the gametest configurations extend main's, so it is already there.
    // Declaring it again would just pin a second, possibly conflicting version.

    // Nothing in the normal build graph compiles a `gametest` source set, so a
    // compile error in the client gametest would sit undetected until someone
    // launched the game. Compiling it is cheap and needs no display; make
    // `check` do it. Running it is still opt-in (see runClientGameTest).
    tasks.named("check") {
        dependsOn(tasks.named("compileGametestJava"))
    }
}

// NeoForge's transitive net.neoforged.fancymodloader:junit-fml (<=9.0.18) auto-registers a
// LauncherSessionListener that looks up a run-config file "mainargs.txt" via a relative path
// that doesn't resolve under Gradle's test-worker working directory, failing `test` at
// JUnit-launcher startup with NoSuchFileException: mainargs.txt. Forcing the upstream-fixed
// junit-fml 10.0+ doesn't work either (NoClassDefFoundError:
// net/neoforged/fml/startup/StartupArgs - it needs a newer FML core than these NeoForge
// releases ship). This repo's tests are plain pure-logic JUnit tests that need no FML
// bootstrap at all, so junit-fml is excluded from the test runtime classpath. Same approach
// as the sibling critical-orientation / EasierVillagerTrading / FlightHud /
// simple-utilities-mod / ToroHealth repos.
//
// Caveat, recorded 2026-08-13 so nobody re-derives it: excluding junit-fml is the
// right call *for these repos*, not a universal one. junit-fml is precisely
// NeoForge's own loaded-test bootstrap - it is what stands FML up so a test can
// run against a real, loaded game. NeoForge's supported loaded-test path
// (`neoForge { unitTest { enable(); testedMod = ... } }`, the `testframework`
// artifact, `@ExtendWith(EphemeralTestServerProvider.class)`, `runGameTestServer`)
// is ModDevGradle-only, and this repo builds on Architectury Loom via Stonecraft,
// so that path is unavailable here regardless. Excluding junit-fml therefore costs
// nothing today. If a cell is ever migrated to ModDevGradle, this exclusion must
// be revisited before writing any loaded NeoForge test - it would silently disable
// the very bootstrap such a test depends on.
if (mod.isNeoforge) {
    // NeoForge's own POM pulls in fancymodloader's junit-fml, which registers a JUnit
    // Platform LauncherSessionListener that unconditionally expects a mainargs.txt
    // launch-args file (only ever produced for NeoForge's in-game gametest run
    // configs). This project's tests are plain, loader-agnostic math tests - not
    // in-game gametests - so pull it back off the test classpath. Mirrors the fix
    // already landed in the sibling critical-flight-details mod; added here
    // proactively even though the active test project during Phase 2 is
    // 1.20.1-fabric (unaffected) - FlightHud does have 1.21.4-neoforge/26.2-neoforge
    // cells, and this keeps `test`/`check` from breaking on them later.
    configurations.named("testRuntimeClasspath") {
        exclude(group = "net.neoforged.fancymodloader", module = "junit-fml")
    }
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

// JaCoCo scope: everything that touches a live Minecraft/loader singleton, GLFW,
// or a rendering primitive (Font/GuiGraphics/PoseStack) at class-load or call time
// is excluded - merely referencing such a class headless is unsafe/meaningless
// without a running game client. The genuinely pure, loader-agnostic logic
// (config parsing/persistence, file-watch plumbing, and flight/HUD math) was
// extracted into (or already lived in) classes with zero Minecraft-class
// dependency and is fully unit-tested; see PLAN.md ("Phase 2: Test coverage")
// for the full per-class reasoning.
val jacocoExcludes = listOf(
    "net/torocraft/flighthud/FlightHud.class",
    "net/torocraft/flighthud/FlightHud$*.class",
    "net/torocraft/flighthud/HudHooks.class",
    "net/torocraft/flighthud/HudHooks$*.class",
    "net/torocraft/flighthud/PlatformCanvas.class",
    "net/torocraft/flighthud/PlatformCanvas$*.class",
    "net/torocraft/flighthud/HudComponent.class",
    "net/torocraft/flighthud/HudComponent$*.class",
    "net/torocraft/flighthud/HudRenderer.class",
    "net/torocraft/flighthud/HudRenderer$*.class",
    "net/torocraft/flighthud/Dimensions.class",
    "net/torocraft/flighthud/Dimensions$*.class",
    "net/torocraft/flighthud/FlightComputer.class",
    "net/torocraft/flighthud/FlightComputer$*.class",
    "net/torocraft/flighthud/SwitchDisplayModeCommand.class",
    "net/torocraft/flighthud/SwitchDisplayModeCommand$*.class",
    "net/torocraft/flighthud/components/**",
    "net/torocraft/flighthud/config/loader/ConfigFolder.class",
    "net/torocraft/flighthud/config/loader/ConfigFolder$*.class",
)

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
    classDirectories.setFrom(classDirectories.files.map { fileTree(it) { exclude(jacocoExcludes) } })
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test)
    classDirectories.setFrom(classDirectories.files.map { fileTree(it) { exclude(jacocoExcludes) } })
    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "1.00".toBigDecimal()
            }
        }
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
}

// Forge's pack.mcmeta generation task writes into the main source set's resources
// output without declaring that as a tracked task output, which Gradle's task
// validation flags as an undeclared ("implicit") dependency on :compileTestJava
// (which consumes sourceSets.main.output as part of the test compile classpath).
// Declare the dependency explicitly so validation/chiseledBuild doesn't hit a
// task-graph ordering failure. tasks.matching(...) is a live/lazy filter, so
// this is a harmless no-op on loaders that don't have a generatePackMCMetaJson
// task (e.g. Fabric).
tasks.matching { it.name == "compileTestJava" }.configureEach {
    dependsOn(tasks.matching { it.name == "generatePackMCMetaJson" })
}
