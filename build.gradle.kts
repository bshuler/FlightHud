import gg.meza.stonecraft.mod

plugins {
    id("gg.meza.stonecraft")
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
