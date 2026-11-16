pluginManagement {
    repositories {
        mavenLocal()
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.kikugie.dev/releases")
        maven("https://maven.kikugie.dev/snapshots")
        maven("https://maven.fabricmc.net/")
        maven("https://maven.architectury.dev")
        maven("https://maven.minecraftforge.net")
        maven("https://maven.neoforged.net/releases/")
    }
}
plugins {
    id("gg.meza.stonecraft") version "1.12.+"
    id("dev.kikugie.stonecutter") version "0.9.+"
    // Auto-provisions JDKs (e.g. Java 25 for MC 26.x) that aren't installed
    // locally, so `./gradlew` doesn't need a system JDK per MC version.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

stonecutter {
    centralScript = "build.gradle.kts"
    kotlinController = true
    shared {
        fun mc(version: String, vararg loaders: String) {
            // Make the relevant version directories named "1.20.1-fabric", "1.20.1-forge", etc.
            for (it in loaders) version("$version-$it", version)
        }

        // Newest stable + previous major: NeoForge (split from Forge at 1.20.2)
        mc("26.3", "fabric", "neoforge")
        mc("26.2", "fabric", "neoforge")
        mc("1.21.4", "fabric", "neoforge")

        // Legacy versions: Forge (predates the NeoForge split)
        mc("1.20.1", "fabric", "forge")
        mc("1.19.4", "fabric", "forge")
        mc("1.18.2", "fabric", "forge")

        // Active/default node while porting: the pre-existing baseline.
        vcsVersion = "1.20.1-fabric"
    }
    create(rootProject)
}

rootProject.name = "FlightHud"
