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
