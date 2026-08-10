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
}

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
