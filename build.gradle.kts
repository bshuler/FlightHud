import org.gradle.language.jvm.tasks.ProcessResources

plugins {
    id("gg.meza.stonecraft")
}

modSettings {
    clientOptions {
        narrator = false
    }
}

tasks.named<Jar>("jar") {
    from("LICENSE")
}

// fabric.mod.json's depends.minecraft must track whichever MC version this
// node actually builds against, not a hardcoded pin — see the ${minecraft_dependency}
// token in src/main/resources/fabric.mod.json. Mirrors the pre-Stonecutter
// build's existing `${version}` expand pattern.
tasks.named<ProcessResources>("processResources") {
    val mcDependency = "~${stonecutter.current.version}"
    inputs.property("minecraft_dependency", mcDependency)
    inputs.property("version", project.version)
    filesMatching("fabric.mod.json") {
        expand("minecraft_dependency" to mcDependency, "version" to project.version)
    }
}
